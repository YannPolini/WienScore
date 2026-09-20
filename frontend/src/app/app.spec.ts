import { TestBed } from '@angular/core/testing';
import { App, Assessment } from './app';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

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
    status: 'OK', message: 'Draft student estimate.', profile: 'Student', modelVersion: 'student-draft-v1',
    location: { id: 'address.1', label: 'Hoher Markt 1, 1010 Wien', longitude: 16.373, latitude: 48.211 },
    candidates: [], addressRetrievedAt: '2026-09-17T07:00:00Z', score: 78, grade: 2,
    factors: [{ category: 'Parks', nearestId: 'park.1', nearestName: 'Test Park', distanceMetres: 250,
      distanceLimitMetres: 1000, score: 75, weight: .25, contribution: 18.75 }],
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
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain('Resolving');
    const request = TestBed.inject(HttpTestingController).expectOne('/api/assessments');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ address: input.value });
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
    const details: HTMLDetailsElement = fixture.nativeElement.querySelector('details');
    expect(details.open).toBe(true);
    expect(details.textContent).toContain('Sep 17, 2026');
    expect(details.textContent).toContain('Older snapshot fallback');
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
});
