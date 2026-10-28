import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormsModule, NgForm } from '@angular/forms';
import { CreditTermValidator } from './credit-term.validator';

@Component({
  imports: [FormsModule, CreditTermValidator],
  template: `<form #form="ngForm">
    <input name="endDate" [ngModel]="end()" [appCreditTerm]="start()" />
  </form>`,
})
class Host {
  start = signal<Date | null>(null);
  end = signal<Date | null>(null);
}

describe('CreditTermValidator', () => {
  /**
   * Renders the host with two dates and returns the end control's errors.
   * @param start the start date
   * @param end the end date
   * @returns the fixture and the errors of the `endDate` control
   */
  async function check(start: Date | null, end: Date | null) {
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.start.set(start);
    fixture.componentInstance.end.set(end);
    await fixture.whenStable();
    const form = fixture.debugElement.children[0].injector.get(NgForm);
    return { fixture, form, errors: form.controls['endDate'].errors };
  }

  it('should accept the same day of the month', async () => {
    expect((await check(new Date(2026, 0, 15), new Date(2027, 0, 15))).errors).toBeNull();
  });

  it('should accept month-end clamping (31 Jan to 28 Feb)', async () => {
    expect((await check(new Date(2026, 0, 31), new Date(2026, 1, 28))).errors).toBeNull();
    expect((await check(new Date(2026, 0, 31), new Date(2026, 3, 30))).errors).toBeNull();
  });

  it('should reject another day of the month', async () => {
    expect((await check(new Date(2026, 0, 15), new Date(2026, 1, 16))).errors).toEqual({
      wholeMonths: true,
    });
  });

  it('should reject an end on or before the start', async () => {
    expect((await check(new Date(2026, 0, 15), new Date(2026, 0, 15))).errors).toEqual({
      endNotAfterStart: true,
    });
    expect((await check(new Date(2026, 5, 15), new Date(2026, 0, 15))).errors).toEqual({
      endNotAfterStart: true,
    });
  });

  it('should accept 480 months and reject 481', async () => {
    expect((await check(new Date(2000, 0, 1), new Date(2040, 0, 1))).errors).toBeNull();
    expect((await check(new Date(2000, 0, 1), new Date(2040, 1, 1))).errors).toEqual({
      termRange: { min: 1, max: 480 },
    });
  });

  it('should leave missing dates to required', async () => {
    expect((await check(null, new Date(2027, 0, 15))).errors).toBeNull();
    expect((await check(new Date(2026, 0, 15), null)).errors).toBeNull();
  });

  it('should re-validate when the start date changes', async () => {
    const { fixture, form } = await check(new Date(2026, 0, 15), new Date(2027, 0, 15));
    fixture.componentInstance.start.set(new Date(2026, 0, 16));
    await fixture.whenStable();
    expect(form.controls['endDate'].errors).toEqual({ wholeMonths: true });
  });
});
