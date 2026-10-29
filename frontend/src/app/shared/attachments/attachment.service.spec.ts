import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AttachmentService } from './attachment.service';

describe('AttachmentService', () => {
  let service: AttachmentService;
  let httpTesting: HttpTestingController;

  const attachment = {
    id: 3,
    fileName: 'a.pdf',
    contentType: 'application/pdf',
    sizeBytes: 10,
    uploadedBy: 'user1',
    uploadedAt: '2026-10-27T10:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AttachmentService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  for (const resource of ['clients', 'credits'] as const) {
    describe(resource, () => {
      it('should list attachments', () => {
        let received: unknown;
        service.list(resource, 1).subscribe((a) => (received = a));
        const req = httpTesting.expectOne(`/api/${resource}/1/attachments`);
        expect(req.request.method).toBe('GET');
        req.flush([attachment]);
        expect(received).toEqual([attachment]);
      });

      it('should download the content as a blob', () => {
        service.download(resource, 1, 3).subscribe();
        const req = httpTesting.expectOne(`/api/${resource}/1/attachments/3/content`);
        expect(req.request.method).toBe('GET');
        expect(req.request.responseType).toBe('blob');
        req.flush(new Blob(['x']));
      });

      it('should upload files as multipart parts "files"', () => {
        const pdf = new File(['x'], 'a.pdf', { type: 'application/pdf' });
        service.upload(resource, 1, [pdf]).subscribe();
        const req = httpTesting.expectOne(`/api/${resource}/1/attachments`);
        expect(req.request.method).toBe('POST');
        expect((req.request.body as FormData).getAll('files')).toHaveLength(1);
        req.flush([attachment], { status: 201, statusText: 'Created' });
      });

      it('should delete an attachment', () => {
        service.delete(resource, 1, 3).subscribe();
        const req = httpTesting.expectOne(`/api/${resource}/1/attachments/3`);
        expect(req.request.method).toBe('DELETE');
        req.flush(null, { status: 204, statusText: 'No Content' });
      });
    });
  }
});
