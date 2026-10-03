import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { DatePipe, DecimalPipe } from '@angular/common';
import { timeout, Subject, timer, switchMap, of, catchError } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

interface Match { id: string; label: string; longitude: number; latitude: number; }
interface Measure {
  label: string; nearestId: string | null; nearestName: string | null; distanceMetres: number | null;
  targetMetres: number; distanceLimitMetres: number; score: number; withinTarget: boolean; state: string;
}
interface Factor {
  category: string; score: number; weight: number; contribution: number; aggregation: string; method: string; measures: Measure[];
}
interface Source {
  category: string; layer: string; url: string; attribution: string; license: string;
  retrievedAt: string | null; sha256: string | null; featureCount: number | null;
  unnamedFeatures: number | null; state: string; message: string; limitation: string;
}
export interface Assessment {
  status: string; message: string; profile: string; profileDescription: string; modelVersion: string;
  location: Match | null; candidates: Match[]; addressRetrievedAt: string | null;
  score: number | null; grade: number | null; factors: Factor[]; sources: Source[];
  sensitivity: { stricterScore: number; stricterGrade: number; looserScore: number; looserGrade: number } | null;
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
  profile: 'STUDENT' | 'SENIOR' = 'STUDENT';
  readonly busy = signal(false);
  readonly error = signal('');
  readonly result = signal<Assessment | null>(null);

  readonly suggestions = signal<Match[]>([]);
  readonly suggestionMessage = signal('');
  readonly suggesting = signal(false);
  readonly suggestionsOpen = signal(false);
  readonly activeSuggestion = signal(-1);
  private selectedAddress: Match | null = null;
  private readonly addressQueries = new Subject<string | null>();

  constructor() {
    this.addressQueries.pipe(
      switchMap(query => query === null ? of(null) : timer(300).pipe(
        switchMap(() => this.http.post<{ status: string; message: string; matches: Match[] }>(
          '/api/addresses/suggest', { address: query }).pipe(timeout(15_000))),
        catchError(() => of({ status: 'LOOKUP_UNAVAILABLE', matches: [],
          message: 'Suggestions unavailable. Try again or enter the exact address.' }))
      )),
      takeUntilDestroyed()
    ).subscribe(response => {
      this.suggesting.set(false);
      if (response === null) return;
      this.suggestions.set(response.matches);
      this.suggestionMessage.set(response.message);
      this.suggestionsOpen.set(true);
    });
  }

  addressChanged() {
    this.selectedAddress = null;
    this.clearResult();
    this.loadSuggestions();
  }

  loadSuggestions() {
    this.dismissSuggestions();
    if (this.busy() || this.selectedAddress || this.address.trim().length < 3) return;
    this.suggesting.set(true);
    this.addressQueries.next(this.address);
  }

  dismissSuggestions() {
    this.addressQueries.next(null);
    this.suggesting.set(false);
    this.suggestionsOpen.set(false);
    this.suggestions.set([]);
    this.suggestionMessage.set('');
    this.activeSuggestion.set(-1);
  }

  chooseAddress(match: Match) {
    this.address = match.label;
    this.selectedAddress = match;
    this.clearResult();
    this.dismissSuggestions();
  }

  addressKeydown(event: KeyboardEvent) {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.dismissSuggestions();
    } else if (this.suggestionsOpen() && this.suggestions().length) {
      if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        event.preventDefault();
        const count = this.suggestions().length;
        const next = this.activeSuggestion() < 0 ? (event.key === 'ArrowDown' ? 0 : count - 1)
          : (this.activeSuggestion() + (event.key === 'ArrowDown' ? 1 : -1) + count) % count;
        this.activeSuggestion.set(next);
      } else if (event.key === 'Enter' && this.activeSuggestion() >= 0) {
        event.preventDefault();
        this.chooseAddress(this.suggestions()[this.activeSuggestion()]);
      }
    }
  }

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
    this.dismissSuggestions();
    this.busy.set(true);
    this.http.post<Assessment>('/api/assessments', { address: this.address, profile: this.profile,
      ...(this.selectedAddress ? { addressId: this.selectedAddress.id } : {}) })
      .pipe(timeout(120_000)).subscribe({
      next: (result) => { this.result.set(result); this.busy.set(false); },
      error: (error) => {
        this.error.set(error.name === 'TimeoutError'
          ? 'The calculation took too long. Please try again.' : typeof error.error?.message === 'string'
          ? error.error.message : 'The server could not be reached. Please try again.');
        this.busy.set(false);
      },
    });
  }
}
