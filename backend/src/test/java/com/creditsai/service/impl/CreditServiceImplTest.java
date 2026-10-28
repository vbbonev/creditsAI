package com.creditsai.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.creditsai.dto.CreditCalculationRequest;
import com.creditsai.dto.CreditRequest;
import com.creditsai.dto.CreditResponse;
import com.creditsai.dto.CreditSummaryResponse;
import com.creditsai.dto.PaymentPlanResponse;
import com.creditsai.exception.BusinessRuleException;
import com.creditsai.exception.ResourceNotFoundException;
import com.creditsai.mapper.CreditMapper;
import com.creditsai.model.Client;
import com.creditsai.model.Credit;
import com.creditsai.model.CreditCurrency;
import com.creditsai.model.CreditStatus;
import com.creditsai.model.CreditType;
import com.creditsai.repository.ClientRepository;
import com.creditsai.repository.CreditRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CreditServiceImplTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 15);

    @Mock
    private CreditRepository creditRepository;

    @Mock
    private ClientRepository clientRepository;

    private CreditServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CreditServiceImpl(creditRepository, clientRepository, Mappers.getMapper(CreditMapper.class));
    }

    private static Client client(long id, boolean archived) {
        Client c = new Client();
        ReflectionTestUtils.setField(c, "id", id);
        c.setName("Acme");
        c.setIdentifier("123");
        c.setAddress("Sofia");
        if (archived) {
            c.archive();
        }
        return c;
    }

    private static CreditRequest request(long clientId, int months, boolean collateral) {
        return new CreditRequest(clientId, CreditType.CONSUMER, new BigDecimal("10000"), CreditCurrency.EUR,
                new BigDecimal("12"), START, START.plusMonths(months), collateral,
                collateral ? new BigDecimal("500") : null, collateral ? "Car" : null);
    }

    private Credit existingCredit(Client client, int months) {
        Credit credit = new Credit();
        credit.assignTo(client);
        ReflectionTestUtils.setField(credit, "id", 7L);
        service = new CreditServiceImpl(creditRepository, clientRepository, Mappers.getMapper(CreditMapper.class));
        PaymentPlan plan = PaymentPlanCalculator.calculate(new BigDecimal("10000"), new BigDecimal("12"), START,
                START.plusMonths(months));
        CreditMapper mapper = Mappers.getMapper(CreditMapper.class);
        credit.replacePayments(plan.termMonths(), plan.monthlyPayment(),
                plan.rows().stream().map(mapper::toPayment).toList());
        return credit;
    }

    // ---- calculate ----

    @Test
    void calculate_validRequest_returnsPlanWithoutTouchingRepositories() {
        // given
        CreditCalculationRequest request = new CreditCalculationRequest(new BigDecimal("10000"), CreditCurrency.EUR,
                new BigDecimal("12"), START, START.plusMonths(12));

        // when
        PaymentPlanResponse plan = service.calculate(request);

        // then
        assertThat(plan.termMonths()).isEqualTo(12);
        assertThat(plan.monthlyPayment()).isEqualByComparingTo("888.49");
        assertThat(plan.rows()).hasSize(12);
        verifyNoInteractions(creditRepository, clientRepository);
    }

    // ---- create ----

    @Test
    void create_validRequest_savesActiveCreditWithRecalculatedPlan() {
        // given
        given(clientRepository.findByIdAndArchivedFalse(1L)).willReturn(Optional.of(client(1, false)));
        given(creditRepository.saveAndFlush(any(Credit.class))).willAnswer(i -> i.getArgument(0));

        // when
        CreditResponse response = service.create(request(1, 12, true));

        // then
        ArgumentCaptor<Credit> saved = ArgumentCaptor.forClass(Credit.class);
        verify(creditRepository).saveAndFlush(saved.capture());
        Credit credit = saved.getValue();
        assertThat(credit.getStatus()).isEqualTo(CreditStatus.ACTIVE);
        assertThat(credit.isArchived()).isFalse();
        assertThat(credit.getTermMonths()).isEqualTo(12);
        assertThat(credit.getMonthlyPayment()).isEqualByComparingTo("888.49");
        assertThat(credit.getPayments()).hasSize(12);
        assertThat(credit.isHasCollateral()).isTrue();
        assertThat(credit.getCollateralValue()).isEqualByComparingTo("500");
        assertThat(response.plan().rows()).hasSize(12);
        assertThat(response.plan().totalPaid()).isEqualByComparingTo(
                response.plan().rows().stream().map(r -> r.payment()).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertThat(response.client().id()).isEqualTo(1L);
    }

    @Test
    void create_withoutCollateral_clearsCollateralFields() {
        // given
        given(clientRepository.findByIdAndArchivedFalse(1L)).willReturn(Optional.of(client(1, false)));
        given(creditRepository.saveAndFlush(any(Credit.class))).willAnswer(i -> i.getArgument(0));

        // when
        CreditResponse response = service.create(request(1, 6, false));

        // then
        assertThat(response.hasCollateral()).isFalse();
        assertThat(response.collateralValue()).isNull();
        assertThat(response.collateralDescription()).isNull();
    }

    @Test
    void create_unknownClient_throwsResourceNotFoundAndSavesNothing() {
        // given
        given(clientRepository.findByIdAndArchivedFalse(9L)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.create(request(9, 12, false)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Client 9 was not found.");
        verify(creditRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_archivedClient_throwsResourceNotFoundAndSavesNothing() {
        // given: an archived client is hidden from the lookup, like a missing one
        given(clientRepository.findByIdAndArchivedFalse(1L)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.create(request(1, 12, false)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Client 1 was not found.");
        verify(creditRepository, never()).saveAndFlush(any());
    }

    // ---- list ----

    @Test
    void list_noSearchNoStatus_usesMatchAllPatternAndNoStatusFilter() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        given(creditRepository.search(eq("%"), eq(false), eq(null), eq(false), eq(pageable)))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        // when
        Page<CreditSummaryResponse> page = service.list(null, null, false, pageable);

        // then
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void list_blankSearch_isTreatedAsNoSearch() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        given(creditRepository.search(eq("%"), anyBoolean(), any(), anyBoolean(), any()))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        // when
        service.list("   ", null, false, pageable);

        // then
        verify(creditRepository).search("%", false, null, false, pageable);
    }

    @Test
    void list_searchWithWildcards_lowersAndEscapesPattern() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        given(creditRepository.search(any(), anyBoolean(), any(), anyBoolean(), any()))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        // when
        service.list("  50%_Ab\\ ", CreditStatus.ACTIVE, true, pageable);

        // then
        verify(creditRepository).search("%50\\%\\_ab\\\\%", true, CreditStatus.ACTIVE, true, pageable);
    }

    @Test
    void list_results_areMappedToSummaries() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        Credit credit = existingCredit(client(1, false), 12);
        given(creditRepository.search(any(), anyBoolean(), any(), anyBoolean(), any()))
                .willReturn(new PageImpl<>(List.of(credit), pageable, 1));

        // when
        Page<CreditSummaryResponse> page = service.list(null, null, false, pageable);

        // then
        assertThat(page.getContent()).singleElement().satisfies(s -> {
            assertThat(s.termMonths()).isEqualTo(12);
            assertThat(s.monthlyPayment()).isEqualByComparingTo("888.49");
            assertThat(s.client().name()).isEqualTo("Acme");
        });
    }

    // ---- get ----

    @Test
    void get_nonAdmin_usesNonArchivedLookupAndThrowsWhenMissing() {
        // given
        given(creditRepository.findByIdAndArchivedFalseAndClientArchivedFalse(7L)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.get(7L, false))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Credit 7 was not found.");
        verify(creditRepository, never()).findById(any());
    }

    @Test
    void get_admin_findsArchivedCredit() {
        // given
        Credit credit = existingCredit(client(1, false), 3);
        given(creditRepository.findById(7L)).willReturn(Optional.of(credit));

        // when
        CreditResponse response = service.get(7L, true);

        // then
        assertThat(response.plan().rows()).hasSize(3);
        verify(creditRepository, never()).findByIdAndArchivedFalseAndClientArchivedFalse(any());
    }

    // ---- update ----

    @Test
    void update_changedTerm_replacesPlanRows() {
        // given
        Credit credit = existingCredit(client(1, false), 12);
        given(creditRepository.findById(7L)).willReturn(Optional.of(credit));
        given(clientRepository.findById(1L)).willReturn(Optional.of(credit.getClient()));
        given(creditRepository.saveAndFlush(credit)).willReturn(credit);

        // when
        CreditResponse response = service.update(7L, request(1, 24, false));

        // then
        assertThat(credit.getPayments()).hasSize(24);
        assertThat(credit.getTermMonths()).isEqualTo(24);
        assertThat(credit.getEndDate()).isEqualTo(START.plusMonths(24));
        assertThat(response.plan().rows()).hasSize(24);
        verify(creditRepository).flush();
    }

    @Test
    void update_moveToArchivedClient_throwsBusinessRuleException() {
        // given
        Credit credit = existingCredit(client(1, false), 12);
        given(creditRepository.findById(7L)).willReturn(Optional.of(credit));
        given(clientRepository.findById(2L)).willReturn(Optional.of(client(2, true)));

        // when / then
        assertThatThrownBy(() -> service.update(7L, request(2, 12, false)))
                .isInstanceOf(BusinessRuleException.class);
        verify(creditRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_unknownCredit_throwsResourceNotFound() {
        // given
        given(creditRepository.findById(7L)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.update(7L, request(1, 12, false)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_unknownClient_throwsResourceNotFound() {
        // given
        given(creditRepository.findById(7L)).willReturn(Optional.of(existingCredit(client(1, false), 12)));
        given(clientRepository.findById(5L)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.update(7L, request(5, 12, false)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Client 5 was not found.");
    }

    @Test
    void update_archivedCreditOfSameClient_isAllowedEvenIfClientArchived() {
        // given: the client was archived after the (inactive) credit was archived
        Client client = client(1, false);
        Credit credit = existingCredit(client, 12);
        credit.changeStatus(CreditStatus.INACTIVE);
        credit.archive();
        client.archive();
        given(creditRepository.findById(7L)).willReturn(Optional.of(credit));
        given(clientRepository.findById(1L)).willReturn(Optional.of(client));
        given(creditRepository.saveAndFlush(credit)).willReturn(credit);

        // when
        CreditResponse response = service.update(7L, request(1, 6, false));

        // then
        assertThat(response.plan().rows()).hasSize(6);
    }

    // ---- status ----

    @Test
    void changeStatus_deactivate_savesInactive() {
        // given
        Credit credit = existingCredit(client(1, false), 12);
        given(creditRepository.findById(7L)).willReturn(Optional.of(credit));
        given(creditRepository.save(credit)).willReturn(credit);

        // when
        CreditResponse response = service.changeStatus(7L, CreditStatus.INACTIVE);

        // then
        assertThat(response.status()).isEqualTo(CreditStatus.INACTIVE);
    }

    @Test
    void changeStatus_activateArchivedCredit_throwsBusinessRuleExceptionAndDoesNotSave() {
        // given
        Credit credit = existingCredit(client(1, false), 12);
        credit.changeStatus(CreditStatus.INACTIVE);
        credit.archive();
        given(creditRepository.findById(7L)).willReturn(Optional.of(credit));

        // when / then
        assertThatThrownBy(() -> service.changeStatus(7L, CreditStatus.ACTIVE))
                .isInstanceOf(BusinessRuleException.class);
        verify(creditRepository, never()).save(any());
    }

    @Test
    void changeStatus_unknownCredit_throwsResourceNotFound() {
        // given
        given(creditRepository.findById(7L)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.changeStatus(7L, CreditStatus.ACTIVE))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- archive / restore ----

    @Test
    void archive_inactiveCredit_marksArchivedAndSaves() {
        // given
        Credit credit = existingCredit(client(1, false), 12);
        credit.changeStatus(CreditStatus.INACTIVE);
        given(creditRepository.findById(7L)).willReturn(Optional.of(credit));

        // when
        service.archive(7L);

        // then
        assertThat(credit.isArchived()).isTrue();
        verify(creditRepository).save(credit);
    }

    @Test
    void archive_activeCredit_throwsBusinessRuleExceptionAndDoesNotSave() {
        // given
        Credit credit = existingCredit(client(1, false), 12);
        given(creditRepository.findById(7L)).willReturn(Optional.of(credit));

        // when / then
        assertThatThrownBy(() -> service.archive(7L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("An active credit cannot be archived. Deactivate it first.");
        verify(creditRepository, never()).save(any());
    }

    @Test
    void archive_unknownCredit_throwsResourceNotFound() {
        // given
        given(creditRepository.findById(7L)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.archive(7L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void restore_archivedCredit_unarchivesAndStaysInactive() {
        // given
        Credit credit = existingCredit(client(1, false), 12);
        credit.changeStatus(CreditStatus.INACTIVE);
        credit.archive();
        given(creditRepository.findById(7L)).willReturn(Optional.of(credit));
        given(creditRepository.save(credit)).willReturn(credit);

        // when
        CreditResponse response = service.restore(7L);

        // then
        assertThat(response.archived()).isFalse();
        assertThat(response.status()).isEqualTo(CreditStatus.INACTIVE);
    }

    @Test
    void restore_notArchivedCredit_throwsBusinessRuleException() {
        // given
        given(creditRepository.findById(7L)).willReturn(Optional.of(existingCredit(client(1, false), 12)));

        // when / then
        assertThatThrownBy(() -> service.restore(7L)).isInstanceOf(BusinessRuleException.class);
        verify(creditRepository, never()).save(any());
    }
}
