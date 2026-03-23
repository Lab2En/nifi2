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

    ref: string | null = null;
    apiResponse: any = null;

    layerVisibility = {
        remote: true,
        local: true,
        geojson: true
    };

    constructor() {
        this.store
            .select(selectRef)
            .pipe(isDefinedAndNotNull(), takeUntilDestroyed())
            .subscribe((ref) => {
                this.ref = ref;
                this.loadMapData(ref);
                // If map is already loaded, update/add the NiFi source
                if (this.map?.isStyleLoaded()) {
                    this.addNifiTileSource(ref);
                }
            });
    }

    ngAfterViewInit(): void {
        this.initializeMap();
    }

    private initializeMap(): void {
        const remoteTileUrl = 'https://tiles-c.sntglobal.net/maps/keangnam/{z}/{x}/{y}.vector.pbf';

        this.map = new maplibregl.Map({
            container: 'map-canvas',
            style: 'https://demotiles.maplibre.org/style.json',
            center: [127.0276, 37.4979],
            zoom: 13,
            trackResize: true
        });

        this.map.addControl(new maplibregl.NavigationControl());

        this.map.on('load', () => {
            if (!this.map) return;

            // 1. Add Remote Source
            this.map.addSource('remote-source', {
                type: 'vector',
                tiles: [remoteTileUrl]
            });

            this.map.addLayer({
                id: 'remote-layer',
                type: 'fill',
                source: 'remote-source',
                'source-layer': 'kn_buildings',
                paint: { 'fill-color': '#ff0000', 'fill-opacity': 0.4 }
            });

            // 2. Add Local Source if ref is already present
            if (this.ref) {
                this.addNifiTileSource(this.ref);
            }

            // --- ADDED ONLY THIS LINE ---
            // If the GeoJSON API call finished before map was ready, draw it now.
            if (this.apiResponse) {
                this.updateMapSource(this.apiResponse);
            }

            this.map.resize();
        });
    }

    private addNifiTileSource(ref: string): void {
        if (!this.map) return;

        if (this.map.getSource('nifi-source')) {
            if (this.map.getLayer('local-layer')) this.map.removeLayer('local-layer');
            this.map.removeSource('nifi-source');
        }

        const nifiTileUrl = `${window.location.origin}/${this.contextPath}/api/geometry/tiles/{z}/{x}/{y}?ref=${encodeURIComponent(ref)}`;
        this.map.addSource('nifi-source', {
            type: 'vector',
            tiles: [nifiTileUrl]
        });

        this.map.addLayer({
            id: 'local-layer',
            type: 'fill',
            source: 'nifi-source',
            'source-layer': 'kn_buildings',
            layout: { visibility: this.layerVisibility.local ? 'visible' : 'none' },
            paint: { 'fill-color': '#0786e0', 'fill-opacity': 0.7 }
        });
    }

    toggleLayer(layerKey: 'remote' | 'local' | 'geojson', layerId: string): void {
        if (!this.map) return;
        this.layerVisibility[layerKey] = !this.layerVisibility[layerKey];
        if (this.map.getLayer(layerId)) {
            const visibility = this.layerVisibility[layerKey] ? 'visible' : 'none';
            this.map.setLayoutProperty(layerId, 'visibility', visibility);
        }
    }

    private loadMapData(ref: string): void {
        const url = `/${this.contextPath}/api/geometry/hello?ref=${encodeURIComponent(ref)}`;
        this.http.get(url).subscribe({
            next: (data: any) => {
                this.apiResponse = data;
                if (this.map?.isStyleLoaded()) this.updateMapSource(data);
            },
            error: (err) => console.error('API Error:', err)
        });
    }

    private updateMapSource(data: any): void {
        if (!this.map || !data) return;
        const geoJsonData = data.geoJson || data;
        if (this.map.getSource('niFiData')) {
            (this.map.getSource('niFiData') as maplibregl.GeoJSONSource).setData(geoJsonData);
        } else {
            this.map.addSource('niFiData', { type: 'geojson', data: geoJsonData });
            this.map.addLayer({
                id: 'geojson-layer', // This ID matches the toggleLayer call in your HTML
                type: 'circle',
                source: 'niFiData',
                layout: { visibility: this.layerVisibility.geojson ? 'visible' : 'none' },
                paint: { 'circle-radius': 8, 'circle-color': '#ffcc00', 'circle-stroke-width': 2 }
            });
        }
    }

    ngOnDestroy(): void {
        if (this.map) this.map.remove();
    }
}
