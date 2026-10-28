package com.creditsai.service.impl;

import com.creditsai.exception.InvalidRequestException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculates an annuity payment plan (equal monthly instalments) with the 30/360 convention:
 * every period is 1/12 of a year, so the monthly rate is the same for all periods. A pure
 * function with no state and no Spring dependency, so it is easy to unit test.
 */
public final class PaymentPlanCalculator {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal MONTHLY_DIVISOR = BigDecimal.valueOf(1200);

    private PaymentPlanCalculator() {
    }

    /**
     * Finds the term in whole months between two dates: the {@code n >= 1} for which
     * {@code start.plusMonths(n)} equals {@code end}.
     *
     * @param start the start date
     * @param end the end date
     * @return the number of months, or {@code -1} if the end is not a whole number of months after the start
     *         (or the distance is too large to be a term)
     */
    public static int termMonths(LocalDate start, LocalDate end) {
        long months = ChronoUnit.MONTHS.between(start, end);
        if (months >= Integer.MAX_VALUE - 1) {
            return -1;
        }
        // Month-end clamping (31 Jan + 1 month = 28 Feb) makes MONTHS.between one short, so check the next month too
        for (long n = Math.max(1, months); n <= months + 1; n++) {
            try {
                if (start.plusMonths(n).equals(end)) {
                    return Math.toIntExact(n);
                }
            } catch (DateTimeException e) {
                return -1; // beyond the supported date range
            }
        }
        return -1;
    }

    /**
     * Builds the payment plan.
     *
     * @param amount the principal, greater than zero
     * @param annualRatePercent the fixed annual nominal rate in percent, e.g. 5.25 (0 allowed)
     * @param start the start date
     * @param end the end date, a whole number of months after the start
     * @return the plan; the last row brings the balance to exactly 0.00
     * @throws InvalidRequestException if the end date is not a whole number of months after the start
     */
    public static PaymentPlan calculate(BigDecimal amount, BigDecimal annualRatePercent, LocalDate start,
            LocalDate end) {
        int n = termMonths(start, end);
        if (n < 1) {
            throw new InvalidRequestException(
                    "The end date must be a whole number of months after the start date, on the same day of the month.");
        }
        BigDecimal r = annualRatePercent.divide(MONTHLY_DIVISOR, MC);
        BigDecimal installment = installment(amount, r, n);

        List<PaymentPlan.Row> rows = new ArrayList<>(n);
        BigDecimal balance = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalInterest = BigDecimal.ZERO.setScale(2);
        BigDecimal totalPaid = BigDecimal.ZERO.setScale(2);
        for (int k = 1; k <= n; k++) {
            BigDecimal interest = balance.multiply(r, MC).setScale(2, RoundingMode.HALF_UP);
            BigDecimal principal = k == n ? balance : installment.subtract(interest);
            BigDecimal payment = principal.add(interest);
            balance = balance.subtract(principal);
            rows.add(new PaymentPlan.Row(k, start.plusMonths(k), payment, principal, interest, balance));
            totalInterest = totalInterest.add(interest);
            totalPaid = totalPaid.add(payment);
        }
        return new PaymentPlan(n, installment, totalInterest, totalPaid, List.copyOf(rows));
    }

    /**
     * Computes the regular instalment {@code A = P*r / (1 - (1+r)^-n)}, or {@code P/n} when the rate is 0.
     *
     * @param principal the principal
     * @param r the monthly rate as a fraction
     * @param n the number of instalments
     * @return the instalment rounded half-up to 2 decimals
     */
    private static BigDecimal installment(BigDecimal principal, BigDecimal r, int n) {
        if (r.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
        }
        BigDecimal growth = BigDecimal.ONE.add(r).pow(n, MC);
        BigDecimal discount = BigDecimal.ONE.subtract(BigDecimal.ONE.divide(growth, MC), MC);
        return principal.multiply(r, MC).divide(discount, MC).setScale(2, RoundingMode.HALF_UP);
    }
}
