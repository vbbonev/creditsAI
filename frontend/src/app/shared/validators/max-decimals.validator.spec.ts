import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormsModule, NgForm } from '@angular/forms';
import { MaxDecimalsValidator } from './max-decimals.validator';

@Component({
  imports: [FormsModule, MaxDecimalsValidator],
  template: `<form #form="ngForm">
    <input type="number" name="amount" [(ngModel)]="value" [appMaxDecimals]="2" />
  </form>`,
})
class Host {
  value: number | null = null;
}

describe('MaxDecimalsValidator', () => {
  /**
   * Renders the host with a value and returns the control's errors.
   * @param value the number to put into the input
   * @returns the errors of the `amount` control
   */
  async function errorsFor(value: number | null) {
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.value = value;
    await fixture.whenStable();
    const form = fixture.debugElement.children[0].injector.get(NgForm);
    return form.controls['amount'].errors;
  }

  it.each([null, 5, 5.1, 5.25, 0.01])('should accept %j', async (value) => {
    expect(await errorsFor(value)).toBeNull();
  });

  it.each([5.123, 0.001, 1e-7])('should reject %j', async (value) => {
    expect(await errorsFor(value)).toEqual({ maxDecimals: { max: 2 } });
  });
});
