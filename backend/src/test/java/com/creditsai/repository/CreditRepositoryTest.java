package com.creditsai.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.creditsai.TestcontainersConfiguration;
import com.creditsai.model.Client;
import com.creditsai.model.Credit;
import com.creditsai.model.CreditCurrency;
import com.creditsai.model.AttachmentDraft;
import com.creditsai.model.CreditPayment;
import com.creditsai.model.CreditStatus;
import com.creditsai.model.CreditType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Runs against the Testcontainers Postgres with the real Liquibase schema validated by Hibernate. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class CreditRepositoryTest {

    @Autowired
    private CreditRepository repository;

    @Autowired
    private ClientRepository clientRepository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
        clientRepository.deleteAll();
    }

    private Client client(String name, String identifier) {
        Client c = new Client();
        c.setName(name);
        c.setIdentifier(identifier);
        c.setAddress("Sofia");
        return clientRepository.saveAndFlush(c);
    }

    private Credit credit(Client client, String amount, CreditStatus status, boolean archived) {
        Credit credit = new Credit();
        credit.assignTo(client);
        credit.setType(CreditType.CONSUMER);
        credit.setAmount(new BigDecimal(amount));
        credit.setCurrency(CreditCurrency.EUR);
        credit.setInterestRate(new BigDecimal("5.2500"));
        credit.setStartDate(LocalDate.of(2026, 1, 1));
        credit.setEndDate(LocalDate.of(2026, 3, 1));
        credit.applyCollateral(false, null, null);
        credit.replacePayments(2, new BigDecimal("500.00"), List.of(
                row(1, "500.00", "500.00", "0.00"), row(2, "500.00", "0.00", "0.00")));
        credit.changeStatus(status);
        if (archived) {
            credit.changeStatus(CreditStatus.INACTIVE);
            credit.archive();
        }
        return repository.saveAndFlush(credit);
    }

    private static CreditPayment row(int n, String payment, String principal, String balance) {
        return CreditPayment.builder().paymentNumber(n).paymentDate(LocalDate.of(2026, 1 + n, 1))
                .payment(new BigDecimal(payment)).principal(new BigDecimal(principal))
                .interest(BigDecimal.ZERO).remainingBalance(new BigDecimal(balance)).build();
    }

    private Page<Credit> search(String pattern, CreditStatus status, boolean includeArchived, PageRequest page) {
        return repository.search(pattern, status != null, status, includeArchived, page);
    }

    @Test
    void save_newCredit_generatesCreditNumberAndTimestamps() {
        // when
        Credit saved = credit(client("Acme", "111"), "1000", CreditStatus.ACTIVE, false);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreditNumber()).matches("CR-" + Year.now().getValue() + "-\\d{6}");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(CreditStatus.ACTIVE);
    }

    @Test
    void save_twoCredits_getDistinctIncreasingCreditNumbers() {
        // given
        Client client = client("Acme", "111");

        // when
        Credit a = credit(client, "1000", CreditStatus.ACTIVE, false);
        Credit b = credit(client, "1000", CreditStatus.ACTIVE, false);

        // then
        assertThat(a.getCreditNumber()).isNotEqualTo(b.getCreditNumber());
        assertThat(b.getCreditNumber()).isGreaterThan(a.getCreditNumber());
    }

    @Test
    void save_creditWithPayments_persistsRowsInOrderViaCascade() {
        // given
        Credit saved = credit(client("Acme", "111"), "1000", CreditStatus.ACTIVE, false);

        // when
        Credit loaded = repository.findById(saved.getId()).orElseThrow();

        // then
        assertThat(loaded.getPayments()).extracting(CreditPayment::getPaymentNumber).containsExactly(1, 2);
    }

    @Test
    void search_noFilters_returnsOnlyNonArchived() {
        // given
        Client client = client("Acme", "111");
        credit(client, "1000", CreditStatus.ACTIVE, false);
        credit(client, "2000", CreditStatus.INACTIVE, true);

        // when
        Page<Credit> page = search("%", null, false, PageRequest.of(0, 10));

        // then
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).isArchived()).isFalse();
    }

    @Test
    void search_includeArchived_returnsArchivedToo() {
        // given
        Client client = client("Acme", "111");
        credit(client, "1000", CreditStatus.ACTIVE, false);
        credit(client, "2000", CreditStatus.INACTIVE, true);

        // when / then
        assertThat(search("%", null, true, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);
    }

    @Test
    void search_statusFilter_returnsOnlyThatStatus() {
        // given
        Client client = client("Acme", "111");
        credit(client, "1000", CreditStatus.ACTIVE, false);
        credit(client, "2000", CreditStatus.INACTIVE, false);

        // when
        Page<Credit> active = search("%", CreditStatus.ACTIVE, false, PageRequest.of(0, 10));
        Page<Credit> inactive = search("%", CreditStatus.INACTIVE, false, PageRequest.of(0, 10));

        // then
        assertThat(active.getContent()).extracting(Credit::getAmount).singleElement()
                .satisfies(a -> assertThat(a).isEqualByComparingTo("1000"));
        assertThat(inactive.getContent()).extracting(Credit::getAmount).singleElement()
                .satisfies(a -> assertThat(a).isEqualByComparingTo("2000"));
    }

    @Test
    void search_byClientNameCaseInsensitive_matchesPartOfName() {
        // given
        credit(client("Acme Corp", "111"), "1000", CreditStatus.ACTIVE, false);
        credit(client("Beta", "222"), "2000", CreditStatus.ACTIVE, false);

        // when
        Page<Credit> page = search("%acme%", null, false, PageRequest.of(0, 10));

        // then
        assertThat(page.getContent()).extracting(c -> c.getClient().getName()).containsExactly("Acme Corp");
    }

    @Test
    void search_byClientIdentifierCaseInsensitive_matches() {
        // given
        credit(client("Acme", "BG-ABC-1"), "1000", CreditStatus.ACTIVE, false);
        credit(client("Beta", "222"), "2000", CreditStatus.ACTIVE, false);

        // when
        Page<Credit> page = search("%abc%", null, false, PageRequest.of(0, 10));

        // then
        assertThat(page.getContent()).extracting(c -> c.getClient().getName()).containsExactly("Acme");
    }

    @Test
    void search_byCreditNumber_matches() {
        // given
        Client client = client("Acme", "111");
        Credit a = credit(client, "1000", CreditStatus.ACTIVE, false);
        credit(client, "2000", CreditStatus.ACTIVE, false);

        // when
        Page<Credit> page = search("%" + a.getCreditNumber().toLowerCase() + "%", null, false, PageRequest.of(0, 10));

        // then
        assertThat(page.getContent()).extracting(Credit::getId).containsExactly(a.getId());
    }

    @Test
    void search_escapedWildcard_isMatchedLiterally() {
        // given
        credit(client("100% Co", "111"), "1000", CreditStatus.ACTIVE, false);
        credit(client("1000 Co", "222"), "2000", CreditStatus.ACTIVE, false);

        // when
        Page<Credit> page = search("%100\\%%", null, false, PageRequest.of(0, 10));

        // then
        assertThat(page.getContent()).extracting(c -> c.getClient().getName()).containsExactly("100% Co");
    }

    @Test
    void search_combinedFilters_applyTogether() {
        // given
        Client acme = client("Acme", "111");
        credit(acme, "1000", CreditStatus.ACTIVE, false);
        credit(acme, "2000", CreditStatus.INACTIVE, false);
        credit(client("Beta", "222"), "3000", CreditStatus.ACTIVE, false);

        // when
        Page<Credit> page = search("%acme%", CreditStatus.INACTIVE, false, PageRequest.of(0, 10));

        // then
        assertThat(page.getContent()).singleElement()
                .satisfies(c -> assertThat(c.getAmount()).isEqualByComparingTo("2000"));
    }

    @Test
    void search_pagination_returnsPageSliceAndTotals() {
        // given
        Client client = client("Acme", "111");
        for (int i = 1; i <= 5; i++) {
            credit(client, i + "000", CreditStatus.ACTIVE, false);
        }

        // when
        Page<Credit> page = search("%", null, false, PageRequest.of(1, 2, Sort.by("amount")));

        // then
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getContent()).extracting(c -> c.getAmount().intValue()).containsExactly(3000, 4000);
    }

    @Test
    void search_sortDescendingByAmount_ordersResults() {
        // given
        Client client = client("Acme", "111");
        credit(client, "1000", CreditStatus.ACTIVE, false);
        credit(client, "3000", CreditStatus.ACTIVE, false);
        credit(client, "2000", CreditStatus.ACTIVE, false);

        // when
        Page<Credit> page = search("%", null, false, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "amount")));

        // then
        assertThat(page.getContent()).extracting(c -> c.getAmount().intValue()).containsExactly(3000, 2000, 1000);
    }

    @Test
    void search_sortByEveryAllowedField_works() {
        // given
        credit(client("Acme", "111"), "1000", CreditStatus.ACTIVE, false);

        // when / then
        for (String field : List.of("creditNumber", "startDate", "endDate", "amount", "createdAt")) {
            assertThat(search("%", null, false, PageRequest.of(0, 10, Sort.by(field))).getContent())
                    .as(field).hasSize(1);
        }
    }

    @Test
    void findByIdAndArchivedFalse_archivedCredit_isEmpty() {
        // given
        Client client = client("Acme", "111");
        Credit active = credit(client, "1000", CreditStatus.ACTIVE, false);
        Credit archived = credit(client, "2000", CreditStatus.INACTIVE, true);

        // when / then
        assertThat(repository.findByIdAndArchivedFalse(active.getId())).isPresent();
        assertThat(repository.findByIdAndArchivedFalse(archived.getId())).isEmpty();
        assertThat(repository.findById(archived.getId())).isPresent();
    }

    private Credit inactiveCreditOfArchivedClient() {
        Client client = client("Gone Ltd", "ARCH-1");
        Credit credit = credit(client, "1000", CreditStatus.INACTIVE, false);
        client.archive();
        clientRepository.saveAndFlush(client);
        return credit;
    }

    @Test
    void search_creditOfArchivedClientWithoutIncludeArchived_isExcludedFromContentAndCount() {
        // given
        inactiveCreditOfArchivedClient();
        credit(client("Live", "LIVE-1"), "2000", CreditStatus.ACTIVE, false);

        // when
        Page<Credit> page = search("%", null, false, PageRequest.of(0, 10));

        // then
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(c -> c.getClient().getName()).containsExactly("Live");
    }

    @Test
    void search_creditOfArchivedClientWithIncludeArchived_isIncluded() {
        // given
        inactiveCreditOfArchivedClient();
        credit(client("Live", "LIVE-1"), "2000", CreditStatus.ACTIVE, false);

        // when
        Page<Credit> page = search("%", null, true, PageRequest.of(0, 10));

        // then
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void search_byArchivedClientNameWithoutIncludeArchived_returnsNothing() {
        // given
        inactiveCreditOfArchivedClient();

        // when / then
        assertThat(search("%gone%", null, false, PageRequest.of(0, 10)).getTotalElements()).isZero();
        assertThat(search("%arch-1%", null, false, PageRequest.of(0, 10)).getTotalElements()).isZero();
        assertThat(search("%gone%", null, true, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
    }

    @Test
    void findByIdAndArchivedFalseAndClientArchivedFalse_creditOfArchivedClient_isEmpty() {
        // given
        Credit hidden = inactiveCreditOfArchivedClient();
        Credit visible = credit(client("Live", "LIVE-1"), "2000", CreditStatus.ACTIVE, false);
        Credit archivedCredit = credit(client("Other", "O-1"), "3000", CreditStatus.INACTIVE, true);

        // when / then
        assertThat(repository.findByIdAndArchivedFalseAndClientArchivedFalse(hidden.getId())).isEmpty();
        assertThat(repository.findByIdAndArchivedFalseAndClientArchivedFalse(archivedCredit.getId())).isEmpty();
        assertThat(repository.findByIdAndArchivedFalseAndClientArchivedFalse(visible.getId())).isPresent();
        assertThat(repository.findById(hidden.getId())).isPresent();
    }

    @Autowired
    private CreditAttachmentRepository attachmentRepository;

    @Test
    void attachmentProjections_returnCreditIdsAndFileNames() {
        // given
        Client client = client("A", "1");
        Credit first = credit(client, "1000", CreditStatus.ACTIVE, false);
        Credit second = credit(client, "2000", CreditStatus.ACTIVE, false);
        credit(client, "3000", CreditStatus.ACTIVE, false);
        first.attach(new AttachmentDraft("b.pdf", "application/pdf", 1, "0".repeat(64), "u"), 30, 1000);
        first.attach(new AttachmentDraft("a.pdf", "application/pdf", 1, "0".repeat(64), "u"), 30, 1000);
        second.attach(new AttachmentDraft("c.pdf", "application/pdf", 1, "0".repeat(64), "u"), 30, 1000);
        repository.saveAndFlush(first);
        repository.saveAndFlush(second);

        // when / then
        assertThat(attachmentRepository.findCreditIdsWithAttachments())
                .containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(attachmentRepository.findFileNamesByCreditId(first.getId()))
                .containsExactlyInAnyOrder("a.pdf", "b.pdf");
        assertThat(attachmentRepository.findFileNamesByCreditId(-1L)).isEmpty();
    }

    @Test
    void findByIdForUpdate_existingAndMissing_returnsCreditOrEmpty() {
        // given
        Credit credit = credit(client("A", "1"), "1000", CreditStatus.ACTIVE, false);

        // when / then
        assertThat(repository.findByIdForUpdate(credit.getId())).isPresent();
        assertThat(repository.findByIdForUpdate(-1L)).isEmpty();
    }

    @Test
    void removeAttachment_orphanRemoval_deletesTheRow() {
        // given
        Credit credit = credit(client("A", "1"), "1000", CreditStatus.ACTIVE, false);
        Long id = credit.attach(new AttachmentDraft("a.pdf", "application/pdf", 1, "0".repeat(64), "u"), 30, 1000)
                .getId();
        repository.saveAndFlush(credit);
        Long attachmentId = attachmentRepository.findAll().get(0).getId();

        // when
        credit.removeAttachment(attachmentId);
        repository.saveAndFlush(credit);

        // then
        assertThat(id).isNull();
        assertThat(attachmentRepository.count()).isZero();
    }
}
