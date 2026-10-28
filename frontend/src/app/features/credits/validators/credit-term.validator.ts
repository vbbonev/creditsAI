import { Directive, input, OnChanges } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';
import { termMonths } from '../../../shared/dates/date-math';

/**
 * Template-driven validator `appCreditTerm` for the end date: relative to the start date it must
 * be later, a whole number of months away on the same day of the month (clamped to the month end,
 * 31 Jan to 28 Feb), and within the allowed term. It mirrors the backend's `ValidCreditTerms`.
 * Error keys: `endNotAfterStart`, `wholeMonths`, `termRange`. A missing date is left to `required`.
 */
@Directive({
  selector: '[appCreditTerm][ngModel]',
  providers: [{ provide: NG_VALIDATORS, useExisting: CreditTermValidator, multi: true }],
})
export class CreditTermValidator implements Validator, OnChanges {
  /** The start date to measure from. */
  readonly appCreditTerm = input<Date | null>(null);
  /** Shortest allowed term in months. */
  readonly minMonths = input(1);
  /** Longest allowed term in months. */
  readonly maxMonths = input(480);

  private onValidatorChange: () => void = () => undefined;

  /**
   * Checks the end date against the start date and the term limits.
   * @param control the end date control
   * @returns the error for the first failing rule, otherwise null
   */
  validate(control: AbstractControl): ValidationErrors | null {
    const end: unknown = control.value;
    const start = this.appCreditTerm();
    if (!(end instanceof Date) || !(start instanceof Date) || isNaN(end.getTime())) {
      return null;
    }
    const startDay = new Date(start.getFullYear(), start.getMonth(), start.getDate());
    const endDay = new Date(end.getFullYear(), end.getMonth(), end.getDate());
    if (endDay.getTime() <= startDay.getTime()) {
      return { endNotAfterStart: true };
    }
    const months = termMonths(start, end);
    if (months < 1) {
      return { wholeMonths: true };
    }
    if (months < this.minMonths() || months > this.maxMonths()) {
      return { termRange: { min: this.minMonths(), max: this.maxMonths() } };
    }
    return null;
  }

  /**
   * Re-validates when the start date or a limit changes.
   */
  ngOnChanges(): void {
    this.onValidatorChange();
  }

  /**
   * Registers the callback Angular uses to re-run validation.
   * @param fn the callback
   */
  registerOnValidatorChange(fn: () => void): void {
    this.onValidatorChange = fn;
  }
}
