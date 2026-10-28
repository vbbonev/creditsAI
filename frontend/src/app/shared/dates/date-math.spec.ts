import { addMonths, isSameDay, parseIsoDate, termMonths, toIsoDate } from './date-math';

describe('toIsoDate', () => {
  it('should build yyyy-MM-dd from the local parts, with zero padding', () => {
    expect(toIsoDate(new Date(2026, 0, 5))).toBe('2026-01-05');
    expect(toIsoDate(new Date(2026, 11, 31))).toBe('2026-12-31');
  });

  it('should not shift the day for local midnight (no toISOString)', () => {
    expect(toIsoDate(new Date(2026, 2, 1, 0, 0, 0))).toBe('2026-03-01');
  });
});

describe('parseIsoDate', () => {
  it('should parse into a local date at midnight', () => {
    const date = parseIsoDate('2026-02-09');
    expect([date.getFullYear(), date.getMonth(), date.getDate(), date.getHours()]).toEqual([
      2026, 1, 9, 0,
    ]);
  });

  it('should round-trip with toIsoDate', () => {
    expect(toIsoDate(parseIsoDate('1900-01-01'))).toBe('1900-01-01');
  });
});

describe('addMonths', () => {
  it('should clamp to the end of a shorter month', () => {
    expect(toIsoDate(addMonths(new Date(2026, 0, 31), 1))).toBe('2026-02-28');
    expect(toIsoDate(addMonths(new Date(2028, 0, 31), 1))).toBe('2028-02-29');
  });

  it('should cross year boundaries', () => {
    expect(toIsoDate(addMonths(new Date(2026, 10, 15), 3))).toBe('2027-02-15');
  });
});

describe('isSameDay', () => {
  it('should ignore the time of day', () => {
    expect(isSameDay(new Date(2026, 0, 1, 8), new Date(2026, 0, 1, 20))).toBe(true);
    expect(isSameDay(new Date(2026, 0, 1), new Date(2026, 0, 2))).toBe(false);
  });
});

describe('termMonths', () => {
  it('should count whole months on the same day', () => {
    expect(termMonths(new Date(2026, 0, 15), new Date(2027, 0, 15))).toBe(12);
    expect(termMonths(new Date(2026, 0, 15), new Date(2026, 1, 15))).toBe(1);
  });

  it('should accept month-end clamping (31 Jan to 28 Feb)', () => {
    expect(termMonths(new Date(2026, 0, 31), new Date(2026, 1, 28))).toBe(1);
    expect(termMonths(new Date(2026, 0, 31), new Date(2026, 3, 30))).toBe(3);
  });

  it('should reject a different day of the month', () => {
    expect(termMonths(new Date(2026, 0, 15), new Date(2026, 1, 16))).toBe(-1);
    expect(termMonths(new Date(2026, 0, 15), new Date(2026, 0, 30))).toBe(-1);
  });

  it('should reject 29 Feb when the clamped day would be 28 Feb', () => {
    expect(termMonths(new Date(2027, 0, 31), new Date(2027, 1, 27))).toBe(-1);
  });

  it('should reject an end that is not after the start', () => {
    expect(termMonths(new Date(2026, 0, 15), new Date(2026, 0, 15))).toBe(-1);
    expect(termMonths(new Date(2026, 5, 15), new Date(2026, 0, 15))).toBe(-1);
  });
});
