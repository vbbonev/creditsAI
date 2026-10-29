import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PendingFiles } from './pending-files';

describe('PendingFiles', () => {
  let fixture: ComponentFixture<PendingFiles>;
  let el: HTMLElement;

  /**
   * Simulates choosing files in the picker.
   * @param files the files to choose
   */
  async function pick(files: File[]): Promise<void> {
    const input = el.querySelector<HTMLInputElement>('input[type="file"]')!;
    Object.defineProperty(input, 'files', { value: files, configurable: true });
    input.dispatchEvent(new Event('change'));
    await fixture.whenStable();
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({ imports: [PendingFiles] });
    fixture = TestBed.createComponent(PendingFiles);
    await fixture.whenStable();
    el = fixture.nativeElement as HTMLElement;
  });

  it('should accept only the allowed types in the picker and show the limits', () => {
    expect(el.querySelector('input[type="file"]')!.getAttribute('accept')).toContain('.pdf');
    expect(el.textContent).toContain('Up to 30 files, 5 MB each, 20 MB in total.');
  });

  it('should collect the chosen files across several selections', async () => {
    const a = new File(['x'], 'a.pdf');
    const b = new File(['y'], 'b.png');
    await pick([a]);
    await pick([b]);
    expect(fixture.componentInstance.files()).toEqual([a, b]);
    expect(el.textContent).toContain('a.pdf');
    expect(el.textContent).toContain('b.png');
  });

  it('should remove a file with its button', async () => {
    const a = new File(['x'], 'a.pdf');
    const b = new File(['y'], 'b.png');
    await pick([a, b]);
    el.querySelector<HTMLButtonElement>('button[aria-label="Remove a.pdf"]')!.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.files()).toEqual([b]);
  });

  it('should show the given errors and disable the buttons when disabled', async () => {
    fixture.componentRef.setInput('errors', ['Too big.']);
    fixture.componentRef.setInput('disabled', true);
    await fixture.whenStable();
    expect(el.querySelector('.file-errors')!.textContent).toContain('Too big.');
    expect(el.querySelector<HTMLButtonElement>('button')!.disabled).toBe(true);
  });
});
