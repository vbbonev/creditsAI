import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CreditQuery, CreditRequest } from './credit.model';
import { CreditService } from './credit.service';

describe('CreditService', () => {
  let service: CreditService;
  let httpTesting: HttpTestingController;

  const query: CreditQuery = {
    search: '',
    status: null,
    includeArchived: false,
    page: 0,
    size: 10,
    sort: 'creditNumber',
    direction: 'asc',
  };
  const request: CreditRequest = {
    clientId: 3,
    type: 'CONSUMER',
    amount: 10000,
    currency: 'EUR',
    interestRate: 5.25,
    startDate: '2026-01-15',
    endDate: '2027-01-15',
    hasCollateral: false,
    collateralValue: null,
    collateralDescription: null,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CreditService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('should list with page, size and sort, and without search, status or includeArchived by default', () => {
    service.list(query).subscribe();
    const req = httpTesting.expectOne((r) => r.url === '/api/credits');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('sort')).toBe('creditNumber,asc');
    expect(req.request.params.has('search')).toBe(false);
    expect(req.request.params.has('status')).toBe(false);
    expect(req.request.params.has('includeArchived')).toBe(false);
    req.flush({ content: [], page: { size: 10, number: 0, totalElements: 0, totalPages: 0 } });
  });

  it('should send the trimmed search, status, includeArchived and the chosen sort', () => {
    service
      .list({
        ...query,
        search: '  CR-2026 ',
        status: 'ACTIVE',
        includeArchived: true,
        page: 2,
        size: 50,
        sort: 'amount',
        direction: 'desc',
      })
      .subscribe();
    const req = httpTesting.expectOne((r) => r.url === '/api/credits');
    expect(req.request.params.get('search')).toBe('CR-2026');
    expect(req.request.params.get('status')).toBe('ACTIVE');
    expect(req.request.params.get('includeArchived')).toBe('true');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('50');
    expect(req.request.params.get('sort')).toBe('amount,desc');
    req.flush({ content: [], page: { size: 50, number: 2, totalElements: 0, totalPages: 0 } });
  });

  it('should not send a blank search', () => {
    service.list({ ...query, search: '   ' }).subscribe();
    const req = httpTesting.expectOne((r) => r.url === '/api/credits');
    expect(req.request.params.has('search')).toBe(false);
    req.flush({ content: [], page: { size: 10, number: 0, totalElements: 0, totalPages: 0 } });
  });

  it('should get one credit by id', () => {
    service.get(5).subscribe();
    const req = httpTesting.expectOne('/api/credits/5');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('should post the calculation inputs to /calculate', () => {
    const inputs = {
      amount: 10000,
      currency: 'EUR' as const,
      interestRate: 5.25,
      startDate: '2026-01-15',
      endDate: '2027-01-15',
    };
    service.calculate(inputs).subscribe();
    const req = httpTesting.expectOne('/api/credits/calculate');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(inputs);
    req.flush({});
  });

  it('should post a new credit', () => {
    service.create(request).subscribe();
    const req = httpTesting.expectOne('/api/credits');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('should post a new credit with files as multipart: JSON part "credit" and parts "files"', async () => {
    const pdf = new File(['x'], 'a.pdf', { type: 'application/pdf' });
    const png = new File(['y'], 'b.png', { type: 'image/png' });
    service.createWithFiles(request, [pdf, png]).subscribe();
    const req = httpTesting.expectOne('/api/credits');
    expect(req.request.method).toBe('POST');
    const body = req.request.body as FormData;
    expect(body).toBeInstanceOf(FormData);
    const part = body.get('credit') as Blob;
    expect(part.type).toBe('application/json');
    expect(JSON.parse(await part.text())).toEqual(request);
    expect((body.getAll('files') as File[]).map((f) => f.name)).toEqual(['a.pdf', 'b.png']);
    req.flush({});
  });

  it('should send only the credit part when there are no files', () => {
    service.createWithFiles(request, []).subscribe();
    const body = httpTesting.expectOne('/api/credits').request.body as FormData;
    expect(body.has('credit')).toBe(true);
    expect(body.has('files')).toBe(false);
  });

  it('should put an updated credit', () => {
    service.update(5, request).subscribe();
    const req = httpTesting.expectOne('/api/credits/5');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('should post the new status', () => {
    service.changeStatus(5, 'INACTIVE').subscribe();
    const req = httpTesting.expectOne('/api/credits/5/status');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ status: 'INACTIVE' });
    req.flush({});
  });

  it('should archive with DELETE', () => {
    service.archive(5).subscribe();
    const req = httpTesting.expectOne('/api/credits/5');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('should restore with POST', () => {
    service.restore(5).subscribe();
    const req = httpTesting.expectOne('/api/credits/5/restore');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('should search clients by text and reduce them to id, name and identifier', () => {
    let result: unknown;
    service.searchClients(' Acme ').subscribe((clients) => (result = clients));
    const req = httpTesting.expectOne((r) => r.url === '/api/clients');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('search')).toBe('Acme');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('sort')).toBe('name,asc');
    expect(req.request.params.has('includeArchived')).toBe(false);
    req.flush({
      content: [{ id: 1, name: 'Acme', identifier: '123', address: 'Sofia', archived: false }],
      page: { size: 10, number: 0, totalElements: 1, totalPages: 1 },
    });
    expect(result).toEqual([{ id: 1, name: 'Acme', identifier: '123' }]);
  });
});
