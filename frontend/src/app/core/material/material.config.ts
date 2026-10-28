import { Provider } from '@angular/core';
import { DateAdapter, MAT_DATE_FORMATS } from '@angular/material/core';
import { MAT_ICON_DEFAULT_OPTIONS } from '@angular/material/icon';
import { APP_DATE_FORMATS, AppDateAdapter } from './app-date-adapter';

/**
 * App-wide Angular Material defaults. `mat-icon` uses the self-hosted Material Symbols (Outlined)
 * font from the `material-symbols` package (bundled via `angular.json` styles), and datepickers
 * read and show dates as `dd.MM.yyyy`.
 * @returns the providers to add to `appConfig`
 */
export function provideMaterialDefaults(): Provider[] {
  return [
    { provide: MAT_ICON_DEFAULT_OPTIONS, useValue: { fontSet: 'material-symbols-outlined' } },
    { provide: DateAdapter, useClass: AppDateAdapter },
    { provide: MAT_DATE_FORMATS, useValue: APP_DATE_FORMATS },
  ];
}
