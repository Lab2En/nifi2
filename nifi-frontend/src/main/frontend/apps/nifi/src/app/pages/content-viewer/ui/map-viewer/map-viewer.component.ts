import { Component, inject, AfterViewInit, OnDestroy } from '@angular/core';
import { Store } from '@ngrx/store';
import { NiFiState } from '../../../../state';
import { selectRef } from '../../state/content/content.selectors';
import { isDefinedAndNotNull } from '@nifi/shared';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import * as maplibregl from 'maplibre-gl';

@Component({
    selector: 'map-viewer',
    standalone: true,
    templateUrl: './map-viewer.component.html',
    imports: [CommonModule],
    styleUrls: ['./map-viewer.component.scss']
})
export class MapViewer implements AfterViewInit, OnDestroy {
    private store = inject<Store<NiFiState>>(Store);

    private map: maplibregl.Map | undefined;
    private contextPath = 'nifi-geometry-viewer-2.8.0-SNAPSHOT';
    private clickListener: ((e: maplibregl.MapMouseEvent) => void) | null = null;

    ref: string | null = null;
    layerVisibility = { local: true };

    constructor() {
        this.store
            .select(selectRef)
            .pipe(isDefinedAndNotNull(), takeUntilDestroyed())
            .subscribe((ref) => {
                this.ref = ref;
                if (this.map?.isStyleLoaded()) {
                    this.addNifiTileSource(ref);
                }
            });
    }

    ngAfterViewInit(): void {
        this.initializeMap();
    }

    private initializeMap(): void {
        this.map = new maplibregl.Map({
            container: 'map-canvas',
            style: 'https://demotiles.maplibre.org/style.json',
            center: [105.6528, 20.975],
            zoom: 13,
            trackResize: true
        });

        this.map.addControl(new maplibregl.NavigationControl());

        this.map.on('load', () => {
            if (!this.map) return;
            if (this.ref) this.addNifiTileSource(this.ref);

            // Ensures the map fills the container after initial render
            setTimeout(() => this.map?.resize(), 100);
        });
    }

    private addNifiTileSource(ref: string): void {
        if (!this.map) return;

        const sourceId = 'nifi-source';
        const layerIds = ['local-polygons', 'local-lines', 'local-points'];

        // 1. Cleanup existing layers and source
        layerIds.forEach((id) => {
            if (this.map?.getLayer(id)) this.map.removeLayer(id);
        });
        if (this.map.getSource(sourceId)) this.map.removeSource(sourceId);

        // 2. Cleanup existing click listener (Fixed TS2554)
        if (this.clickListener) {
            this.map.off('click', this.clickListener);
        }

        // 3. Build the Absolute URL
        const baseUrl = window.location.origin;
        const path = `/${this.contextPath}/api/geometry/tiles/{z}/{x}/{y}`;
        const urlWithParams = new URL(path, baseUrl);
        urlWithParams.searchParams.set('ref', ref);
        const fullNifiTileUrl = decodeURI(urlWithParams.toString());

        this.map.addSource(sourceId, {
            type: 'vector',
            tiles: [fullNifiTileUrl],
            minzoom: 0,
            maxzoom: 22
        });

        const visibility = this.layerVisibility.local ? 'visible' : 'none';

        // 4. Add Geometry Layers
        this.map.addLayer({
            id: 'local-polygons',
            type: 'fill',
            source: sourceId,
            'source-layer': 'myPolygons',
            layout: { visibility },
            paint: { 'fill-color': '#0786e0', 'fill-opacity': 0.6, 'fill-outline-color': '#ffffff' }
        });

        this.map.addLayer({
            id: 'local-lines',
            type: 'line',
            source: sourceId,
            'source-layer': 'myLines',
            layout: { visibility },
            paint: { 'line-color': '#0786e0', 'line-width': 2.5 }
        });

        this.map.addLayer({
            id: 'local-points',
            type: 'circle',
            source: sourceId,
            'source-layer': 'myPoints',
            layout: { visibility },
            paint: {
                'circle-radius': 6,
                'circle-color': '#0786e0',
                'circle-stroke-width': 2,
                'circle-stroke-color': '#ffffff'
            }
        });

        // 5. Define and Attach the Click Listener
        this.clickListener = (e: maplibregl.MapMouseEvent) => {
            const features = this.map?.queryRenderedFeatures(e.point, {
                layers: layerIds
            });

            if (!features?.length) return;

            const props = features[0].properties;
            let html = '<div class="nifi-popup-table"><b>NiFi Attributes</b><hr/><table>';
            Object.entries(props).forEach(([k, v]) => {
                html += `<tr><td><b>${k}</b></td><td>${v}</td></tr>`;
            });
            html += '</table></div>';

            new maplibregl.Popup({ closeButton: true, anchor: 'bottom', offset: [0, -10] })
                .setLngLat(e.lngLat)
                .setHTML(html)
                .addTo(this.map!);
        };

        this.map.on('click', this.clickListener);
    }

    toggleNiFiLayer(): void {
        if (!this.map) return;
        this.layerVisibility.local = !this.layerVisibility.local;
        const visibility = this.layerVisibility.local ? 'visible' : 'none';
        ['local-polygons', 'local-lines', 'local-points'].forEach((id) => {
            if (this.map?.getLayer(id)) this.map.setLayoutProperty(id, 'visibility', visibility);
        });
    }

    ngOnDestroy(): void {
        if (this.map) {
            if (this.clickListener) this.map.off('click', this.clickListener);
            this.map.remove();
        }
    }
}
