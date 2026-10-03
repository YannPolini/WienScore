import { TestBed } from '@angular/core/testing';
import { App, Assessment } from './app';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { vi } from 'vitest';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    })
      .compileComponents();
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  const success: Assessment = {
    status: 'OK', message: 'Draft student estimate.', profile: 'Student', profileDescription: 'Draft study and transport priorities.', modelVersion: 'neighbourhood-proximity-v3',
    location: { id: 'address.1', label: 'Hoher Markt 1, 1010 Wien', longitude: 16.373, latitude: 48.211 },
    candidates: [], addressRetrievedAt: '2026-09-17T07:00:00Z', score: 78, grade: 2,
    sensitivity: { stricterScore: 70, stricterGrade: 2, looserScore: 82, looserGrade: 1 },
    factors: [{ category: 'Green space', score: 75, weight: .25, contribution: 18.75, aggregation: 'MEAN', method: 'Mean of four measurements.',
      measures: [{ label: 'Any public green space', nearestId: 'park.1', nearestName: 'Test Park', distanceMetres: 375,
        targetMetres: 250, distanceLimitMetres: 750, score: 60, withinTarget: false, state: 'MEASURED' }] }],
    sources: [{ category: 'Parks', layer: 'PARKINFOOGD', url: 'https://data.wien.gv.at',
      attribution: 'Stadt Wien', license: 'CC BY 4.0', retrievedAt: '2026-09-17T07:00:00Z',
      sha256: 'abc', featureCount: 2, unnamedFeatures: 0, state: 'CACHED', message: 'Validated cache.', limitation: 'Points, not entrances.' }],
  };

  it('rejects empty input without a request', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.componentInstance.address = '  ';
    fixture.componentInstance.search();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Enter a Vienna');
    TestBed.inject(HttpTestingController).expectNone('/api/assessments');
  });

  it('submits the form, shows loading and renders the resolved location, grade and evidence', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
    input.value = 'Hoher Markt 1, 1010 Wien';
    input.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('button').disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('.calculation-status').textContent).toContain('Resolving');
    const request = TestBed.inject(HttpTestingController).expectOne('/api/assessments');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ address: input.value, profile: 'STUDENT' });
    expect(fixture.nativeElement.querySelector('select').disabled).toBe(true);
    request.flush(success);
    await fixture.whenStable();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Hoher Markt 1, 1010 Wien');
    expect(text).toContain('78.00 / 100');
    expect(text).toContain('Test Park');
    expect(text).toContain('18.75 points');
    expect(text).toContain('PARKINFOOGD');
    expect(fixture.nativeElement.querySelector('.grade').textContent).toContain('2');
  });

  it('clears the previous result immediately when the address changes', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.componentInstance.result.set(success);
    await fixture.whenStable();
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
    input.value = 'Another address 1';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.grade')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Test Park');
  });

  it('shows missing data without a grade and makes fallback dates visible', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.componentInstance.result.set({ ...success, status: 'DATA_UNAVAILABLE', score: null, grade: null,
      message: 'No overall grade: unavailable data for Pharmacies.',
      sources: [{ ...success.sources[0], state: 'STALE_FALLBACK', message: 'Refresh failed; using an older snapshot.' }] });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.grade')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('No overall grade: unavailable data for Pharmacies');
    const details: HTMLDetailsElement = fixture.nativeElement.querySelector('.source-details');
    expect(details.open).toBe(true);
    expect(details.textContent).toContain('Sep 17, 2026');
    expect(details.textContent).toContain('Older snapshot fallback');
    expect(fixture.nativeElement.textContent).toContain('Missing data prevent an overall score');
    expect(fixture.nativeElement.textContent).not.toContain('contributions add up to the overall score');
  });

  it('clears all old output on profile change and sends senior selection to the backend', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.componentInstance.address = 'Hoher Markt 1';
    fixture.componentInstance.result.set(success);
    await fixture.whenStable();
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('#profile');
    select.value = 'SENIOR';
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.grade')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Test Park');
    expect(fixture.nativeElement.textContent).not.toContain('PARKINFOOGD');
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    const request = TestBed.inject(HttpTestingController).expectOne('/api/assessments');
    expect(request.request.body).toEqual({ address: 'Hoher Markt 1', profile: 'SENIOR' });
    request.flush({ ...success, profile: 'Senior', profileDescription: 'Draft parks and pharmacy priorities.', score: 52.5, grade: 3,
      factors: [{ ...success.factors[0], weight: .35, contribution: 26.25 }] });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Senior profile.');
    expect(fixture.nativeElement.textContent).toContain('52.50 / 100');
    expect(fixture.nativeElement.textContent).toContain('35%');
    expect(fixture.nativeElement.textContent).toContain('26.25 points');
    expect(fixture.nativeElement.querySelector('.result-announcement').textContent).toContain('Senior grade 3');
  });

  it('explains zero contributions without claiming the amenity does not exist', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.componentInstance.result.set({ ...success,
      factors: [{ ...success.factors[0], score: 0, contribution: 0,
        measures: [{ ...success.factors[0].measures[0], distanceMetres: 1500, score: 0 }] }] });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Beyond the scoring cutoff');
    expect(fixture.nativeElement.textContent).toContain('does not mean the amenity is absent');
  });

  it('shows missing qualifying places without an invented distance and explains sensitivity', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.componentInstance.result.set({ ...success,
      factors: [{ ...success.factors[0], measures: [{ ...success.factors[0].measures[0],
        nearestId: null, nearestName: null, distanceMetres: null, score: 0, state: 'NO_QUALIFYING_FEATURE' }] }] });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('No qualifying feature in the complete dataset');
    expect(fixture.nativeElement.textContent).not.toContain('0 m ·');
    expect(fixture.nativeElement.textContent).toContain('70.0–82.0 / 100');
    expect(fixture.nativeElement.textContent).toContain('not a confidence interval');
    expect(fixture.nativeElement.textContent).toContain('noise');
  });

  it('does not round a near-boundary score into a better grade', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.componentInstance.result.set({ ...success, score: 79.999, grade: 2 });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.grade').textContent).toContain('2');
    expect(fixture.nativeElement.textContent).toContain('Grades use unrounded values');
  });

  it('shows backend validation feedback and can retry successfully without old output', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.componentInstance.address = 'Marchfeldstraße 10 Top 2';
    fixture.componentInstance.result.set(success);
    fixture.componentInstance.search();
    TestBed.inject(HttpTestingController).expectOne('/api/assessments').flush(
      { status: 'INVALID_INPUT', message: 'Leave out apartment or Top numbers.' }, { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Leave out apartment');
    expect(fixture.nativeElement.querySelector('.grade')).toBeNull();
    fixture.componentInstance.address = 'Hoher Markt 1';
    fixture.componentInstance.search();
    TestBed.inject(HttpTestingController).expectOne('/api/assessments').flush(success);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('.grade')).not.toBeNull();
  });

  it('prevents duplicate submissions while a request is running', () => {
    const fixture = TestBed.createComponent(App);
    fixture.componentInstance.address = 'Hoher Markt 1';
    fixture.componentInstance.search();
    fixture.componentInstance.search();
    TestBed.inject(HttpTestingController).expectOne('/api/assessments').flush(success);
  });

  it('ends a stalled request and makes retry available', () => {
    vi.useFakeTimers();
    try {
      const fixture = TestBed.createComponent(App);
      fixture.componentInstance.address = 'Hoher Markt 1';
      fixture.componentInstance.search();
      const request = TestBed.inject(HttpTestingController).expectOne('/api/assessments');
      vi.advanceTimersByTime(120_001);
      expect(request.cancelled).toBe(true);
      expect(fixture.componentInstance.busy()).toBe(false);
      expect(fixture.componentInstance.result()).toBeNull();
      expect(fixture.componentInstance.error()).toContain('took too long');
    } finally { vi.useRealTimers(); }
  });

  it('displays ambiguity without presenting a chosen location', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.componentInstance.result.set({ ...success, status: 'AMBIGUOUS', message: 'Several addresses match.',
      location: null, candidates: [success.location!], score: null, grade: null, factors: [], sources: [] });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Possible matches');
    expect(fixture.nativeElement.textContent).not.toContain('Resolved location');
    expect(fixture.nativeElement.querySelector('.grade')).toBeNull();
  });

  it('handles network failure and allows retry', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.componentInstance.address = 'Hoher Markt 1';
    fixture.componentInstance.search();
    TestBed.inject(HttpTestingController).expectOne('/api/assessments').error(new ProgressEvent('error'));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('server could not be reached');
    expect(fixture.nativeElement.querySelector('button').disabled).toBe(false);
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render title', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toBe('WienScore');
  });

  it('debounces suggestions, cancels obsolete requests and selects by keyboard', async () => {
    vi.useFakeTimers();
    try {
      const fixture = TestBed.createComponent(App);
      fixture.detectChanges();
      const app = fixture.componentInstance;
      const http = TestBed.inject(HttpTestingController);
      app.address = 'Kar'; app.addressChanged();
      vi.advanceTimersByTime(200);
      http.expectNone('/api/addresses/suggest');
      app.address = 'Karlsplatz'; app.addressChanged();
      vi.advanceTimersByTime(300);
      const old = http.expectOne('/api/addresses/suggest');
      app.address = 'Karlsplatz 13'; app.addressChanged();
      expect(old.cancelled).toBe(true);
      vi.advanceTimersByTime(300);
      const request = http.expectOne('/api/addresses/suggest');
      expect(request.request.body).toEqual({ address: 'Karlsplatz 13' });
      const match = { ...success.location!, id: 'ADRESSENOGD.123', label: 'Karlsplatz 13 Technische Universität Wien, 1040 Wien' };
      request.flush({ status: 'SUGGESTIONS', message: 'Choose an address.', matches: [match] });
      fixture.detectChanges();
      const input = fixture.nativeElement.querySelector('input');
      expect(input.getAttribute('aria-expanded')).toBe('true');
      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', cancelable: true }));
      fixture.detectChanges();
      expect(input.getAttribute('aria-activedescendant')).toBe('address-option-0');
      const enter = new KeyboardEvent('keydown', { key: 'Enter', cancelable: true });
      input.dispatchEvent(enter);
      expect(enter.defaultPrevented).toBe(true);
      expect(app.address).toBe(match.label);
      expect(app.suggestionsOpen()).toBe(false);
      http.expectNone('/api/assessments');
      app.search();
      const assessment = http.expectOne('/api/assessments');
      expect(assessment.request.body.addressId).toBe(match.id);
      assessment.flush(success);
    } finally { vi.useRealTimers(); }
  });

  it('selects a suggestion by click and discards its ID after editing', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    app.suggestions.set([{ ...success.location!, id: 'ADRESSENOGD.123' }]);
    app.suggestionsOpen.set(true);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[role="option"]').click();
    expect(app.address).toBe(success.location!.label);
    app.address = 'Hoher Markt 2'; app.addressChanged();
    app.search();
    const request = TestBed.inject(HttpTestingController).expectOne('/api/assessments');
    expect(request.request.body.addressId).toBeUndefined();
    request.flush(success);
  });

  it('dismisses with Escape and blur, including pending requests', () => {
    vi.useFakeTimers();
    try {
      const fixture = TestBed.createComponent(App);
      const app = fixture.componentInstance;
      fixture.detectChanges();
      app.address = 'Hoher'; app.addressChanged();
      vi.advanceTimersByTime(300);
      const request = TestBed.inject(HttpTestingController).expectOne('/api/addresses/suggest');
      fixture.nativeElement.querySelector('input').dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      expect(request.cancelled).toBe(true);
      expect(app.suggestionsOpen()).toBe(false);
      app.loadSuggestions();
      fixture.nativeElement.querySelector('input').dispatchEvent(new Event('blur'));
      vi.advanceTimersByTime(300);
      TestBed.inject(HttpTestingController).expectNone('/api/addresses/suggest');
    } finally { vi.useRealTimers(); }
  });

  it('shows empty and unavailable suggestions without blocking manual entry', () => {
    vi.useFakeTimers();
    try {
      const fixture = TestBed.createComponent(App);
      const app = fixture.componentInstance;
      app.address = 'Hoher'; app.addressChanged();
      vi.advanceTimersByTime(300);
      TestBed.inject(HttpTestingController).expectOne('/api/addresses/suggest')
        .flush({ status: 'SUGGESTIONS', message: 'No suggestions.', matches: [] });
      expect(app.suggestionMessage()).toBe('No suggestions.');
      app.loadSuggestions(); vi.advanceTimersByTime(300);
      TestBed.inject(HttpTestingController).expectOne('/api/addresses/suggest').error(new ProgressEvent('error'));
      expect(app.suggestionMessage()).toContain('Suggestions unavailable');
      expect(app.busy()).toBe(false);
    } finally { vi.useRealTimers(); }
  });
});
