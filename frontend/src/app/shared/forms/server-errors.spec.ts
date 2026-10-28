import { Component, viewChild } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormsModule, NgForm } from '@angular/forms';
import { applyServerErrors } from './server-errors';

@Component({
  imports: [FormsModule],
  template: `
    <form #form="ngForm">
      <input name="name" [(ngModel)]="name" />
      <input name="address" [(ngModel)]="address" />
    </form>
  `,
})
class TestForm {
  name = 'Acme';
  address = 'Sofia';
  readonly form = viewChild.required<NgForm>('form');
}

describe('applyServerErrors', () => {
  /**
   * Renders the test form and waits until its controls are registered.
   * @returns the form
   */
  async function renderForm(): Promise<NgForm> {
    const fixture = TestBed.createComponent(TestForm);
    await fixture.whenStable();
    return fixture.componentInstance.form();
  }

  it('should put each message on its control as the server error and mark it touched', async () => {
    const form = await renderForm();

    applyServerErrors(form, { name: 'Name is required.' });

    const control = form.controls['name'];
    expect(control.getError('server')).toBe('Name is required.');
    expect(control.touched).toBe(true);
    expect(form.invalid).toBe(true);
    expect(form.controls['address'].errors).toBeNull();
  });

  it('should return the messages that match no control', async () => {
    const form = await renderForm();

    const unmatched = applyServerErrors(form, { name: 'Bad name.', other: 'Something else.' });

    expect(unmatched).toEqual(['Something else.']);
  });

  it('should clear the server error once the value changes', async () => {
    const form = await renderForm();
    applyServerErrors(form, { name: 'Bad name.' });

    form.controls['name'].setValue('Acme Ltd');

    expect(form.controls['name'].errors).toBeNull();
  });
});
