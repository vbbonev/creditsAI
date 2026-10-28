import { NgForm } from '@angular/forms';

/**
 * Shows the backend's per-field validation messages (a 400 `errors` map) on the matching form
 * controls, under the error key `server`. Angular clears the error when the user edits the field.
 * @param form the template-driven form
 * @param errors field name → message, as sent by the backend
 * @returns the messages whose field has no control in the form, so the caller can show them elsewhere
 */
export function applyServerErrors(form: NgForm, errors: Record<string, string>): string[] {
  const unmatched: string[] = [];
  for (const [field, message] of Object.entries(errors)) {
    const control = form.controls[field];
    if (control) {
      control.setErrors({ ...control.errors, server: message });
      control.markAsTouched();
    } else {
      unmatched.push(message);
    }
  }
  return unmatched;
}
