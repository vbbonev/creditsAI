import { TestBed } from '@angular/core/testing';
import { DateAdapter, MAT_DATE_FORMATS } from '@angular/material/core';
import { APP_DATE_FORMATS, AppDateAdapter, DATE_INPUT_FORMAT } from './app-date-adapter';
import { provideMaterialDefaults } from './material.config';

describe('AppDateAdapter', () => {
  let adapter: AppDateAdapter;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideMaterialDefaults() });
    adapter = TestBed.inject(DateAdapter) as AppDateAdapter;
  });

  it('should be the app-wide DateAdapter with dd.MM.yyyy formats', () => {
    expect(adapter).toBeInstanceOf(AppDateAdapter);
    expect(TestBed.inject(MAT_DATE_FORMATS)).toBe(APP_DATE_FORMATS);
    expect(APP_DATE_FORMATS.parse.dateInput).toBe(DATE_INPUT_FORMAT);
    expect(APP_DATE_FORMATS.display.dateInput).toBe(DATE_INPUT_FORMAT);
  });

  describe('parse', () => {
    it('should read dd.MM.yyyy', () => {
      const date = adapter.parse('05.01.2026')!;
      expect([date.getFullYear(), date.getMonth(), date.getDate()]).toEqual([2026, 0, 5]);
    });

    it('should accept single-digit day and month and surrounding spaces', () => {
      const date = adapter.parse(' 5.1.2026 ')!;
      expect([date.getFullYear(), date.getMonth(), date.getDate()]).toEqual([2026, 0, 5]);
    });

    it('should return null for empty text', () => {
      expect(adapter.parse('')).toBeNull();
      expect(adapter.parse('   ')).toBeNull();
    });

    it.each(['2026-01-05', '31.02.2026', '00.01.2026', '15.13.2026', '1.1.26', 'abc'])(
      'should return an invalid date for %s',
      (text) => {
        expect(adapter.isValid(adapter.parse(text)!)).toBe(false);
      },
    );

    it('should accept 29 February only in a leap year', () => {
      expect(adapter.isValid(adapter.parse('29.02.2028')!)).toBe(true);
      expect(adapter.isValid(adapter.parse('29.02.2027')!)).toBe(false);
    });

    it('should leave Date values to the native adapter', () => {
      const date = new Date(2026, 0, 5);
      expect(adapter.parse(date)).toEqual(date);
    });
  });

  describe('format', () => {
    it('should write dd.MM.yyyy with zero padding', () => {
      expect(adapter.format(new Date(2026, 0, 5), DATE_INPUT_FORMAT)).toBe('05.01.2026');
      expect(adapter.format(new Date(2026, 11, 31), DATE_INPUT_FORMAT)).toBe('31.12.2026');
    });

    it('should throw for an invalid date', () => {
      expect(() => adapter.format(adapter.invalid(), DATE_INPUT_FORMAT)).toThrow();
    });

    it('should use the native label for other formats', () => {
      expect(adapter.format(new Date(2026, 0, 5), { year: 'numeric' })).toContain('2026');
    });
  });

  it('should start the week on Monday', () => {
    expect(adapter.getFirstDayOfWeek()).toBe(1);
  });
});
