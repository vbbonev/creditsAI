import { Directive, input } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';

/**
 * Template-driven validator `appMaxDecimals`: a number may have at most the given count of
 * decimal places, like the backend's `@Digits(fraction = n)`. An empty value is left to
 * `required`. Error key: `maxDecimals`.
 */
@Directive({
  selector: '[appMaxDecimals][ngModel]',
  providers: [{ provide: NG_VALIDATORS, useExisting: MaxDecimalsValidator, multi: true }],
})
export class MaxDecimalsValidator implements Validator {
  /** Largest allowed number of decimal places. */
  readonly appMaxDecimals = input.required<number>();

  /**
   * Checks the number of decimal places of a numeric value.
   * @param control the control to check
   * @returns `{ maxDecimals: { max } }` if there are too many decimals, otherwise null
   */
  validate(control: AbstractControl): ValidationErrors | null {
    const value: unknown = control.value;
    if (typeof value !== 'number' || !Number.isFinite(value)) {
      return null;
    }
    const max = this.appMaxDecimals();
    const match = /^-?\d+(?:\.(\d+))?$/.exec(value.toString());
    // exponent notation (e.g. 1e-7) never matches: such a value has too many decimals anyway
    const decimals = match ? (match[1]?.length ?? 0) : Infinity;
    return decimals > max ? { maxDecimals: { max } } : null;
  }
}
