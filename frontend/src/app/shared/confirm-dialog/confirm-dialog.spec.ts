import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { ConfirmDialog, ConfirmDialogData } from './confirm-dialog';

describe('ConfirmDialog', () => {
  let dialogRef: { close: ReturnType<typeof vi.fn> };

  /**
   * Renders the dialog content with the given texts.
   * @param data the texts to show
   * @returns the rendered root element
   */
  async function render(data: ConfirmDialogData): Promise<HTMLElement> {
    dialogRef = { close: vi.fn() };
    TestBed.configureTestingModule({
      imports: [ConfirmDialog],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    });
    const fixture = TestBed.createComponent(ConfirmDialog);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  /**
   * Finds a dialog button by its label.
   * @param el the dialog element
   * @param label the visible button text
   * @returns the button
   */
  function button(el: HTMLElement, label: string): HTMLButtonElement {
    return Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === label)!;
  }

  const data: ConfirmDialogData = {
    title: 'Archive client?',
    message: '"Acme" will be hidden.',
    confirmLabel: 'Archive',
  };

  it('should show the title, the message and the confirm label', async () => {
    const el = await render(data);
    expect(el.querySelector('h2')?.textContent).toContain('Archive client?');
    expect(el.textContent).toContain('"Acme" will be hidden.');
    expect(button(el, 'Archive')).toBeTruthy();
  });

  it('should close with true when confirmed', async () => {
    const el = await render(data);
    button(el, 'Archive').click();
    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });

  it('should close without a result when cancelled', async () => {
    const el = await render(data);
    button(el, 'Cancel').click();
    expect(dialogRef.close).toHaveBeenCalledWith('');
  });
});
