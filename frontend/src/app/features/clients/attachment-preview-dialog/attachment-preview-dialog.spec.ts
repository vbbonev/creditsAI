import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { AttachmentPreviewDialog } from './attachment-preview-dialog';

describe('AttachmentPreviewDialog', () => {
  it('should show the picture with its file name', async () => {
    TestBed.configureTestingModule({
      imports: [AttachmentPreviewDialog],
      providers: [{ provide: MAT_DIALOG_DATA, useValue: { fileName: 'cat.png', url: 'blob:x' } }],
    });
    const fixture = TestBed.createComponent(AttachmentPreviewDialog);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const img = el.querySelector('img')!;
    expect(img.getAttribute('src')).toBe('blob:x');
    expect(img.alt).toBe('cat.png');
    expect(el.querySelector('h2')?.textContent).toContain('cat.png');
  });
});
