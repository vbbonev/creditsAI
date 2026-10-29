import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ConfirmDialog } from '../../../shared/confirm-dialog/confirm-dialog';
import { NotificationService } from '../../../shared/notification/notification.service';
import { Credit } from '../credit.model';
import { AttachmentService } from '../../../shared/attachments/attachment.service';
import { CreditService } from '../credit.service';
import { CreditDetail } from './credit-detail';

describe('CreditDetail', () => {
  let fixture: ComponentFixture<CreditDetail>;
  let el: HTMLElement;
  let attachmentService: { list: ReturnType<typeof vi.fn> };
  let creditService: {
    get: ReturnType<typeof vi.fn>;
    changeStatus: ReturnType<typeof vi.fn>;
    archive: ReturnType<typeof vi.fn>;
    restore: ReturnType<typeof vi.fn>;
  };
  let dialog: { open: ReturnType<typeof vi.fn> };
  let notification: { success: ReturnType<typeof vi.fn> };
  let navigate: ReturnType<typeof vi.spyOn>;

  const active: Credit = {
    id: 7,
    creditNumber: 'CR-2026-000007',
    client: { id: 1, name: 'Acme', identifier: '123' },
    type: 'MORTGAGE',
    amount: 10000,
    currency: 'EUR',
    interestRate: 5.25,
    startDate: '2026-01-05',
    endDate: '2026-03-05',
    hasCollateral: true,
    collateralValue: 20000,
    collateralDescription: 'A flat in Sofia',
    status: 'ACTIVE',
    archived: false,
    archivedAt: null,
    createdAt: '2026-10-27T10:00:00Z',
    updatedAt: '2026-10-27T11:00:00Z',
    plan: {
      termMonths: 2,
      monthlyPayment: 5022,
      totalInterest: 44,
      totalPaid: 10044,
      rows: [
        {
          number: 1,
          date: '2026-02-05',
          payment: 5022,
          principal: 4978,
          interest: 44,
          remainingBalance: 5022,
        },
        {
          number: 2,
          date: '2026-03-05',
          payment: 5022,
          principal: 5022,
          interest: 0,
          remainingBalance: 0,
        },
      ],
    },
  };
  const inactive: Credit = { ...active, status: 'INACTIVE' };
  const archived: Credit = { ...inactive, archived: true, archivedAt: '2026-10-27T12:00:00Z' };

  /**
   * Renders the page for the given route id, backend answer and role.
   * @param options route id, what `get` returns, and whether the user is an ADMIN
   */
  async function render(options: {
    id?: string;
    credit?: Credit;
    error?: HttpErrorResponse;
    admin?: boolean;
  }): Promise<void> {
    attachmentService = { list: vi.fn().mockReturnValue(of([])) };
    creditService = {
      get: vi
        .fn()
        .mockReturnValue(
          options.error ? throwError(() => options.error) : of(options.credit ?? active),
        ),
      changeStatus: vi.fn(),
      archive: vi.fn(),
      restore: vi.fn(),
    };
    dialog = { open: vi.fn() };
    notification = { success: vi.fn() };
    TestBed.configureTestingModule({
      imports: [CreditDetail],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: options.id ?? '7' }) } },
        },
        { provide: CreditService, useValue: creditService },
        { provide: AttachmentService, useValue: attachmentService },
        { provide: MatDialog, useValue: dialog },
        { provide: NotificationService, useValue: notification },
        { provide: AuthService, useValue: { isAdmin: () => options.admin ?? false } },
      ],
    });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(CreditDetail);
    await fixture.whenStable();
    el = fixture.nativeElement as HTMLElement;
  }

  /**
   * Finds a button or link by its visible label.
   * @param label the text (without the icon)
   * @returns the element, or undefined if not shown
   */
  function action(label: string): HTMLElement | undefined {
    return Array.from(
      el.querySelectorAll<HTMLElement>('mat-card-actions a, mat-card-actions button'),
    ).find((b) => b.textContent!.includes(label));
  }

  it('should load the credit from the route id and show its fields', async () => {
    await render({});
    expect(creditService.get).toHaveBeenCalledWith(7);
    expect(el.querySelector('mat-card-title')?.textContent).toContain('CR-2026-000007');
    expect(el.querySelector('.client')?.textContent).toContain('Acme (123)');
    expect(el.querySelector('.type')?.textContent).toContain('Mortgage');
    expect(el.querySelector('.status')?.textContent).toContain('Active');
    expect(el.querySelector('.amount')?.textContent).toContain('€10,000.00');
    expect(el.querySelector('.rate')?.textContent).toContain('5.25%');
    expect(el.querySelector('.start')?.textContent).toContain('05.01.2026');
    expect(el.querySelector('.end')?.textContent).toContain('05.03.2026');
  });

  it('should show the Files section of the loaded credit, for every credit status', async () => {
    await render({});
    expect(attachmentService.list).toHaveBeenCalledWith('credits', 7);
    expect(el.querySelector('app-attachments')).not.toBeNull();
  });

  it('should show the collateral value and description', async () => {
    await render({});
    const text = el.querySelector('.collateral')!.textContent!;
    expect(text).toContain('€20,000.00');
    expect(text).toContain('A flat in Sofia');
  });

  it('should show "None" when there is no collateral', async () => {
    await render({
      credit: {
        ...active,
        hasCollateral: false,
        collateralValue: null,
        collateralDescription: null,
      },
    });
    expect(el.querySelector('.collateral')?.textContent?.trim()).toBe('None');
  });

  it('should show the payment plan', async () => {
    await render({});
    expect(el.querySelector('app-payment-plan')).not.toBeNull();
    expect(el.querySelectorAll('app-payment-plan tr.mat-mdc-row')).toHaveLength(2);
  });

  it('should offer a link back to the list', async () => {
    await render({});
    expect(el.querySelector('a[href="/credits"]')?.textContent).toContain('Back to list');
  });

  it('should show no actions to a plain USER', async () => {
    await render({ credit: inactive });
    for (const label of ['Edit', 'Activate', 'Deactivate', 'Archive', 'Restore']) {
      expect(action(label)).toBeUndefined();
    }
  });

  it('should show Edit and Deactivate, but no Archive, to an ADMIN for an ACTIVE credit', async () => {
    await render({ admin: true });
    expect(action('Edit')?.getAttribute('href')).toBe('/credits/7/edit');
    expect(action('Deactivate')).toBeDefined();
    expect(action('Archive')).toBeUndefined();
    expect(action('Restore')).toBeUndefined();
  });

  it('should offer Archive and Activate to an ADMIN for an INACTIVE credit', async () => {
    await render({ admin: true, credit: inactive });
    expect(action('Activate')).toBeDefined();
    expect(action('Archive')).toBeDefined();
  });

  it('should mark an archived credit and offer only Restore to an ADMIN', async () => {
    await render({ admin: true, credit: archived });
    expect(el.querySelector('mat-card-title')?.textContent).toContain('Archived');
    expect(action('Restore')).toBeDefined();
    expect(action('Archive')).toBeUndefined();
    expect(action('Edit')).toBeUndefined();
    expect(action('Activate')).toBeUndefined();
  });

  it('should show "Credit not found" when the backend answers 404', async () => {
    await render({ error: new HttpErrorResponse({ status: 404 }) });
    expect(el.querySelector('mat-card-title')?.textContent).toContain('Credit not found');
  });

  it('should show "Credit not found" for an id that is not a number, without calling the backend', async () => {
    await render({ id: 'abc' });
    expect(creditService.get).not.toHaveBeenCalled();
    expect(el.querySelector('mat-card-title')?.textContent).toContain('Credit not found');
  });

  describe('as ADMIN', () => {
    it('should deactivate an active credit and update the page in place', async () => {
      await render({ admin: true });
      creditService.changeStatus.mockReturnValue(of(inactive));

      action('Deactivate')!.click();
      await fixture.whenStable();

      expect(creditService.changeStatus).toHaveBeenCalledWith(7, 'INACTIVE');
      expect(notification.success).toHaveBeenCalledWith('Credit CR-2026-000007 is now inactive.');
      expect(el.querySelector('.status')?.textContent).toContain('Inactive');
      expect(action('Archive')).toBeDefined();
      expect(navigate).not.toHaveBeenCalled();
    });

    it('should activate an inactive credit', async () => {
      await render({ admin: true, credit: inactive });
      creditService.changeStatus.mockReturnValue(of(active));

      action('Activate')!.click();

      expect(creditService.changeStatus).toHaveBeenCalledWith(7, 'ACTIVE');
    });

    it('should ask for confirmation before archiving', async () => {
      await render({ admin: true, credit: inactive });
      dialog.open.mockReturnValue({ afterClosed: () => of(undefined) });

      action('Archive')!.click();

      expect(dialog.open).toHaveBeenCalledWith(
        ConfirmDialog,
        expect.objectContaining({
          data: expect.objectContaining({ title: 'Archive credit?', confirmLabel: 'Archive' }),
        }),
      );
      expect(creditService.archive).not.toHaveBeenCalled();
    });

    it('should archive after confirmation and go back to the list with a message', async () => {
      await render({ admin: true, credit: inactive });
      dialog.open.mockReturnValue({ afterClosed: () => of(true) });
      creditService.archive.mockReturnValue(of(undefined));

      action('Archive')!.click();

      expect(creditService.archive).toHaveBeenCalledWith(7);
      expect(notification.success).toHaveBeenCalledWith('Credit CR-2026-000007 was archived.');
      expect(navigate).toHaveBeenCalledWith(['/credits']);
    });

    it('should stay on the page when archiving is refused (e.g. 409)', async () => {
      await render({ admin: true, credit: inactive });
      dialog.open.mockReturnValue({ afterClosed: () => of(true) });
      creditService.archive.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 409 })),
      );

      action('Archive')!.click();
      await fixture.whenStable();

      expect(navigate).not.toHaveBeenCalled();
      expect(notification.success).not.toHaveBeenCalled();
      expect(action('Archive')!.hasAttribute('disabled')).toBe(false);
    });

    it('should restore and go back to the list with a message', async () => {
      await render({ admin: true, credit: archived });
      creditService.restore.mockReturnValue(of(inactive));

      action('Restore')!.click();

      expect(creditService.restore).toHaveBeenCalledWith(7);
      expect(notification.success).toHaveBeenCalledWith('Credit CR-2026-000007 was restored.');
      expect(navigate).toHaveBeenCalledWith(['/credits']);
    });
  });
});
