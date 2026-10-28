/**
 * Builds a local date at midnight, also for years below 100 (which `new Date(y, m, d)` would
 * move into the 1900s).
 * @param year the full year
 * @param month the zero-based month
 * @param day the day of the month
 * @returns the local date
 */
function makeDate(year: number, month: number, day: number): Date {
  const date = new Date(2000, 0, 1);
  date.setFullYear(year, month, day);
  return date;
}

/**
 * Formats a local date as `yyyy-MM-dd` from its local parts. `toISOString()` is never used,
 * because it converts to UTC and can shift the day.
 * @param date the local date
 * @returns the date as the API expects it, e.g. "2026-01-05"
 */
export function toIsoDate(date: Date): string {
  const year = String(date.getFullYear()).padStart(4, '0');
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/**
 * Parses a `yyyy-MM-dd` string from the API into a local date at midnight.
 * @param value the date string, e.g. "2026-01-05"
 * @returns the local date
 */
export function parseIsoDate(value: string): Date {
  const [year, month, day] = value.split('-').map(Number);
  return makeDate(year, month - 1, day);
}

/**
 * Adds whole months to a date; a day that does not exist in the target month moves to the
 * month's last day (31 Jan + 1 month = 28/29 Feb), like `java.time.LocalDate.plusMonths`.
 * @param date the start date
 * @param months the number of months to add
 * @returns the new local date
 */
export function addMonths(date: Date, months: number): Date {
  const total = date.getMonth() + months;
  const year = date.getFullYear() + Math.floor(total / 12);
  const month = ((total % 12) + 12) % 12;
  const lastDay = makeDate(year, month + 1, 0).getDate();
  return makeDate(year, month, Math.min(date.getDate(), lastDay));
}

/**
 * Tells whether two dates fall on the same calendar day.
 * @param a the first date
 * @param b the second date
 * @returns true if year, month and day are equal
 */
export function isSameDay(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  );
}

/**
 * Counts the months from start to end when end is exactly that many months after start, with
 * month-end clamping. Mirrors the backend's `PaymentPlanCalculator.termMonths`.
 * @param start the start date
 * @param end the end date
 * @returns the number of months (at least 1), or -1 if end is not a whole number of months after start
 */
export function termMonths(start: Date, end: Date): number {
  const difference =
    (end.getFullYear() - start.getFullYear()) * 12 + (end.getMonth() - start.getMonth());
  for (const months of [difference - 1, difference, difference + 1]) {
    if (months >= 1 && isSameDay(addMonths(start, months), end)) {
      return months;
    }
  }
  return -1;
}
