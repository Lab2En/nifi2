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
    showBuildings = true;

    constructor() {
        this.store
            .select(selectRef)
            .pipe(isDefinedAndNotNull(), takeUntilDestroyed())
            .subscribe((ref) => {
                this.ref = ref;
                this.loadMapData(ref);
            });
    }

    ngAfterViewInit(): void {
        this.initializeMap();
    }

    private initializeMap(): void {
        this.map = new maplibregl.Map({
            container: 'map-canvas',
            style: 'https://demotiles.maplibre.org/style.json',
            center: [127.0276, 37.4979], // Gangnam, Seoul
            zoom: 13,
            trackResize: true
        });
        this.map.addControl(new maplibregl.NavigationControl());
        this.map.on('load', () => {
            if (!this.map) return;
            // 1. Add the Vector Source
            this.map.addSource('tegola', {
                type: 'vector',
                tiles: ['https://tiles-c.sntglobal.net/maps/keangnam/{z}/{x}/{y}.vector.pbf'],
                minzoom: 0,
                maxzoom: 20
            });

            // 2. Add the Buildings Layer
            this.map.addLayer({
                id: 'kn_buildings',
                type: 'fill',
                source: 'tegola',
                'source-layer': 'kn_buildings',
                paint: {
                    'fill-color': '#0786e0',
                    'fill-opacity': 0.8,
                    'fill-outline-color': '#ffffff'
                }
            });

            this.map.resize();

            // Load NiFi GeoJSON if it exists
            if (this.apiResponse) {
                this.updateMapSource(this.apiResponse);
            }
        });
    }

    // This matches the call in your HTML
    toggleVectorLayer(event: Event): void {
        this.showBuildings = (event.target as HTMLInputElement).checked;
        if (this.map?.getLayer('kn_buildings')) {
            const visibility = this.showBuildings ? 'visible' : 'none';
            this.map.setLayoutProperty('kn_buildings', 'visibility', visibility);
        }
    }

    private loadMapData(ref: string): void {
        const url = `/${this.contextPath}/api/geometry/hello?ref=${encodeURIComponent(ref)}`;
        this.http.get(url).subscribe({
            next: (data: any) => {
                this.apiResponse = data;
                if (this.map?.isStyleLoaded()) {
                    this.updateMapSource(data);
                }
            },
            error: (err) => console.error('Failed to contact Geometry API', err)
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
                id: 'niFiLayer',
                type: 'circle',
                source: 'niFiData',
                paint: {
                    'circle-radius': 8,
                    'circle-color': '#ffcc00',
                    'circle-stroke-width': 2,
                    'circle-stroke-color': '#000000'
                }
            });
        }
    }

    ngOnDestroy(): void {
        if (this.map) {
            this.map.remove();
        }
    }
}
