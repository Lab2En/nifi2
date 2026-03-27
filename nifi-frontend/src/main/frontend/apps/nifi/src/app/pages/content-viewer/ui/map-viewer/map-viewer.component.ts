import { Component, inject, AfterViewInit, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
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
    private http = inject(HttpClient);

    private map: maplibregl.Map | undefined;
    private contextPath = 'nifi-geometry-viewer-2.8.0-SNAPSHOT';
    private clickListener: ((e: maplibregl.MapMouseEvent) => void) | null = null;

    ref: string | null = null;
    layerVisibility = {
        local: true,
        baseMap: true
    };

    constructor() {
        this.store
            .select(selectRef)
            .pipe(isDefinedAndNotNull(), takeUntilDestroyed())
            .subscribe((ref) => {
                this.ref = ref;
                if (this.map?.isStyleLoaded()) {
                    this.addNifiTileSource(ref);
                    this.zoomToDataExtent(ref);
                }
            });
    }

    ngAfterViewInit(): void {
        this.initializeMap();
    }

    private initializeMap(): void {
        // 1. Define the OSM Source
        const osmSource: maplibregl.RasterSourceSpecification = {
            type: 'raster',
            tiles: ['https://a.tile.openstreetmap.fr/osmfr/{z}/{x}/{y}.png'],
            tileSize: 256,
            attribution: '© OpenStreetMap contributors'
        };
        this.map = new maplibregl.Map({
            container: 'map-canvas',
            style: {
                version: 8,
                // Add glyphs if you plan to use labels in vector layers later
                glyphs: `https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf`,
                sources: {
                    'osm-source': osmSource
                },
                layers: [
                    {
                        id: 'osm-layer',
                        type: 'raster',
                        source: 'osm-source',
                        layout: { visibility: 'visible' },
                        minzoom: 0,
                        maxzoom: 19
                    }
                ]
            },
            center: [0, 0],
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

    private zoomToDataExtent(ref: string): void {
        const url = `/${this.contextPath}/api/geometry/bounds?ref=${encodeURIComponent(ref)}`;

        this.http.get<number[]>(url).subscribe({
            next: (bbox) => {
                if (this.map && bbox && bbox.length === 4) {
                    // Safety check: Ensure values are Lon/Lat and not Meters
                    const isInvalidLat = Math.abs(bbox[1]) > 90 || Math.abs(bbox[3]) > 90;
                    if (isInvalidLat) return;

                    this.map.fitBounds(
                        [
                            [bbox[0], bbox[1]],
                            [bbox[2], bbox[3]]
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

        // Cleanup existing layers/source
        layerIds.forEach((id) => {
            if (this.map?.getLayer(id)) this.map.removeLayer(id);
        });
        if (this.map.getSource(sourceId)) this.map.removeSource(sourceId);
        if (this.clickListener) this.map.off('click', this.clickListener);

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

        // Add Layers
        // 1. Polygons: Use a softer fill with a distinct thick border
        this.map.addLayer({
            id: 'local-polygons',
            type: 'fill',
            source: sourceId,
            'source-layer': 'myPolygons',
            layout: { visibility },
            paint: {
                'fill-color': '#00599a', // Deeper NiFi blue
                'fill-opacity': 0.85, // More transparent to see the map under it
                'fill-outline-color': '#fffAAA' // Crisp white edge
            }
        });

        // 2. Lines: Add a "Halo" effect (a white background line) so the blue line stands out
        this.map.addLayer({
            id: 'local-lines-bg', // Background line for contrast
            type: 'line',
            source: sourceId,
            'source-layer': 'myLines',
            layout: { visibility, 'line-join': 'round', 'line-cap': 'round' },
            paint: {
                'line-color': '#ffffff',
                'line-width': 4.5, // Slightly wider than the foreground
                'line-opacity': 0.85
            }
        });

        this.map.addLayer({
            id: 'local-lines',
            type: 'line',
            source: sourceId,
            'source-layer': 'myLines',
            layout: { visibility, 'line-join': 'round', 'line-cap': 'round' },
            paint: {
                'line-color': '#007ad1',
                'line-width': 2.5
            }
        });

        // 3. Points: Use a "Pulsing" look with a heavy stroke
        this.map.addLayer({
            id: 'local-points',
            type: 'circle',
            source: sourceId,
            'source-layer': 'myPoints',
            layout: { visibility },
            paint: {
                'circle-radius': 7,
                'circle-color': '#00b4eb', // Brighter cyan-blue for points
                'circle-stroke-width': 3,
                'circle-stroke-color': '#ffffff',
                'circle-pitch-alignment': 'map' // Keeps circles flat when map tilts
            }
        });

        // Click Logic
        this.clickListener = (e: maplibregl.MapMouseEvent) => {
            const features = this.map?.queryRenderedFeatures(e.point, { layers: layerIds });
            if (!features?.length) return;

            this.map?.resize();
            const coordinates = e.lngLat;
            const props = features[0].properties;

            // Move map to click location
            this.map?.flyTo({
                center: coordinates,
                zoom: Math.max(this.map.getZoom(), 14),
                essential: true
            });

            // Build Table HTML
            let html = '<div class="nifi-popup-table"><b>Feature Attributes</b><hr/><table>';
            Object.entries(props).forEach(([k, v]) => {
                html += `<tr><td><b>${k}</b></td><td>${v}</td></tr>`;
            });
            html += '</table></div>';

            // Create and show popup
            new maplibregl.Popup({
                closeButton: true,
                closeOnClick: true,
                anchor: 'bottom',
                offset: 5
            })
                .setLngLat(coordinates)
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
    toggleBaseMap(): void {
        if (!this.map) return;

        this.layerVisibility.baseMap = !this.layerVisibility.baseMap;
        const visibility = this.layerVisibility.baseMap ? 'visible' : 'none';

        // 'osm-layer' is the ID we set in initializeMap()
        if (this.map.getLayer('osm-layer')) {
            this.map.setLayoutProperty('osm-layer', 'visibility', visibility);
        }
    }
    ngOnDestroy(): void {
        if (this.map) {
            if (this.clickListener) this.map.off('click', this.clickListener);
            this.map.remove();
        }
    }
}
