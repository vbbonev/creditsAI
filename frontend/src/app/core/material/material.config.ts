import { Provider } from '@angular/core';
import { MAT_ICON_DEFAULT_OPTIONS } from '@angular/material/icon';

/**
 * App-wide Angular Material defaults. `mat-icon` uses the self-hosted Material Symbols (Outlined)
 * font from the `material-symbols` package (bundled via `angular.json` styles).
 * @returns the providers to add to `appConfig`
 */
export function provideMaterialDefaults(): Provider[] {
  return [
    { provide: MAT_ICON_DEFAULT_OPTIONS, useValue: { fontSet: 'material-symbols-outlined' } },
  ];
}
