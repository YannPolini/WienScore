import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { DatePipe, DecimalPipe } from '@angular/common';

interface Match { id: string; label: string; longitude: number; latitude: number; }
interface Factor {
  category: string; nearestId: string; nearestName: string; distanceMetres: number;
  distanceLimitMetres: number; score: number; weight: number; contribution: number;
}
interface Source {
  category: string; layer: string; url: string; attribution: string; license: string;
  retrievedAt: string | null; sha256: string | null; featureCount: number | null;
  unnamedFeatures: number | null; state: string; message: string; limitation: string;
}
export interface Assessment {
  status: string; message: string; profile: string; modelVersion: string;
  location: Match | null; candidates: Match[]; addressRetrievedAt: string | null;
  score: number | null; grade: number | null; factors: Factor[]; sources: Source[];
}

@Component({
  imports: [FormsModule, DatePipe, DecimalPipe],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  private readonly http = inject(HttpClient);
  address = '';
  readonly busy = signal(false);
  readonly error = signal('');
  readonly result = signal<Assessment | null>(null);

  clearResult() {
    this.result.set(null);
    this.error.set('');
  }

  search() {
    if (this.busy()) return;
    this.result.set(null);
    this.error.set('');
    if (!this.address.trim()) {
      this.error.set('Enter a Vienna street and house number.');
      return;
    }
    this.busy.set(true);
    this.http.post<Assessment>('/api/assessments', { address: this.address }).subscribe({
      next: (result) => { this.result.set(result); this.busy.set(false); },
      error: (error) => {
        this.error.set(typeof error.error?.message === 'string'
          ? error.error.message : 'The server could not be reached. Please try again.');
        this.busy.set(false);
      },
    });
  }
}
