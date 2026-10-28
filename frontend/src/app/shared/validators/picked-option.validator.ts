import { Directive } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';

/**
 * Template-driven validator `appPickedOption` for autocomplete inputs whose model is the chosen
 * option object: text that was typed but not picked from the list is invalid. An empty value is
 * left to `required`. Error key: `notPicked`.
 */
@Directive({
  selector: '[appPickedOption][ngModel]',
  providers: [{ provide: NG_VALIDATORS, useExisting: PickedOptionValidator, multi: true }],
})
export class PickedOptionValidator implements Validator {
  /**
   * Checks that a non-empty value is an option object and not typed text.
   * @param control the control to check
   * @returns `{ notPicked: true }` for non-empty text, otherwise null
   */
  validate(control: AbstractControl): ValidationErrors | null {
    const value: unknown = control.value;
    return typeof value === 'string' && value.length > 0 ? { notPicked: true } : null;
  }
}
