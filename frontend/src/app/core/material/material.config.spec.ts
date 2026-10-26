import { TestBed } from '@angular/core/testing';
import { MAT_ICON_DEFAULT_OPTIONS } from '@angular/material/icon';
import { provideMaterialDefaults } from './material.config';

describe('provideMaterialDefaults', () => {
  it('should make mat-icon use the Material Symbols Outlined font', () => {
    TestBed.configureTestingModule({ providers: provideMaterialDefaults() });
    expect(TestBed.inject(MAT_ICON_DEFAULT_OPTIONS).fontSet).toBe('material-symbols-outlined');
  });
});
