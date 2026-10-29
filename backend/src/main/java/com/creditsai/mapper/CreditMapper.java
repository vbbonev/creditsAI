package com.creditsai.mapper;

import com.creditsai.dto.ClientSummary;
import com.creditsai.dto.CreditRequest;
import com.creditsai.dto.CreditResponse;
import com.creditsai.dto.CreditSummaryResponse;
import com.creditsai.dto.PaymentPlanResponse;
import com.creditsai.dto.PaymentRowResponse;
import com.creditsai.model.Client;
import com.creditsai.model.Credit;
import com.creditsai.model.CreditPayment;
import com.creditsai.service.impl.PaymentPlan;
import java.math.BigDecimal;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper pattern (MapStruct) between {@link Credit} and its DTOs. Only the plain input fields
 * have setters on the entity, so only they are copied here. The client, the collateral and the
 * payment plan (ignored here) are set through the entity's methods (they carry rules), and the server-set fields
 * need no {@code ignore} because they have no setters.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CreditMapper {

    /**
     * Converts an entity to its detail response, including the full payment plan.
     *
     * @param credit the entity
     * @return the response
     */
    @Mapping(target = "plan", expression = "java(toPlanResponse(credit))")
    CreditResponse toResponse(Credit credit);

    /**
     * Converts an entity to its list item, without the payment rows.
     *
     * @param credit the entity
     * @return the list item
     */
    CreditSummaryResponse toSummary(Credit credit);

    /**
     * Converts a client to the summary shown inside a credit.
     *
     * @param client the entity
     * @return the summary
     */
    ClientSummary toClientSummary(Client client);

    /**
     * Creates a new entity from the plain input fields of a request.
     *
     * @param request the validated request
     * @return a new, unsaved credit without client, collateral and plan
     */
    @Mapping(target = "payments", ignore = true)
    @Mapping(target = "attachments", ignore = true)
    Credit toEntity(CreditRequest request);

    /**
     * Copies the plain input fields of a request onto an existing entity.
     *
     * @param request the validated request
     * @param credit the entity to change
     */
    @Mapping(target = "payments", ignore = true)
    @Mapping(target = "attachments", ignore = true)
    void updateEntity(CreditRequest request, @MappingTarget Credit credit);

    /**
     * Converts a calculated plan to its response.
     *
     * @param plan the calculated plan
     * @return the response
     */
    PaymentPlanResponse toPlanResponse(PaymentPlan plan);

    /**
     * Converts one calculated row to its response.
     *
     * @param row the calculated row
     * @return the response
     */
    PaymentRowResponse toRowResponse(PaymentPlan.Row row);

    /**
     * Converts a calculated row to a plan row entity.
     *
     * @param row the calculated row
     * @return a new plan row, not yet linked to a credit
     */
    default CreditPayment toPayment(PaymentPlan.Row row) {
        return CreditPayment.builder()
                .paymentNumber(row.number())
                .paymentDate(row.date())
                .payment(row.payment())
                .principal(row.principal())
                .interest(row.interest())
                .remainingBalance(row.remainingBalance())
                .build();
    }

    /**
     * Builds the stored payment plan of a credit, summing the totals from its rows.
     *
     * @param credit the entity
     * @return the plan response
     */
    default PaymentPlanResponse toPlanResponse(Credit credit) {
        List<PaymentRowResponse> rows = credit.getPayments().stream()
                .map(p -> new PaymentRowResponse(p.getPaymentNumber(), p.getPaymentDate(), p.getPayment(),
                        p.getPrincipal(), p.getInterest(), p.getRemainingBalance()))
                .toList();
        BigDecimal totalInterest = rows.stream().map(PaymentRowResponse::interest)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        BigDecimal totalPaid = rows.stream().map(PaymentRowResponse::payment)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        return new PaymentPlanResponse(credit.getTermMonths(), credit.getMonthlyPayment(), totalInterest, totalPaid,
                rows);
    }
}
