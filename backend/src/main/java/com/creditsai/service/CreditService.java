package com.creditsai.service;

import com.creditsai.dto.CreditCalculationRequest;
import com.creditsai.dto.CreditRequest;
import com.creditsai.dto.CreditResponse;
import com.creditsai.dto.CreditSummaryResponse;
import com.creditsai.dto.PaymentPlanResponse;
import com.creditsai.exception.BusinessRuleException;
import com.creditsai.exception.InvalidRequestException;
import com.creditsai.exception.ResourceNotFoundException;
import com.creditsai.model.CreditStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service layer / facade for credit use cases. It knows nothing about security: callers decide
 * what a user may see.
 */
public interface CreditService {

    /**
     * Calculates a payment plan without saving anything.
     *
     * @param request the validated inputs
     * @return the plan
     * @throws InvalidRequestException if the end date is not a whole number of months after the start date
     */
    PaymentPlanResponse calculate(CreditCalculationRequest request);

    /**
     * Registers a new active credit. The payment plan is always calculated from the inputs.
     *
     * @param request the validated credit data
     * @return the created credit with its plan
     * @throws ResourceNotFoundException if the client does not exist or is archived
     * @throws InvalidRequestException if the end date is not a whole number of months after the start date
     */
    CreditResponse create(CreditRequest request);

    /**
     * Lists credits whose number, client name or client identifier contains the search text
     * (case-insensitive).
     *
     * @param search the text to find; {@code null} or blank matches every credit
     * @param status keep only credits with this status; {@code null} for all
     * @param includeArchived {@code true} to list archived credits together with active ones,
     *        {@code false} for active ones only
     * @param pageable page, size and sort
     * @return the requested page, without payment rows
     */
    Page<CreditSummaryResponse> list(String search, CreditStatus status, boolean includeArchived, Pageable pageable);

    /**
     * Returns one credit with its plan.
     *
     * @param id the credit id
     * @param includeArchived whether an archived credit, or a credit of an archived client, may be returned
     * @return the credit
     * @throws ResourceNotFoundException if it does not exist, or is hidden and not allowed
     */
    CreditResponse get(Long id, boolean includeArchived);

    /**
     * Replaces all editable fields of a credit, archived or not, and recalculates its plan.
     *
     * @param id the credit id
     * @param request the validated credit data
     * @return the updated credit
     * @throws ResourceNotFoundException if the credit or the client does not exist
     * @throws BusinessRuleException if the client is archived
     * @throws InvalidRequestException if the end date is not a whole number of months after the start date
     */
    CreditResponse update(Long id, CreditRequest request);

    /**
     * Changes the status of a credit.
     *
     * @param id the credit id
     * @param status the new status
     * @return the updated credit
     * @throws ResourceNotFoundException if the credit does not exist
     * @throws BusinessRuleException if activating an archived credit or a credit of an archived client
     */
    CreditResponse changeStatus(Long id, CreditStatus status);

    /**
     * Archives a credit (soft delete).
     *
     * @param id the credit id
     * @throws ResourceNotFoundException if the credit does not exist
     * @throws BusinessRuleException if the credit is active or already archived
     */
    void archive(Long id);

    /**
     * Restores an archived credit; it stays inactive.
     *
     * @param id the credit id
     * @return the restored credit
     * @throws ResourceNotFoundException if the credit does not exist
     * @throws BusinessRuleException if the credit is not archived
     */
    CreditResponse restore(Long id);
}
