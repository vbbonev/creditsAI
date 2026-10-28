import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  it('should show a success message in a snackbar that closes by itself', () => {
    const snackBar = { open: vi.fn() };
    TestBed.configureTestingModule({ providers: [{ provide: MatSnackBar, useValue: snackBar }] });

    TestBed.inject(NotificationService).success('Client "Acme" was created.');

    expect(snackBar.open).toHaveBeenCalledWith('Client "Acme" was created.', 'Close', {
      duration: 4000,
    });
  });
});
