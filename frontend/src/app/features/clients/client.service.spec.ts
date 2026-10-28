import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Client, ClientQuery, ClientRequest } from './client.model';
import { ClientService } from './client.service';

describe('ClientService', () => {
  let service: ClientService;
  let httpTesting: HttpTestingController;

  const client: Client = {
    id: 1,
    name: 'Acme',
    identifier: '123',
    address: 'Sofia',
    description: null,
    archived: false,
    archivedAt: null,
    createdAt: '2026-10-27T10:00:00Z',
    updatedAt: '2026-10-27T10:00:00Z',
  };
  const request: ClientRequest = {
    name: 'Acme',
    identifier: '123',
    address: 'Sofia',
    description: null,
  };
  const query: ClientQuery = {
    search: '',
    includeArchived: false,
    page: 0,
    size: 10,
    sort: 'name',
    direction: 'asc',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ClientService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('should list with page, size and sort, and without search or includeArchived by default', () => {
    service.list(query).subscribe();
    const req = httpTesting.expectOne((r) => r.url === '/api/clients');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('sort')).toBe('name,asc');
    expect(req.request.params.has('search')).toBe(false);
    expect(req.request.params.has('includeArchived')).toBe(false);
    req.flush({ content: [], page: { size: 10, number: 0, totalElements: 0, totalPages: 0 } });
  });

  it('should send the trimmed search text, includeArchived and the chosen sort', () => {
    service
      .list({
        ...query,
        search: '  acme ',
        includeArchived: true,
        page: 2,
        sort: 'identifier',
        direction: 'desc',
      })
      .subscribe();
    const req = httpTesting.expectOne((r) => r.url === '/api/clients');
    expect(req.request.params.get('search')).toBe('acme');
    expect(req.request.params.get('includeArchived')).toBe('true');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('sort')).toBe('identifier,desc');
    req.flush({ content: [], page: { size: 10, number: 2, totalElements: 0, totalPages: 0 } });
  });

  it('should not send a whitespace-only search', () => {
    service.list({ ...query, search: '   ' }).subscribe();
    const req = httpTesting.expectOne((r) => r.url === '/api/clients');
    expect(req.request.params.has('search')).toBe(false);
    req.flush({ content: [], page: { size: 10, number: 0, totalElements: 0, totalPages: 0 } });
  });

  it('should get one client by id', () => {
    let received: Client | undefined;
    service.get(1).subscribe((c) => (received = c));
    const req = httpTesting.expectOne('/api/clients/1');
    expect(req.request.method).toBe('GET');
    req.flush(client);
    expect(received).toEqual(client);
  });

  it('should create a client with POST', () => {
    service.create(request).subscribe();
    const req = httpTesting.expectOne('/api/clients');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(client, { status: 201, statusText: 'Created' });
  });

  it('should update a client with PUT', () => {
    service.update(1, request).subscribe();
    const req = httpTesting.expectOne('/api/clients/1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush(client);
  });

  it('should archive a client with DELETE', () => {
    service.archive(1).subscribe();
    const req = httpTesting.expectOne('/api/clients/1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('should restore a client with POST to /restore', () => {
    service.restore(1).subscribe();
    const req = httpTesting.expectOne('/api/clients/1/restore');
    expect(req.request.method).toBe('POST');
    req.flush(client);
  });

  describe('attachments', () => {
    const attachment = {
      id: 3,
      fileName: 'a.pdf',
      contentType: 'application/pdf',
      sizeBytes: 10,
      uploadedBy: 'user1',
      uploadedAt: '2026-10-27T10:00:00Z',
    };

    it('should create with a multipart body: JSON part "client" and parts "files"', async () => {
      const pdf = new File(['x'], 'a.pdf', { type: 'application/pdf' });
      const png = new File(['y'], 'b.png', { type: 'image/png' });
      service.createWithFiles(request, [pdf, png]).subscribe();
      const req = httpTesting.expectOne('/api/clients');
      expect(req.request.method).toBe('POST');
      const body = req.request.body as FormData;
      expect(body).toBeInstanceOf(FormData);
      const part = body.get('client') as Blob;
      expect(part.type).toBe('application/json');
      expect(JSON.parse(await part.text())).toEqual(request);
      const files = body.getAll('files') as File[];
      expect(files.map((f) => f.name)).toEqual(['a.pdf', 'b.png']);
      req.flush(client, { status: 201, statusText: 'Created' });
    });

    it('should create with only the client part when there are no files', () => {
      service.createWithFiles(request, []).subscribe();
      const req = httpTesting.expectOne('/api/clients');
      const body = req.request.body as FormData;
      expect(body.has('client')).toBe(true);
      expect(body.has('files')).toBe(false);
      req.flush(client, { status: 201, statusText: 'Created' });
    });

    it('should list attachments', () => {
      let received: unknown;
      service.listAttachments(1).subscribe((a) => (received = a));
      const req = httpTesting.expectOne('/api/clients/1/attachments');
      expect(req.request.method).toBe('GET');
      req.flush([attachment]);
      expect(received).toEqual([attachment]);
    });

    it('should download the content as a blob', () => {
      service.downloadAttachment(1, 3).subscribe();
      const req = httpTesting.expectOne('/api/clients/1/attachments/3/content');
      expect(req.request.method).toBe('GET');
      expect(req.request.responseType).toBe('blob');
      req.flush(new Blob(['x']));
    });

    it('should upload files as multipart parts "files"', () => {
      const pdf = new File(['x'], 'a.pdf', { type: 'application/pdf' });
      service.uploadAttachments(1, [pdf]).subscribe();
      const req = httpTesting.expectOne('/api/clients/1/attachments');
      expect(req.request.method).toBe('POST');
      expect((req.request.body as FormData).getAll('files')).toHaveLength(1);
      req.flush([attachment], { status: 201, statusText: 'Created' });
    });

    it('should delete an attachment', () => {
      service.deleteAttachment(1, 3).subscribe();
      const req = httpTesting.expectOne('/api/clients/1/attachments/3');
      expect(req.request.method).toBe('DELETE');
      req.flush(null, { status: 204, statusText: 'No Content' });
    });
  });
});
