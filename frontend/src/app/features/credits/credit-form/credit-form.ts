import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule, NgForm } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, of, Subject, switchMap } from 'rxjs';
import { parseIsoDate, toIsoDate } from '../../../shared/dates/date-math';
import { applyServerErrors } from '../../../shared/forms/server-errors';
import { hasFieldErrors } from '../../../shared/models/problem-detail';
import { NotificationService } from '../../../shared/notification/notification.service';
import { MaxDecimalsValidator } from '../../../shared/validators/max-decimals.validator';
import { NotBlankValidator } from '../../../shared/validators/not-blank.validator';
import { PickedOptionValidator } from '../../../shared/validators/picked-option.validator';
import {
  CREDIT_CURRENCIES,
  CREDIT_LIMITS,
  CREDIT_TYPE_LABELS,
  CREDIT_TYPES,
  ClientSummary,
  Credit,
  CreditCalculationRequest,
  CreditCurrency,
  CreditRequest,
  CreditType,
  PaymentPlan as PaymentPlanModel,
} from '../credit.model';
import { CreditService } from '../credit.service';
import { PaymentPlan } from '../payment-plan/payment-plan';
import { CreditTermValidator } from '../validators/credit-term.validator';

/** Delay between the last keystroke in the client box and the search, in milliseconds. */
export const CLIENT_SEARCH_DEBOUNCE_MS = 300;

/**
 * The form's model. `client` holds the typed text until an option is picked, then the picked
 * client; the form is invalid while it is text. Dates are `Date`s, sent as `yyyy-MM-dd`.
 */
interface CreditFormModel {
  client: string | ClientSummary;
  type: CreditType | null;
  amount: number | null;
  currency: CreditCurrency | null;
  interestRate: number | null;
  startDate: Date | null;
  endDate: Date | null;
  hasCollateral: boolean;
  collateralValue: number | null;
  collateralDescription: string;
}

/**
 * Full page with the template-driven form for a new credit (`/credits/new`) or for editing one
 * (`/credits/:id/edit`, ADMIN only, which starts with the saved plan shown and Save enabled). Flow: fill in the inputs, press Calculate to see the payment
 * plan, then Save. Save is enabled only while a plan from the current inputs is shown, so every
 * input change clears the plan. Only the inputs are saved; the server recalculates the plan. On
 * a 400 the backend's field messages are shown on the matching controls.
 */
@Component({
  imports: [
    FormsModule,
    MatAutocompleteModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    MaxDecimalsValidator,
    NotBlankValidator,
    PaymentPlan,
    PickedOptionValidator,
    CreditTermValidator,
    RouterLink,
  ],
  selector: 'app-credit-form',
  styleUrl: './credit-form.scss',
  templateUrl: './credit-form.html',
})
export class CreditForm implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly creditService = inject(CreditService);
  private readonly notification = inject(NotificationService);

  /** Typed client text, debounced before it is searched. */
  private readonly clientSearch = new Subject<string>();

  /** Limits used by the validators, mirroring the backend. */
  protected readonly limits = CREDIT_LIMITS;

  /** Credit types offered in the select. */
  protected readonly types = CREDIT_TYPES;

  /** User-facing names of the credit types. */
  protected readonly typeLabels = CREDIT_TYPE_LABELS;

  /** Currencies offered in the select. */
  protected readonly currencies = CREDIT_CURRENCIES;

  /** The id of the credit being edited, or null when creating one. */
  protected creditId: number | null = null;

  /** Whether an existing credit is being edited. */
  protected isEdit = false;

  /** Values bound to the form controls with `ngModel`. */
  protected model: CreditFormModel = {
    client: '',
    type: null,
    amount: null,
    currency: 'EUR',
    interestRate: null,
    startDate: null,
    endDate: null,
    hasCollateral: false,
    collateralValue: null,
    collateralDescription: '',
  };

  /** The credit number shown in the heading while editing. */
  protected readonly creditNumber = signal('');

  /** True while the credit to edit is loading. */
  protected readonly loading = signal(false);

  /** True when the credit to edit does not exist or may not be seen. */
  protected readonly notFound = signal(false);

  /** Clients found for the typed text. */
  protected readonly clientOptions = signal<ClientSummary[]>([]);

  /** The payment plan of the current inputs, or null before Calculate and after any change. */
  protected readonly plan = signal<PaymentPlanModel | null>(null);

  /** True while the calculation request is running. */
  protected readonly calculating = signal(false);

  /** True while the save request is running; disables the buttons. */
  protected readonly saving = signal(false);

  /**
   * Wires the debounced client search.
   */
  constructor() {
    this.clientSearch
      .pipe(
        debounceTime(CLIENT_SEARCH_DEBOUNCE_MS),
        distinctUntilChanged(),
        // a failed search is already reported by the error interceptor; keep the stream alive
        switchMap((text) =>
          text ? this.creditService.searchClients(text).pipe(catchError(() => of([]))) : of([]),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((clients) => this.clientOptions.set(clients));
  }

  /**
   * Decides between create and edit from the route; when editing, loads the credit into the form.
   */
  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam === null) {
      return;
    }
    this.isEdit = true;
    const id = Number(idParam);
    if (!Number.isInteger(id) || id <= 0) {
      this.notFound.set(true);
      return;
    }
    this.creditId = id;
    this.loading.set(true);
    this.creditService.get(id).subscribe({
      next: (credit) => {
        this.fill(credit);
        this.loading.set(false);
      },
      error: () => {
        // the interceptor already showed the message; show the empty state here
        this.loading.set(false);
        this.notFound.set(true);
      },
    });
  }

  /**
   * Text shown in the client box and the options for a client.
   * @param client the picked client, or the typed text
   * @returns "name (identifier)" for a client, the text itself otherwise
   */
  protected displayClient(client: string | ClientSummary | null): string {
    if (client === null) {
      return '';
    }
    return typeof client === 'string' ? client : `${client.name} (${client.identifier})`;
  }

  /**
   * Reacts to typing in the client box: clears the plan and searches clients for the text.
   * Picking an option passes a client, which needs no search.
   * @param value the typed text or the picked client
   */
  protected onClientChange(value: string | ClientSummary): void {
    this.clearPlan();
    if (typeof value === 'string') {
      this.clientSearch.next(value.trim());
    }
  }

  /**
   * Hides and clears the collateral fields when the box is unchecked, and clears the plan.
   * @param checked the new state of the checkbox
   */
  protected onCollateralChange(checked: boolean): void {
    if (!checked) {
      this.model.collateralValue = null;
      this.model.collateralDescription = '';
    }
    this.clearPlan();
  }

  /**
   * Removes the shown plan, which disables Save until the next successful Calculate.
   */
  protected clearPlan(): void {
    this.plan.set(null);
  }

  /**
   * Tells whether the inputs of the plan (amount, currency, rate and both dates) are valid.
   * @param form the template-driven form
   * @returns true if Calculate may be pressed
   */
  protected canCalculate(form: NgForm): boolean {
    return ['amount', 'currency', 'interestRate', 'startDate', 'endDate'].every(
      (name) => form.controls[name]?.valid === true,
    );
  }

  /**
   * Asks the backend for the payment plan of the current inputs and shows it.
   * @param form the template-driven form
   */
  protected calculate(form: NgForm): void {
    if (!this.canCalculate(form) || this.calculating() || this.saving()) {
      return;
    }
    this.calculating.set(true);
    this.creditService.calculate(this.toCalculation()).subscribe({
      next: (plan) => {
        this.calculating.set(false);
        this.plan.set(plan);
      },
      error: (error: unknown) => {
        this.calculating.set(false);
        this.showFieldErrors(form, error);
      },
    });
  }

  /**
   * Saves the credit if the form is valid and a plan is shown, then opens its detail page.
   * @param form the template-driven form
   */
  protected save(form: NgForm): void {
    if (form.invalid || this.plan() === null || this.saving() || this.calculating()) {
      return;
    }
    this.saving.set(true);
    const request = this.toRequest();
    const call =
      this.creditId === null
        ? this.creditService.create(request)
        : this.creditService.update(this.creditId, request);
    call.subscribe({
      next: (saved) => {
        this.notification.success(
          `Credit ${saved.creditNumber} was ${this.creditId === null ? 'created' : 'updated'}.`,
        );
        void this.router.navigate(['/credits', saved.id]);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.showFieldErrors(form, error);
      },
    });
  }

  /**
   * Puts a loaded credit into the form model and shows its saved plan, which counts as a valid
   * calculation for the unchanged inputs. Prefilling does not clear it, because only edits by the
   * user raise `ngModelChange`.
   * @param credit the credit to edit
   */
  private fill(credit: Credit): void {
    this.creditNumber.set(credit.creditNumber);
    this.plan.set(credit.plan);
    this.model = {
      client: credit.client,
      type: credit.type,
      amount: credit.amount,
      currency: credit.currency,
      interestRate: credit.interestRate,
      startDate: parseIsoDate(credit.startDate),
      endDate: parseIsoDate(credit.endDate),
      hasCollateral: credit.hasCollateral,
      collateralValue: credit.collateralValue,
      collateralDescription: credit.collateralDescription ?? '',
    };
  }

  /**
   * Shows the field messages of a 400 on the matching controls; other errors are left to the
   * HTTP error interceptor.
   * @param form the template-driven form
   * @param error whatever the request failed with
   */
  private showFieldErrors(form: NgForm, error: unknown): void {
    if (error instanceof HttpErrorResponse && error.status === 400 && hasFieldErrors(error.error)) {
      applyServerErrors(form, error.error.errors);
    }
  }

  /**
   * Builds the plan inputs; dates are sent as `yyyy-MM-dd` from their local parts. Only call it
   * when the plan controls are valid.
   * @returns the request for the calculation
   */
  private toCalculation(): CreditCalculationRequest {
    return {
      amount: this.model.amount!,
      currency: this.model.currency!,
      interestRate: this.model.interestRate!,
      startDate: toIsoDate(this.model.startDate!),
      endDate: toIsoDate(this.model.endDate!),
    };
  }

  /**
   * Builds the save request from the inputs only (never the plan); without collateral both
   * collateral fields are null. Only call it when the form is valid.
   * @returns the request for the backend
   */
  private toRequest(): CreditRequest {
    const { hasCollateral, collateralValue, collateralDescription } = this.model;
    return {
      ...this.toCalculation(),
      clientId: (this.model.client as ClientSummary).id,
      type: this.model.type!,
      hasCollateral,
      collateralValue: hasCollateral ? collateralValue : null,
      collateralDescription: hasCollateral ? collateralDescription : null,
    };
  }
}
