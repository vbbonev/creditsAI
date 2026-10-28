import { FormControl } from '@angular/forms';
import { PickedOptionValidator } from './picked-option.validator';

describe('PickedOptionValidator', () => {
  const validator = new PickedOptionValidator();

  it('should reject typed text that was not picked', () => {
    expect(validator.validate(new FormControl('Ac'))).toEqual({ notPicked: true });
  });

  it('should accept a picked option object', () => {
    expect(validator.validate(new FormControl({ id: 1, name: 'Acme' }))).toBeNull();
  });

  it.each(['', null])('should leave empty value %j to required', (value) => {
    expect(validator.validate(new FormControl(value))).toBeNull();
  });
});
