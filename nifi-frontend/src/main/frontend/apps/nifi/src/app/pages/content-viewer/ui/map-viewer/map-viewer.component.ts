import { Component, inject, AfterViewInit, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http'; // Added import
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
    private http = inject(HttpClient); // Re-added the missing http property

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
                    this.zoomToDataExtent(ref); // New: Auto-zoom when ref changes
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
            center: [0, 0], // Start at neutral 0,0; zoomToDataExtent will move it
            zoom: 2,
            trackResize: true
        });

        this.map.addControl(new maplibregl.NavigationControl());

        this.map.on('load', () => {
            if (!this.map) return;
            if (this.ref) {
                this.addNifiTileSource(this.ref);
                this.zoomToDataExtent(this.ref);
            }
            setTimeout(() => this.map?.resize(), 100);
        });
    }

    /**
     * Fetches the Bounding Box from the API and zooms the map to fit.
     * Expects API to return: [minLng, minLat, maxLng, maxLat]
     */
    private zoomToDataExtent(ref: string): void {
        const url = `/${this.contextPath}/api/geometry/bounds?ref=${encodeURIComponent(ref)}`;

        this.http.get<number[]>(url).subscribe({
            next: (bbox) => {
                if (this.map && bbox && bbox.length === 4) {
                    // DEBUG: Look at your console to see what the Java API sent
                    console.log('Received BBox from API:', bbox);

                    // Check if the coordinates are actually Lat/Lng (Lat must be -90 to 90)
                    const isInvalidLat = Math.abs(bbox[1]) > 90 || Math.abs(bbox[3]) > 90;

                    if (isInvalidLat) {
                        console.error(
                            'API returned Projected coordinates (Meters) instead of WGS84 (Degrees). Zoom cancelled.'
                        );
                        return;
                    }

                    this.map.fitBounds(
                        [
                            [bbox[0], bbox[1]], // Southwest [Lon, Lat]
                            [bbox[2], bbox[3]] // Northeast [Lon, Lat]
                        ],
                        { padding: 40, duration: 1200, essential: true }
                    );
                }
            },
            error: (err) => console.warn('Could not zoom to extent.', err)
        });
    }

    private addNifiTileSource(ref: string): void {
        if (!this.map) return;

        const sourceId = 'nifi-source';
        const layerIds = ['local-polygons', 'local-lines', 'local-points'];

        layerIds.forEach((id) => {
            if (this.map?.getLayer(id)) this.map.removeLayer(id);
        });
        if (this.map.getSource(sourceId)) this.map.removeSource(sourceId);

        if (this.clickListener) {
            this.map.off('click', this.clickListener);
        }

        const baseUrl = window.location.origin;
        const path = `/${this.contextPath}/api/geometry/tiles/{z}/{x}/{y}`;
        const urlWithParams = new URL(path, baseUrl);
        urlWithParams.searchParams.set('ref', ref);
        const fullNifiTileUrl = decodeURI(urlWithParams.toString());

        this.map.addSource(sourceId, {
            type: 'vector',
            tiles: [fullNifiTileUrl],
            minzoom: 0,
            maxzoom: 18
        });

        const visibility = this.layerVisibility.local ? 'visible' : 'none';

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

        this.clickListener = (e: maplibregl.MapMouseEvent) => {
            const features = this.map?.queryRenderedFeatures(e.point, { layers: layerIds });
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
