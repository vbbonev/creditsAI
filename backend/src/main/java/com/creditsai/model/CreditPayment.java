package com.creditsai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * One row of a {@link Credit}'s payment plan. Rows are never edited: when the plan changes, the
 * credit replaces all of them through {@link Credit#replacePayments}.
 */
@Entity
@Table(name = "credit_payments")
@Getter
@NoArgsConstructor
@ToString(exclude = "credit")
public class CreditPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_id", nullable = false, updatable = false)
    private Credit credit;

    /** The position in the plan, starting at 1; unique per credit. */
    @Column(name = "payment_number", nullable = false, updatable = false)
    private int paymentNumber;

    @Column(name = "payment_date", nullable = false, updatable = false)
    private LocalDate paymentDate;

    /** The whole instalment: principal plus interest. */
    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal payment;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal principal;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal interest;

    @Column(name = "remaining_balance", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal remainingBalance;

    /**
     * Creates a plan row (Builder pattern, as the fields are many and all required).
     *
     * @param paymentNumber the position in the plan, starting at 1
     * @param paymentDate the due date
     * @param payment the whole instalment
     * @param principal the part that repays the principal
     * @param interest the interest part
     * @param remainingBalance the principal still owed after this instalment
     */
    @Builder
    public CreditPayment(int paymentNumber, LocalDate paymentDate, BigDecimal payment, BigDecimal principal,
            BigDecimal interest, BigDecimal remainingBalance) {
        this.paymentNumber = paymentNumber;
        this.paymentDate = paymentDate;
        this.payment = payment;
        this.principal = principal;
        this.interest = interest;
        this.remainingBalance = remainingBalance;
    }

    /**
     * Links this row to its credit; called only by {@link Credit#replacePayments}.
     *
     * @param owner the credit this row belongs to
     */
    void assignTo(Credit owner) {
        this.credit = owner;
    }
}
