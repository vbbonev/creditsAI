import { Injectable } from '@angular/core';
import { MatDateFormats, NativeDateAdapter } from '@angular/material/core';

/** Pattern of dates typed into and shown in datepicker inputs. */
export const DATE_INPUT_FORMAT = 'dd.MM.yyyy';

/** Matches a typed date such as "05.01.2026" or "5.1.2026". */
const TYPED_DATE = /^(\d{1,2})\.(\d{1,2})\.(\d{4})$/;

/**
 * Date formats for Angular Material: datepicker inputs use `dd.MM.yyyy`, the calendar keeps the
 * native labels.
 */
export const APP_DATE_FORMATS: MatDateFormats = {
  parse: { dateInput: DATE_INPUT_FORMAT, timeInput: { hour: '2-digit', minute: '2-digit' } },
  display: {
    dateInput: DATE_INPUT_FORMAT,
    monthYearLabel: { year: 'numeric', month: 'short' },
    dateA11yLabel: { year: 'numeric', month: 'long', day: 'numeric' },
    monthYearA11yLabel: { year: 'numeric', month: 'long' },
    timeInput: { hour: '2-digit', minute: '2-digit' },
    timeOptionLabel: { hour: '2-digit', minute: '2-digit' },
  },
};

/**
 * A `NativeDateAdapter` that parses and formats `dd.MM.yyyy` (an Adapter over the native `Date`)
 * and starts the week on Monday. No extra date library is needed.
 */
@Injectable()
export class AppDateAdapter extends NativeDateAdapter {
  /**
   * Parses text typed by the user.
   * @param value the typed text (or a date value, which is handled natively)
   * @param parseFormat the format from `MAT_DATE_FORMATS`
   * @returns the date, null for empty text, or an invalid date if the text is not a real `dd.MM.yyyy` date
   */
  override parse(value: unknown, parseFormat?: unknown): Date | null {
    if (typeof value !== 'string') {
      return super.parse(value, parseFormat);
    }
    const text = value.trim();
    if (!text) {
      return null;
    }
    const match = TYPED_DATE.exec(text);
    if (!match) {
      return this.invalid();
    }
    const [day, month, year] = [Number(match[1]), Number(match[2]), Number(match[3])];
    if (month < 1 || month > 12 || day < 1) {
      return this.invalid();
    }
    const date = this.createDate(year, month - 1, 1);
    if (day > this.getNumDaysInMonth(date)) {
      return this.invalid();
    }
    return this.createDate(year, month - 1, day);
  }

  /**
   * Formats a date for display.
   * @param date the date to show
   * @param displayFormat the format from `MAT_DATE_FORMATS`
   * @returns "dd.MM.yyyy" for the input format, otherwise the native label
   * @throws Error if the date is invalid
   */
  override format(date: Date, displayFormat: object | string): string {
    if (displayFormat !== DATE_INPUT_FORMAT) {
      return super.format(date, displayFormat as object);
    }
    if (!this.isValid(date)) {
      throw Error('AppDateAdapter: Cannot format invalid date.');
    }
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = String(date.getFullYear()).padStart(4, '0');
    return `${day}.${month}.${year}`;
  }

  /**
   * Returns the first day of the week.
   * @returns 1 (Monday)
   */
  override getFirstDayOfWeek(): number {
    return 1;
  }
}
