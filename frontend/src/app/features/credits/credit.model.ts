import { SortDirection } from '../../shared/models/page';

/** Kind of credit, as in the backend's `CreditType`. */
export type CreditType = 'CONSUMER' | 'MORTGAGE' | 'AUTO' | 'BUSINESS';

/** Currency of a credit, as in the backend's `CreditCurrency`. */
export type CreditCurrency = 'EUR' | 'USD' | 'CHF';

/** Status of a credit, as in the backend's `CreditStatus`. */
export type CreditStatus = 'ACTIVE' | 'INACTIVE';

/** Credit types in the order shown in the form. */
export const CREDIT_TYPES: readonly CreditType[] = ['CONSUMER', 'MORTGAGE', 'AUTO', 'BUSINESS'];

/** Currencies in the order shown in the form. */
export const CREDIT_CURRENCIES: readonly CreditCurrency[] = ['EUR', 'USD', 'CHF'];

/** User-facing names of the credit types. */
export const CREDIT_TYPE_LABELS: Record<CreditType, string> = {
  CONSUMER: 'Consumer',
  MORTGAGE: 'Mortgage',
  AUTO: 'Auto',
  BUSINESS: 'Business',
};

/** User-facing names of the credit statuses. */
export const CREDIT_STATUS_LABELS: Record<CreditStatus, string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
};

/** The few client fields shown inside a credit (`ClientSummary`). */
export interface ClientSummary {
  id: number;
  name: string;
  identifier: string;
}

/** One instalment of a payment plan (`PaymentRowResponse`). Dates are `yyyy-MM-dd` strings. */
export interface PaymentRow {
  number: number;
  date: string;
  payment: number;
  principal: number;
  interest: number;
  remainingBalance: number;
}

/** A payment plan as returned by the API (`PaymentPlanResponse`). */
export interface PaymentPlan {
  termMonths: number;
  monthlyPayment: number;
  totalInterest: number;
  totalPaid: number;
  rows: PaymentRow[];
}

/** A credit in the list, without the payment rows (`CreditSummaryResponse`). */
export interface CreditSummary {
  id: number;
  creditNumber: string;
  client: ClientSummary;
  type: CreditType;
  amount: number;
  currency: CreditCurrency;
  interestRate: number;
  startDate: string;
  endDate: string;
  termMonths: number;
  monthlyPayment: number;
  status: CreditStatus;
  archived: boolean;
  createdAt: string;
}

/** A credit with its payment plan (`CreditResponse`). Instants are ISO-8601 strings. */
export interface Credit {
  id: number;
  creditNumber: string;
  client: ClientSummary;
  type: CreditType;
  amount: number;
  currency: CreditCurrency;
  interestRate: number;
  startDate: string;
  endDate: string;
  hasCollateral: boolean;
  collateralValue: number | null;
  collateralDescription: string | null;
  status: CreditStatus;
  archived: boolean;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
  plan: PaymentPlan;
}

/** Inputs that determine a payment plan; body of `POST /api/credits/calculate`. Dates are `yyyy-MM-dd`. */
export interface CreditCalculationRequest {
  amount: number;
  currency: CreditCurrency;
  interestRate: number;
  startDate: string;
  endDate: string;
}

/**
 * Body of `POST /api/credits` and `PUT /api/credits/{id}`; PUT replaces every field. The plan is
 * never sent, the server calculates it. Without collateral, both collateral fields must be null.
 */
export interface CreditRequest extends CreditCalculationRequest {
  clientId: number;
  type: CreditType;
  hasCollateral: boolean;
  collateralValue: number | null;
  collateralDescription: string | null;
}

/** Fields the backend allows sorting the credit list by. */
export type CreditSortField = 'creditNumber' | 'startDate' | 'endDate' | 'amount' | 'createdAt';

/** What the credit list asks the backend for. */
export interface CreditQuery {
  /** Text to find in the credit number, client name or client identifier; blank lists all. */
  search: string;
  /** Keep only credits with this status; null lists both. */
  status: CreditStatus | null;
  /** Whether archived credits are listed together with active ones (ADMIN only). */
  includeArchived: boolean;
  /** Zero-based page index. */
  page: number;
  /** Rows per page. */
  size: number;
  sort: CreditSortField;
  direction: SortDirection;
}

/** Input limits, mirroring the backend's `app.credits` settings and `CreditRequest` constraints. */
export const CREDIT_LIMITS = {
  /** Smallest amount with 2 decimals that is greater than zero. */
  minAmount: 0.01,
  maxAmount: 100_000_000,
  maxAmountDecimals: 2,
  minRate: 0,
  maxRate: 100,
  maxRateDecimals: 4,
  minTermMonths: 1,
  maxTermMonths: 480,
  /** Largest collateral value allowed by `@Digits(integer = 10, fraction = 2)`. */
  maxCollateralValue: 9_999_999_999.99,
  maxCollateralDescription: 500,
  minStartDate: new Date(1900, 0, 1),
  maxEndDate: new Date(2200, 11, 31),
} as const;
