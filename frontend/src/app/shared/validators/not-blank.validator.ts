import { Directive } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';

/**
 * Template-driven validator `appNotBlank`: rejects text that is only whitespace, like the backend's
 * `@NotBlank`. An empty value is left to `required`, so only one message shows per failure.
 * Error key: `notBlank`.
 */
@Directive({
  selector: '[appNotBlank][ngModel]',
  providers: [{ provide: NG_VALIDATORS, useExisting: NotBlankValidator, multi: true }],
})
export class NotBlankValidator implements Validator {
  /**
   * Checks that a non-empty text value contains at least one non-whitespace character.
   * @param control the control to check
   * @returns `{ notBlank: true }` for whitespace-only text, otherwise null
   */
  validate(control: AbstractControl): ValidationErrors | null {
    const value: unknown = control.value;
    if (typeof value !== 'string' || value.length === 0) {
      return null;
    }
    return value.trim().length === 0 ? { notBlank: true } : null;
  }
}
