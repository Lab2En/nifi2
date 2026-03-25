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

    ref: string | null = null;

    // Simplified visibility tracking
    layerVisibility = {
        local: true
    };

    constructor() {
        this.store
            .select(selectRef)
            .pipe(isDefinedAndNotNull(), takeUntilDestroyed())
            .subscribe((ref) => {
                this.ref = ref;
                // Update the tiles whenever the FlowFile reference changes
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
            center: [105.6528, 20.975], // Default focus: Hanoi
            zoom: 13,
            trackResize: true
        });

        this.map.addControl(new maplibregl.NavigationControl());

        this.map.on('load', () => {
            if (!this.map) return;

            // Load NiFi Source immediately if ref exists
            if (this.ref) {
                this.addNifiTileSource(this.ref);
            }

            this.map.resize();
        });
    }

    private addNifiTileSource(ref: string): void {
        if (!this.map) return;

        const sourceId = 'nifi-source';
        const layers = ['local-polygons', 'local-lines', 'local-points'];

        // 1. Remove existing layers and source to prevent "Source already exists" errors
        layers.forEach((id) => {
            if (this.map?.getLayer(id)) this.map.removeLayer(id);
        });
        if (this.map.getSource(sourceId)) this.map.removeSource(sourceId);

        // 2. Build the Absolute URL for the Request constructor
        const baseUrl = window.location.origin;
        const path = `/${this.contextPath}/api/geometry/tiles/{z}/{x}/{y}`;
        const urlWithParams = new URL(path, baseUrl);
        urlWithParams.searchParams.set('ref', ref);

        // decodeURI ensures the {z}/{x}/{y} placeholders aren't double-encoded
        const fullNifiTileUrl = decodeURI(urlWithParams.toString());

        // 3. Add Vector Source
        this.map.addSource(sourceId, {
            type: 'vector',
            tiles: [fullNifiTileUrl],
            minzoom: 0,
            maxzoom: 22
        });

        const visibility = this.layerVisibility.local ? 'visible' : 'none';

        // 4. Add Layers based on your Java Layer Names
        this.map.addLayer({
            id: 'local-polygons',
            type: 'fill',
            source: sourceId,
            'source-layer': 'myPolygons',
            layout: { visibility },
            paint: {
                'fill-color': '#0786e0',
                'fill-opacity': 0.6,
                'fill-outline-color': '#ffffff'
            }
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

        // 5. Interaction: Popup on Click
        this.map.on('click', (e) => {
            const features = this.map?.queryRenderedFeatures(e.point, {
                layers: ['local-polygons', 'local-lines', 'local-points']
            });

            if (!features?.length) return;

            const props = features[0].properties;
            let html = '<div style="color:#333; font-family: sans-serif; padding: 5px;">';
            html +=
                '<b style="font-size:12px; border-bottom:1px solid #ccc; display:block; margin-bottom:5px;">NiFi Attributes</b>';
            html += '<table style="font-size:11px; width:100%;">';
            Object.entries(props).forEach(([k, v]) => {
                html += `<tr><td style="font-weight:bold; padding-right:8px;">${k}</td><td>${v}</td></tr>`;
            });
            html += '</table></div>';

            new maplibregl.Popup({ closeButton: true }).setLngLat(e.lngLat).setHTML(html).addTo(this.map!);
        });

        // Change cursor on hover
        this.map.on('mouseenter', 'local-polygons', () => (this.map!.getCanvas().style.cursor = 'pointer'));
        this.map.on('mouseleave', 'local-polygons', () => (this.map!.getCanvas().style.cursor = ''));
    }

    /**
     * Toggles all NiFi MVT layers simultaneously
     */
    toggleNiFiLayer(): void {
        if (!this.map) return;
        this.layerVisibility.local = !this.layerVisibility.local;
        const visibility = this.layerVisibility.local ? 'visible' : 'none';

        ['local-polygons', 'local-lines', 'local-points'].forEach((id) => {
            if (this.map?.getLayer(id)) {
                this.map.setLayoutProperty(id, 'visibility', visibility);
            }
        });
    }

    ngOnDestroy(): void {
        if (this.map) {
            this.map.remove();
        }
    }
}
