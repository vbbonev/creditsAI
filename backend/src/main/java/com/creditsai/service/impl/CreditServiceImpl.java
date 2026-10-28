package com.creditsai.service.impl;

import com.creditsai.dto.CreditCalculationRequest;
import com.creditsai.dto.CreditRequest;
import com.creditsai.dto.CreditResponse;
import com.creditsai.dto.CreditSummaryResponse;
import com.creditsai.dto.PaymentPlanResponse;
import com.creditsai.exception.ResourceNotFoundException;
import com.creditsai.mapper.CreditMapper;
import com.creditsai.model.Client;
import com.creditsai.model.Credit;
import com.creditsai.model.CreditStatus;
import com.creditsai.repository.ClientRepository;
import com.creditsai.repository.CreditRepository;
import com.creditsai.service.CreditService;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer implementation of {@link CreditService}, using constructor Dependency Injection.
 * It only orchestrates: loading, calling the calculator and entity methods, saving and mapping.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreditServiceImpl implements CreditService {

    private final CreditRepository creditRepository;
    private final ClientRepository clientRepository;
    private final CreditMapper creditMapper;

    /** {@inheritDoc} */
    @Override
    public PaymentPlanResponse calculate(CreditCalculationRequest request) {
        return creditMapper.toPlanResponse(
                PaymentPlanCalculator.calculate(request.amount(), request.interestRate(), request.startDate(),
                        request.endDate()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CreditResponse create(CreditRequest request) {
        Client client = clientRepository.findByIdAndArchivedFalse(request.clientId())
                .orElseThrow(() -> clientNotFound(request.clientId()));
        Credit credit = creditMapper.toEntity(request);
        credit.assignTo(client);
        credit.applyCollateral(request.hasCollateral(), request.collateralValue(), request.collateralDescription());
        applyPlan(credit, request);
        return creditMapper.toResponse(creditRepository.saveAndFlush(credit));
    }

    /** {@inheritDoc} */
    @Override
    public Page<CreditSummaryResponse> list(String search, CreditStatus status, boolean includeArchived,
            Pageable pageable) {
        return creditRepository.search(containsPattern(search), status != null, status, includeArchived, pageable)
                .map(creditMapper::toSummary);
    }

    /** {@inheritDoc} */
    @Override
    public CreditResponse get(Long id, boolean includeArchived) {
        Credit credit = includeArchived
                ? creditRepository.findById(id).orElseThrow(() -> notFound(id))
                : creditRepository.findByIdAndArchivedFalseAndClientArchivedFalse(id).orElseThrow(() -> notFound(id));
        return creditMapper.toResponse(credit);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The old plan rows are deleted and flushed before the new ones are added, because the
     * payment numbers are unique per credit and Hibernate inserts before it deletes.
     */
    @Override
    @Transactional
    public CreditResponse update(Long id, CreditRequest request) {
        Credit credit = findOrThrow(id);
        credit.assignTo(findClient(request.clientId()));
        creditMapper.updateEntity(request, credit);
        credit.applyCollateral(request.hasCollateral(), request.collateralValue(), request.collateralDescription());
        credit.clearPayments();
        creditRepository.flush();
        applyPlan(credit, request);
        return creditMapper.toResponse(creditRepository.saveAndFlush(credit));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CreditResponse changeStatus(Long id, CreditStatus status) {
        Credit credit = findOrThrow(id);
        credit.changeStatus(status);
        return creditMapper.toResponse(creditRepository.save(credit));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void archive(Long id) {
        Credit credit = findOrThrow(id);
        credit.archive();
        creditRepository.save(credit);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CreditResponse restore(Long id) {
        Credit credit = findOrThrow(id);
        credit.restore();
        return creditMapper.toResponse(creditRepository.save(credit));
    }

    /**
     * Calculates the plan from the request inputs and stores it on the credit.
     *
     * @param credit the credit whose plan is replaced
     * @param request the request carrying the inputs
     */
    private void applyPlan(Credit credit, CreditRequest request) {
        PaymentPlan plan = PaymentPlanCalculator.calculate(request.amount(), request.interestRate(),
                request.startDate(), request.endDate());
        credit.replacePayments(plan.termMonths(), plan.monthlyPayment(),
                plan.rows().stream().map(creditMapper::toPayment).toList());
    }

    /**
     * Loads a credit regardless of its archive state.
     *
     * @param id the credit id
     * @return the credit
     * @throws ResourceNotFoundException if it does not exist
     */
    private Credit findOrThrow(Long id) {
        return creditRepository.findById(id).orElseThrow(() -> notFound(id));
    }

    /**
     * Loads the client a credit refers to, regardless of its archive state (the credit checks that).
     *
     * @param clientId the client id
     * @return the client
     * @throws ResourceNotFoundException if it does not exist
     */
    private Client findClient(Long clientId) {
        return clientRepository.findById(clientId).orElseThrow(() -> clientNotFound(clientId));
    }

    /**
     * Builds the user-readable not-found exception for a client.
     *
     * @param clientId the missing client id
     * @return the exception to throw
     */
    private ResourceNotFoundException clientNotFound(Long clientId) {
        return new ResourceNotFoundException("Client " + clientId + " was not found.");
    }

    /**
     * Turns the search text into a lower-case LIKE pattern that matches it anywhere. The LIKE
     * wildcards {@code %} and {@code _} (and the escape character) in the text are matched literally.
     *
     * @param search the search text, may be null or blank
     * @return the pattern; {@code %} (matches everything) when the text is null or blank
     */
    private static String containsPattern(String search) {
        if (search == null || search.isBlank()) {
            return "%";
        }
        String escaped = search.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /**
     * Builds the user-readable not-found exception.
     *
     * @param id the missing credit id
     * @return the exception to throw
     */
    private ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Credit " + id + " was not found.");
    }
}
