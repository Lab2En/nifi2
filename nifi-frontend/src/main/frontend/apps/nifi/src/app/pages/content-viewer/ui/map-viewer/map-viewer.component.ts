import { Component, inject, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Store } from '@ngrx/store';
import { NiFiState } from '../../../../state';
import { selectRef } from '../../state/content/content.selectors';
import { isDefinedAndNotNull } from '@nifi/shared';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'map-viewer',
    standalone: true,
    templateUrl: './map-viewer.component.html',
    imports: [CommonModule],
    styleUrls: ['./map-viewer.component.scss']
})
export class MapViewer {
    private store = inject<Store<NiFiState>>(Store);
    private http = inject(HttpClient);

    ref: string | null = null;
    apiResponse: any = null; // To store the "Hello World Map" JSON

    constructor() {
        this.store
            .select(selectRef)
            .pipe(isDefinedAndNotNull(), takeUntilDestroyed())
            .subscribe((ref) => {
                this.ref = ref;
                this.loadMapData(ref);
            });
    }

    private loadMapData(ref: string): void {
        // Construct the URL to your JAX-RS Resource
        // The context path is usually your artifact ID + version
        const contextPath = 'nifi-geometry-viewer-2.8.0-SNAPSHOT';
        const url = `/${contextPath}/api/geometry/hello?ref=${encodeURIComponent(ref)}`;

        this.http.get(url).subscribe({
            next: (data) => {
                this.apiResponse = data;
                console.log('Data received from GeometryResource:', data);
            },
            error: (err) => {
                console.error('Failed to contact Geometry API', err);
            }
        });
    }
}