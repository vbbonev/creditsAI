import { AbstractControl } from '@angular/forms';
import { NotBlankValidator } from './not-blank.validator';

describe('NotBlankValidator', () => {
  const validator = new NotBlankValidator();

  /**
   * Builds the minimal control the validator reads.
   * @param value the control's value
   * @returns a stand-in for an `NgModel` control
   */
  function control(value: unknown): AbstractControl {
    return { value } as AbstractControl;
  }

  it.each([' ', '   ', '\t\n'])('should reject whitespace-only text %j', (value) => {
    expect(validator.validate(control(value))).toEqual({ notBlank: true });
  });

  it.each(['a', ' a ', 'Иван'])('should accept text with content %j', (value) => {
    expect(validator.validate(control(value))).toBeNull();
  });

  it.each([
    ['empty text', ''],
    ['null', null],
    ['undefined', undefined],
  ])('should leave an empty value (%s) to required', (_label, value) => {
    expect(validator.validate(control(value))).toBeNull();
  });
});
