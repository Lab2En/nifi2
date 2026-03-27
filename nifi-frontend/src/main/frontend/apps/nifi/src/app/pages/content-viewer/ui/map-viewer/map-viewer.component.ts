import { Component, inject, AfterViewInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
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
    private cdr = inject(ChangeDetectorRef);

    private map: maplibregl.Map | undefined;
    private contextPath = 'nifi-geometry-viewer-2.8.0-SNAPSHOT';

    ref: string | null = null;
    currentZoom: number = 2;
    selectedFeatureProperties: any = null;

    layerVisibility = {
        local: true,
        baseMap: true,
        debug: false
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
                sources: { 'osm-source': osmSource },
                layers: [
                    {
                        id: 'osm-layer',
                        type: 'raster',
                        source: 'osm-source',
                        layout: { visibility: 'visible' }
                    }
                ]
            },
            center: [0, 0],
            zoom: 2,
            trackResize: true
        });

        this.map.on('zoom', () => {
            this.currentZoom = Math.round((this.map?.getZoom() || 0) * 100) / 100;
            this.cdr.detectChanges();
        });

        this.map.on('load', () => {
            if (!this.map) return;
            this.map.resize();
            this.addDebugGridSource();
            this.registerSelectionLogic();
            if (this.ref) {
                this.addNifiTileSource(this.ref);
                this.zoomToDataExtent(this.ref);
            }
            setTimeout(() => this.map?.resize(), 100);
        });
    }

    private registerSelectionLogic(): void {
        if (!this.map) return;

        this.map.on('click', (e) => {
            const layers = ['local-polygons', 'local-lines', 'local-points'];
            const features = this.map?.queryRenderedFeatures(e.point, {
                layers: layers.filter((id) => this.map?.getLayer(id))
            });

            // Reset everything first
            this.resetHighlights();

            if (features && features.length > 0) {
                const feature = features[0];
                this.selectedFeatureProperties = feature.properties;
                const selectedId = feature.properties?.['feature_id'];

                if (selectedId !== undefined) {
                    const layerMapping: { [key: string]: string } = {
                        'local-polygons': 'h-poly',
                        'local-lines': 'h-line',
                        'local-points': 'h-point'
                    };

                    const hLayer = layerMapping[feature.layer.id];
                    if (hLayer && this.map?.getLayer(hLayer)) {
                        this.map.setFilter(hLayer, ['==', ['get', 'feature_id'], selectedId]);
                    }
                }
            }
            this.cdr.detectChanges();
        });

        this.map.on('mousemove', (e) => {
            const layers = ['local-polygons', 'local-lines', 'local-points'];
            const f = this.map?.queryRenderedFeatures(e.point, { layers: layers.filter((l) => this.map?.getLayer(l)) });
            this.map!.getCanvas().style.cursor = f && f.length > 0 ? 'pointer' : '';
        });
    }

    private addNifiTileSource(ref: string): void {
        if (!this.map) return;
        const sourceId = 'nifi-source';

        // Remove existing layers and source
        const existingLayers = this.map.getStyle().layers || [];
        existingLayers.forEach((l) => {
            if (l.id.startsWith('local-') || l.id.startsWith('h-')) this.map?.removeLayer(l.id);
        });
        if (this.map.getSource(sourceId)) this.map.removeSource(sourceId);

        const url = new URL(`/${this.contextPath}/api/geometry/tiles/{z}/{x}/{y}`, window.location.origin);
        url.searchParams.set('ref', ref);

        this.map.addSource(sourceId, {
            type: 'vector',
            tiles: [decodeURI(url.toString())],
            promoteId: 'id' // Ensure your features have an "id" property
        });

        const v = this.layerVisibility.local ? 'visible' : 'none';

        // --- Polygons ---
        this.map.addLayer({
            id: 'local-polygons',
            type: 'fill',
            source: sourceId,
            'source-layer': 'myPolygons',
            layout: { visibility: v },
            paint: { 'fill-color': '#00599a', 'fill-opacity': 0.85 }
        });
        this.map.addLayer({
            id: 'h-poly',
            type: 'line',
            source: sourceId,
            'source-layer': 'myPolygons',
            filter: ['==', ['get', 'feature_id'], -1],
            paint: { 'line-color': '#ffa500', 'line-width': 3 }
        });

        // --- Lines ---
        this.map.addLayer({
            id: 'local-lines',
            type: 'line',
            source: sourceId,
            'source-layer': 'myLines',
            layout: { visibility: v },
            paint: { 'line-color': '#007ad1', 'line-width': 2 }
        });
        this.map.addLayer({
            id: 'h-line',
            type: 'line',
            source: sourceId,
            'source-layer': 'myLines',
            filter: ['==', ['get', 'feature_id'], -1],
            paint: { 'line-color': '#ffa500', 'line-width': 4 }
        });

        // --- Points ---
        this.map.addLayer({
            id: 'local-points',
            type: 'circle',
            source: sourceId,
            'source-layer': 'myPoints',
            layout: { visibility: v },
            paint: {
                'circle-radius': 6,
                'circle-color': '#00b4eb',
                'circle-stroke-width': 2,
                'circle-stroke-color': '#fff'
            }
        });
        this.map.addLayer({
            id: 'h-point',
            type: 'circle',
            source: sourceId,
            'source-layer': 'myPoints',
            filter: ['==', ['id'], -1],
            paint: {
                'circle-radius': 9,
                'circle-color': '#ffa500',
                'circle-stroke-width': 2,
                'circle-stroke-color': '#fff'
            }
        });

        if (this.map.getLayer('debug-grid-layer')) this.map.moveLayer('debug-grid-layer');
    }

    private async addDebugGridSource(): Promise<void> {
        if (!this.map) return;

        maplibregl.addProtocol('debug-grid', async (params) => {
            const parts = params.url.split('/');
            const z = parseInt(parts[parts.length - 3]);
            const x = parseInt(parts[parts.length - 2]);
            const y = parseInt(parts[parts.length - 1]);

            const canvas = document.createElement('canvas');
            canvas.width = 256;
            canvas.height = 256;
            const ctx = canvas.getContext('2d')!;

            // 1. Draw Red Border
            ctx.strokeStyle = 'red';
            ctx.lineWidth = 2;
            ctx.strokeRect(0, 0, 256, 256);

            // 2. Background for text
            ctx.fillStyle = 'rgba(255, 255, 255, 0.85)';
            ctx.fillRect(0, 0, 256, 45);

            // 3. X, Y, Z Labels
            ctx.fillStyle = 'black';
            ctx.font = 'bold 11px monospace';
            ctx.fillText(`X:${x} Y:${y} Z:${z}`, 10, 18);

            // 4. RESTORED: Lat/Lon Math
            const n = Math.PI - (2 * Math.PI * y) / Math.pow(2, z);
            const lat = ((180 / Math.PI) * Math.atan(0.5 * (Math.exp(n) - Math.exp(-n)))).toFixed(4);
            const lon = ((x / Math.pow(2, z)) * 360 - 180).toFixed(4);

            ctx.fillText(`NW: ${lat}, ${lon}`, 10, 36);

            const blob = await new Promise<Blob | null>((res) => canvas.toBlob(res));
            return { data: await blob!.arrayBuffer() };
        });

        this.map.addSource('debug-grid-source', {
            type: 'raster',
            tiles: ['debug-grid://{z}/{x}/{y}'],
            tileSize: 256
        });

        this.map.addLayer({
            id: 'debug-grid-layer',
            type: 'raster',
            source: 'debug-grid-source',
            layout: { visibility: this.layerVisibility.debug ? 'visible' : 'none' }
        });
    }

    private zoomToDataExtent(ref: string): void {
        this.http
            .get<number[]>(`/${this.contextPath}/api/geometry/bounds?ref=${encodeURIComponent(ref)}`)
            .subscribe((bbox) => {
                if (this.map && bbox?.length === 4) {
                    this.map.fitBounds(
                        [
                            [bbox[0], bbox[1]],
                            [bbox[2], bbox[3]]
                        ],
                        { padding: 50 }
                    );
                }
            });
    }

    toggleNiFiLayer(): void {
        this.layerVisibility.local = !this.layerVisibility.local;
        const v = this.layerVisibility.local ? 'visible' : 'none';
        ['local-polygons', 'local-lines', 'local-points'].forEach((id) => {
            if (this.map?.getLayer(id)) this.map.setLayoutProperty(id, 'visibility', v);
        });
    }

    toggleBaseMap(): void {
        this.layerVisibility.baseMap = !this.layerVisibility.baseMap;
        if (this.map?.getLayer('osm-layer'))
            this.map.setLayoutProperty('osm-layer', 'visibility', this.layerVisibility.baseMap ? 'visible' : 'none');
    }

    toggleDebugGrid(): void {
        this.layerVisibility.debug = !this.layerVisibility.debug;
        if (this.map?.getLayer('debug-grid-layer'))
            this.map.setLayoutProperty(
                'debug-grid-layer',
                'visibility',
                this.layerVisibility.debug ? 'visible' : 'none'
            );
    }
    resetHighlights(): void {
        if (!this.map) return;

        // Set filter to -1 (or any value that won't exist) to hide the highlight layers
        const hLayers = ['h-poly', 'h-line', 'h-point'];
        hLayers.forEach((id) => {
            if (this.map?.getLayer(id)) {
                // We use the same ['get', 'feature_id'] logic used in the selection
                this.map.setFilter(id, ['==', ['get', 'feature_id'], -1]);
            }
        });

        this.selectedFeatureProperties = null;
        this.cdr.detectChanges();
    }
    ngOnDestroy(): void {
        this.map?.remove();
    }
}
