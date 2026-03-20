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
            container: 'map-canvas', // matches ID in HTML
            style: 'https://demotiles.maplibre.org/style.json', // Vector style
            center: [100.5018, 13.7563], // Default to Bangkok or [0,0]
            zoom: 5,
            trackResize: true
        });

        this.map.addControl(new maplibregl.NavigationControl());

        this.map.on('load', () => {
            this.map?.resize();
            console.log('MapLibre ready');
            if (this.apiResponse) {
                this.updateMapSource(this.apiResponse);
            }
        });
    }

    private loadMapData(ref: string): void {
        const url = `/${this.contextPath}/api/geometry/hello?ref=${encodeURIComponent(ref)}`;

        this.http.get(url).subscribe({
            next: (data: any) => {
                this.apiResponse = data;
                // If map is already loaded, update it now
                if (this.map?.loaded()) {
                    this.updateMapSource(data);
                }
            },
            error: (err) => console.error('Failed to contact Geometry API', err)
        });
    }

    private updateMapSource(data: any): void {
        if (!this.map || !data) return;

        // Assuming your API returns a 'message' that contains GeoJSON
        // OR change this to 'data' if your API returns GeoJSON directly
        const geoJsonData = data.geoJson || data;

        if (this.map.getSource('niFiData')) {
            (this.map.getSource('niFiData') as maplibregl.GeoJSONSource).setData(geoJsonData);
        } else {
            this.map.addSource('niFiData', {
                type: 'geojson',
                data: geoJsonData
            });

            this.map.addLayer({
                id: 'niFiLayer',
                type: 'circle', // or 'fill' / 'line' depending on your geometry
                source: 'niFiData',
                paint: {
                    'circle-radius': 6,
                    'circle-color': '#007cbf'
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
