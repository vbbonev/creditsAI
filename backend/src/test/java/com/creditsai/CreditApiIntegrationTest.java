package com.creditsai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.creditsai.model.Credit;
import com.creditsai.model.CreditStatus;
import com.creditsai.repository.ClientRepository;
import com.creditsai.repository.CreditRepository;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Full-stack flow for credits: controller, security, service, JPA and Liquibase on real Postgres. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CreditApiIntegrationTest {

    private static final RequestPostProcessor USER = jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
    private static final RequestPostProcessor ADMIN = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    private static final String CLIENT_BODY =
            "{\"name\":\"Acme\",\"identifier\":\"BG-123\",\"address\":\"Sofia\",\"description\":\"note\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private CreditRepository creditRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        creditRepository.deleteAll();
        clientRepository.deleteAll();
    }

    private static String creditBody(long clientId, int months, String amount) {
        return """
                {"clientId":%d,"type":"CONSUMER","amount":%s,"currency":"EUR","interestRate":12,
                 "startDate":"2026-01-15","endDate":"%s","hasCollateral":false}"""
                .formatted(clientId, amount, java.time.LocalDate.of(2026, 1, 15).plusMonths(months));
    }

    private static long idFrom(String location, String path) {
        Matcher m = Pattern.compile(path + "/(\\d+)$").matcher(location);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }

    private long createClient(String name) throws Exception {
        String location = mockMvc.perform(post("/api/clients").with(USER).contentType(MediaType.APPLICATION_JSON)
                        .content(CLIENT_BODY.replace("Acme", name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        return idFrom(location, "/api/clients");
    }

    private long createCredit(long clientId, int months) throws Exception {
        String location = mockMvc.perform(post("/api/credits").with(USER).contentType(MediaType.APPLICATION_JSON)
                        .content(creditBody(clientId, months, "10000")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getHeader("Location");
        return idFrom(location, "/api/credits");
    }

    private void deactivate(long creditId) throws Exception {
        mockMvc.perform(post("/api/credits/" + creditId + "/status").with(ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}")).andExpect(status().isOk());
    }

    // ---- calculate ----

    @Test
    void calculate_validRequest_returnsPlanAndPersistsNothing() throws Exception {
        // when / then
        mockMvc.perform(post("/api/credits/calculate").with(USER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10000,\"currency\":\"EUR\",\"interestRate\":12,"
                                + "\"startDate\":\"2026-01-15\",\"endDate\":\"2027-01-15\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termMonths").value(12))
                .andExpect(jsonPath("$.monthlyPayment").value(888.49))
                .andExpect(jsonPath("$.rows.length()").value(12))
                .andExpect(jsonPath("$.rows[11].remainingBalance").value(0.00));
        assertThat(creditRepository.count()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from credit_payments", Long.class)).isZero();
    }

    // ---- create + lifecycle ----

    @Test
    void creditLifecycle_createListGetDeactivateArchiveRestore_behavesPerRole() throws Exception {
        // given: a client and a credit created by a USER
        long clientId = createClient("Acme");
        long id = createCredit(clientId, 12);

        // then: detail has a generated number, ACTIVE status and the recalculated plan
        mockMvc.perform(get("/api/credits/" + id).with(USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditNumber").value(org.hamcrest.Matchers.matchesPattern("CR-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.client.id").value(clientId))
                .andExpect(jsonPath("$.client.name").value("Acme"))
                .andExpect(jsonPath("$.plan.monthlyPayment").value(888.49))
                .andExpect(jsonPath("$.plan.rows.length()").value(12))
                .andExpect(jsonPath("$.plan.rows[0].interest").value(100.00))
                .andExpect(jsonPath("$.plan.rows[11].remainingBalance").value(0.00));

        // and: listed with the stable page shape and no plan rows
        mockMvc.perform(get("/api/credits").with(USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].termMonths").value(12))
                .andExpect(jsonPath("$.content[0].plan").doesNotExist())
                .andExpect(jsonPath("$.page.totalElements").value(1));

        // when: a USER tries to archive, deactivate: all forbidden
        mockMvc.perform(delete("/api/credits/" + id).with(USER)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/credits/" + id + "/status").with(USER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVE\"}")).andExpect(status().isForbidden());

        // when: ADMIN archives an ACTIVE credit
        mockMvc.perform(delete("/api/credits/" + id).with(ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An active credit cannot be archived. Deactivate it first."));
        assertThat(creditRepository.findById(id).orElseThrow().isArchived()).isFalse();

        // when: ADMIN deactivates and archives
        deactivate(id);
        mockMvc.perform(delete("/api/credits/" + id).with(ADMIN)).andExpect(status().isNoContent());

        // then: row still exists
        Credit archived = creditRepository.findById(id).orElseThrow();
        assertThat(archived.isArchived()).isTrue();
        assertThat(archived.getArchivedAt()).isNotNull();

        // and: gone from lists, 404 for USER, 200 for ADMIN
        mockMvc.perform(get("/api/credits").with(USER)).andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(get("/api/credits/" + id).with(USER)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/credits/" + id).with(ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
        mockMvc.perform(get("/api/credits?includeArchived=true").with(ADMIN))
                .andExpect(jsonPath("$.content.length()").value(1));

        // and: activating an archived credit is a conflict
        mockMvc.perform(post("/api/credits/" + id + "/status").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict());

        // when: restored
        mockMvc.perform(post("/api/credits/" + id + "/restore").with(USER)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/credits/" + id + "/restore").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        mockMvc.perform(get("/api/credits/" + id).with(USER)).andExpect(status().isOk());

        // and: restoring again is a conflict
        mockMvc.perform(post("/api/credits/" + id + "/restore").with(ADMIN)).andExpect(status().isConflict());
    }

    @Test
    void create_clientSentServerFields_areIgnoredAndPlanIsRecalculated() throws Exception {
        // given
        long clientId = createClient("Acme");
        String body = creditBody(clientId, 12, "10000").replace("{\"clientId\"",
                "{\"creditNumber\":\"HACK\",\"status\":\"INACTIVE\",\"archived\":true,\"monthlyPayment\":1,"
                        + "\"termMonths\":2,\"plan\":{\"rows\":[]},\"clientId\"");

        // when / then
        mockMvc.perform(post("/api/credits").with(USER).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creditNumber").value(org.hamcrest.Matchers.startsWith("CR-")))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.plan.monthlyPayment").value(888.49))
                .andExpect(jsonPath("$.plan.rows.length()").value(12));
    }

    @Test
    void create_severalCredits_getUniqueIncreasingNumbers() throws Exception {
        // given
        long clientId = createClient("Acme");

        // when
        long a = createCredit(clientId, 6);
        long b = createCredit(clientId, 6);

        // then
        String na = creditRepository.findById(a).orElseThrow().getCreditNumber();
        String nb = creditRepository.findById(b).orElseThrow().getCreditNumber();
        assertThat(na).isNotEqualTo(nb);
        assertThat(nb).isGreaterThan(na);
    }

    @Test
    void create_unknownClient_returns404AndNothingIsStored() throws Exception {
        // when / then
        mockMvc.perform(post("/api/credits").with(USER).contentType(MediaType.APPLICATION_JSON)
                        .content(creditBody(999999, 12, "1000")))
                .andExpect(status().isNotFound());
        assertThat(creditRepository.count()).isZero();
    }

    @Test
    void create_archivedClient_returns404AndNothingIsStored() throws Exception {
        // given
        long clientId = createClient("Acme");
        mockMvc.perform(delete("/api/clients/" + clientId).with(ADMIN)).andExpect(status().isNoContent());

        // when / then
        mockMvc.perform(post("/api/credits").with(USER).contentType(MediaType.APPLICATION_JSON)
                        .content(creditBody(clientId, 12, "1000")))
                .andExpect(status().isNotFound());
        assertThat(creditRepository.count()).isZero();
    }

    @Test
    void create_invalidBody_returns400WithFieldErrorsAndNothingIsStored() throws Exception {
        // given
        long clientId = createClient("Acme");

        // when / then
        mockMvc.perform(post("/api/credits").with(USER).contentType(MediaType.APPLICATION_JSON)
                        .content(creditBody(clientId, 12, "100000001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").value("Amount must be at most 100000000."));
        assertThat(creditRepository.count()).isZero();
    }

    @Test
    void create_withCollateral_storesCollateral() throws Exception {
        // given
        long clientId = createClient("Acme");
        String body = creditBody(clientId, 12, "1000").replace("\"hasCollateral\":false",
                "\"hasCollateral\":true,\"collateralValue\":2500.50,\"collateralDescription\":\"Flat\"");

        // when / then
        mockMvc.perform(post("/api/credits").with(USER).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasCollateral").value(true))
                .andExpect(jsonPath("$.collateralValue").value(2500.50))
                .andExpect(jsonPath("$.collateralDescription").value("Flat"));
    }

    @Test
    void create_monthEndStart_usesClampedDates() throws Exception {
        // given
        long clientId = createClient("Acme");
        String body = creditBody(clientId, 3, "1000").replace("2026-01-15", "2024-01-31").replace("2026-04-15",
                "2024-04-30");

        // when / then
        mockMvc.perform(post("/api/credits").with(USER).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plan.rows[0].date").value("2024-02-29"))
                .andExpect(jsonPath("$.plan.rows[1].date").value("2024-03-31"))
                .andExpect(jsonPath("$.plan.rows[2].date").value("2024-04-30"));
    }

    // ---- update ----

    @Test
    void update_changedTerm_replacesPaymentRows() throws Exception {
        // given
        long clientId = createClient("Acme");
        long id = createCredit(clientId, 12);

        // when: USER is forbidden, ADMIN regenerates with 24 months
        mockMvc.perform(put("/api/credits/" + id).with(USER).contentType(MediaType.APPLICATION_JSON)
                .content(creditBody(clientId, 24, "10000"))).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/credits/" + id).with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content(creditBody(clientId, 24, "10000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.termMonths").value(24))
                .andExpect(jsonPath("$.plan.rows.length()").value(24))
                .andExpect(jsonPath("$.endDate").value("2028-01-15"));

        // then: old rows are gone from the table
        assertThat(jdbc.queryForObject("select count(*) from credit_payments where credit_id = ?", Long.class, id))
                .isEqualTo(24L);
        assertThat(creditRepository.findById(id).orElseThrow().getCreditNumber()).isNotNull();

        // and: shrinking works too
        mockMvc.perform(put("/api/credits/" + id).with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content(creditBody(clientId, 6, "5000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.rows.length()").value(6));
        assertThat(jdbc.queryForObject("select count(*) from credit_payments where credit_id = ?", Long.class, id))
                .isEqualTo(6L);
    }

    @Test
    void update_clientSentCreditNumber_isIgnored() throws Exception {
        // given
        long clientId = createClient("Acme");
        long id = createCredit(clientId, 12);
        String before = creditRepository.findById(id).orElseThrow().getCreditNumber();

        // when
        mockMvc.perform(put("/api/credits/" + id).with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content(creditBody(clientId, 12, "10000").replace("{\"clientId\"",
                                "{\"creditNumber\":\"HACK\",\"status\":\"INACTIVE\",\"clientId\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditNumber").value(before))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void update_toArchivedClient_returns409() throws Exception {
        // given
        long clientId = createClient("Acme");
        long other = createClient("Other");
        long id = createCredit(clientId, 12);
        mockMvc.perform(delete("/api/clients/" + other).with(ADMIN)).andExpect(status().isNoContent());

        // when / then
        mockMvc.perform(put("/api/credits/" + id).with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content(creditBody(other, 12, "10000"))).andExpect(status().isConflict());
    }

    @Test
    void update_missingCredit_returns404() throws Exception {
        // given
        long clientId = createClient("Acme");

        // when / then
        mockMvc.perform(put("/api/credits/999999").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content(creditBody(clientId, 12, "10000"))).andExpect(status().isNotFound());
    }

    // ---- status ----

    @Test
    void changeStatus_toggle_worksAndIsAdminOnly() throws Exception {
        // given
        long id = createCredit(createClient("Acme"), 12);

        // when / then
        deactivate(id);
        assertThat(creditRepository.findById(id).orElseThrow().getStatus()).isEqualTo(CreditStatus.INACTIVE);
        mockMvc.perform(post("/api/credits/" + id + "/status").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void changeStatus_activateCreditOfArchivedClient_returns409() throws Exception {
        // given: an inactive credit whose client is then archived
        long clientId = createClient("Acme");
        long id = createCredit(clientId, 12);
        deactivate(id);
        mockMvc.perform(delete("/api/clients/" + clientId).with(ADMIN)).andExpect(status().isNoContent());

        // when / then
        mockMvc.perform(post("/api/credits/" + id + "/status").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict());
        assertThat(creditRepository.findById(id).orElseThrow().getStatus()).isEqualTo(CreditStatus.INACTIVE);
    }

    // ---- client archive rule ----

    @Test
    void archiveClient_withActiveCredit_returns409ThenSucceedsAfterDeactivation() throws Exception {
        // given
        long clientId = createClient("Acme");
        long creditId = createCredit(clientId, 12);

        // when / then: blocked with a readable message and the client stays unarchived
        mockMvc.perform(delete("/api/clients/" + clientId).with(ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Client has active credits and cannot be archived."));
        assertThat(clientRepository.findById(clientId).orElseThrow().isArchived()).isFalse();

        // when: the credit is deactivated
        deactivate(creditId);

        // then: archiving works and the credit row still exists
        mockMvc.perform(delete("/api/clients/" + clientId).with(ADMIN)).andExpect(status().isNoContent());
        assertThat(clientRepository.findById(clientId).orElseThrow().isArchived()).isTrue();
        assertThat(creditRepository.findById(creditId)).isPresent();
    }

    @Test
    void archiveClient_withArchivedInactiveCredit_succeeds() throws Exception {
        // given
        long clientId = createClient("Acme");
        long creditId = createCredit(clientId, 12);
        deactivate(creditId);
        mockMvc.perform(delete("/api/credits/" + creditId).with(ADMIN)).andExpect(status().isNoContent());

        // when / then
        mockMvc.perform(delete("/api/clients/" + clientId).with(ADMIN)).andExpect(status().isNoContent());
    }

    // ---- list ----

    @Test
    void list_search_matchesNumberClientNameAndIdentifierCaseInsensitively() throws Exception {
        // given
        long acme = createClient("Acme Corp");
        long beta = createClient("Beta");
        createCredit(acme, 6);
        long betaCredit = createCredit(beta, 6);
        String betaNumber = creditRepository.findById(betaCredit).orElseThrow().getCreditNumber();

        // when / then: by client name
        mockMvc.perform(get("/api/credits?search=aCmE").with(USER))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].client.name").value("Acme Corp"));
        // by identifier
        mockMvc.perform(get("/api/credits?search=bg-123").with(USER))
                .andExpect(jsonPath("$.content.length()").value(2));
        // by credit number
        mockMvc.perform(get("/api/credits?search=" + betaNumber.toLowerCase()).with(USER))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].client.name").value("Beta"));
        // wildcard characters are literal
        mockMvc.perform(get("/api/credits?search=%25").with(USER))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void list_statusFilter_returnsOnlyMatching() throws Exception {
        // given
        long clientId = createClient("Acme");
        long first = createCredit(clientId, 6);
        createCredit(clientId, 6);
        deactivate(first);

        // when / then
        mockMvc.perform(get("/api/credits?status=INACTIVE").with(USER))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(first));
        mockMvc.perform(get("/api/credits?status=ACTIVE").with(USER))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void list_includeArchivedAsUser_returns403() throws Exception {
        mockMvc.perform(get("/api/credits?includeArchived=true").with(USER)).andExpect(status().isForbidden());
    }

    @Test
    void list_sortAndPaging_work() throws Exception {
        // given
        long clientId = createClient("Acme");
        for (String amount : new String[] {"3000", "1000", "2000"}) {
            mockMvc.perform(post("/api/credits").with(USER).contentType(MediaType.APPLICATION_JSON)
                    .content(creditBody(clientId, 6, amount))).andExpect(status().isCreated());
        }

        // when / then
        mockMvc.perform(get("/api/credits?sort=amount,asc&size=2").with(USER))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].amount").value(1000))
                .andExpect(jsonPath("$.content[1].amount").value(2000))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2));
        mockMvc.perform(get("/api/credits?sort=amount,desc&size=2&page=1").with(USER))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(1000));
        mockMvc.perform(get("/api/credits?sort=monthlyPayment").with(USER)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/credits?size=100000").with(USER))
                .andExpect(jsonPath("$.page.size").value(100));
    }

    @Test
    void missingCredit_operations_return404() throws Exception {
        // when / then
        mockMvc.perform(get("/api/credits/999999").with(ADMIN)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/credits/999999").with(ADMIN)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/credits/999999/restore").with(ADMIN)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/credits/999999/status").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVE\"}")).andExpect(status().isNotFound());
    }

    @Test
    void endpoints_withoutToken_return401() throws Exception {
        // when / then
        mockMvc.perform(get("/api/credits")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/credits/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/credits/calculate").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/credits").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/credits/1")).andExpect(status().isUnauthorized());
    }

    // ---- credits of archived clients ----

    /** An inactive, non-archived credit whose client was archived afterwards. */
    private long hiddenCredit() throws Exception {
        long clientId = createClient("Ghost Ltd");
        long id = createCredit(clientId, 6);
        deactivate(id);
        mockMvc.perform(delete("/api/clients/" + clientId).with(ADMIN)).andExpect(status().isNoContent());
        return id;
    }

    @Test
    void get_creditOfArchivedClient_is404ForUserAnd200ForAdmin() throws Exception {
        // given
        long id = hiddenCredit();

        // when / then
        mockMvc.perform(get("/api/credits/" + id).with(USER)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/credits/" + id).with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client.name").value("Ghost Ltd"));
    }

    @Test
    void list_creditOfArchivedClient_isHiddenFromUserSearchesAndShownToAdminWithIncludeArchived() throws Exception {
        // given
        long id = hiddenCredit();
        String number = creditRepository.findById(id).orElseThrow().getCreditNumber();

        // when / then: not returned by plain list or by any search
        mockMvc.perform(get("/api/credits").with(USER))
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(0));
        for (String term : new String[] {number, "ghost", "bg-123"}) {
            mockMvc.perform(get("/api/credits?search=" + term).with(USER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0));
        }
        mockMvc.perform(get("/api/credits").with(ADMIN)).andExpect(jsonPath("$.content.length()").value(0));

        // and: ADMIN with includeArchived sees it
        mockMvc.perform(get("/api/credits?includeArchived=true").with(ADMIN))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void create_archivedClientAsAdmin_returnsSame404BodyAsMissingClient() throws Exception {
        // given
        long clientId = createClient("Acme");
        mockMvc.perform(delete("/api/clients/" + clientId).with(ADMIN)).andExpect(status().isNoContent());

        // when
        String archivedBody = mockMvc.perform(post("/api/credits").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content(creditBody(clientId, 12, "1000")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Client " + clientId + " was not found."))
                .andReturn().getResponse().getContentAsString();
        String missingBody = mockMvc.perform(post("/api/credits").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content(creditBody(clientId + 1000, 12, "1000")))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // then: nothing in the body reveals that the client exists
        assertThat(archivedBody.replace(String.valueOf(clientId), "X"))
                .isEqualTo(missingBody.replace(String.valueOf(clientId + 1000), "X"));
        assertThat(creditRepository.count()).isZero();
    }

    // ---- date bounds ----

    @Test
    void calculate_datesOutOfBounds_return400PerField() throws Exception {
        // when / then
        mockMvc.perform(post("/api/credits/calculate").with(USER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000,\"currency\":\"EUR\",\"interestRate\":5,"
                                + "\"startDate\":\"1899-12-31\",\"endDate\":\"2201-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.startDate").value("Start date must be on or after 1900-01-01."))
                .andExpect(jsonPath("$.errors.endDate").value("End date must be on or before 2200-12-31."));
    }

    @Test
    void calculate_boundaryDates_areAccepted() throws Exception {
        // when / then
        mockMvc.perform(post("/api/credits/calculate").with(USER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000,\"currency\":\"EUR\",\"interestRate\":5,"
                                + "\"startDate\":\"1900-01-01\",\"endDate\":\"1900-07-01\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/credits/calculate").with(USER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000,\"currency\":\"EUR\",\"interestRate\":5,"
                                + "\"startDate\":\"2200-06-30\",\"endDate\":\"2200-12-30\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void create_absurdDates_return400NotServerError() throws Exception {
        // given
        long clientId = createClient("Acme");

        // when / then
        for (String[] dates : new String[][] {{"+999999-01-01", "+999999-06-01"}, {"0001-01-01", "0001-06-01"},
                {"-999999999-01-01", "+999999999-12-31"}}) {
            mockMvc.perform(post("/api/credits").with(USER).contentType(MediaType.APPLICATION_JSON)
                            .content(creditBody(clientId, 6, "1000")
                                    .replace("2026-01-15", dates[0]).replaceAll("20\\d\\d-\\d\\d-15", dates[1])))
                    .andExpect(status().isBadRequest());
        }
        assertThat(creditRepository.count()).isZero();
    }

    @Test
    void create_startBeforeMinimum_returns400OnStartDate() throws Exception {
        // given
        long clientId = createClient("Acme");

        // when / then
        mockMvc.perform(post("/api/credits").with(USER).contentType(MediaType.APPLICATION_JSON)
                        .content(creditBody(clientId, 6, "1000").replace("2026-01-15", "1899-12-31")
                                .replaceAll("2026-07-15", "1900-06-30")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.startDate").value("Start date must be on or after 1900-01-01."));
    }

    // ---- money precision and scale ----

    @Test
    void create_amountWithElevenIntegerDigits_returns400() throws Exception {
        // given
        long clientId = createClient("Acme");

        // when / then
        mockMvc.perform(post("/api/credits").with(USER).contentType(MediaType.APPLICATION_JSON)
                        .content(creditBody(clientId, 6, "10000000000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
        assertThat(creditRepository.count()).isZero();
    }

    @Test
    void createAndUpdateAndGet_responses_useSameScaleForMoneyAndRate() throws Exception {
        // given
        long clientId = createClient("Acme");
        String body = creditBody(clientId, 12, "5000").replace("\"interestRate\":12", "\"interestRate\":5.25")
                .replace("\"hasCollateral\":false",
                        "\"hasCollateral\":true,\"collateralValue\":7000,\"collateralDescription\":\"Flat\"");

        // when: create
        String location = mockMvc.perform(post("/api/credits").with(USER).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(5000.00))
                .andReturn().getResponse().getHeader("Location");
        long id = idFrom(location, "/api/credits");
        String created = mockMvc.perform(get("/api/credits/" + id).with(USER)).andReturn().getResponse()
                .getContentAsString();

        // then: the raw JSON of POST and PUT shows 2 and 4 decimals, like GET
        String postBody = mockMvc.perform(put("/api/credits/" + id).with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (String json : new String[] {created, postBody}) {
            assertThat(json).contains("\"amount\":5000.00", "\"interestRate\":5.2500",
                    "\"collateralValue\":7000.00");
        }
        String freshPost = mockMvc.perform(post("/api/credits").with(USER).contentType(MediaType.APPLICATION_JSON)
                        .content(body)).andReturn().getResponse().getContentAsString();
        assertThat(freshPost).contains("\"amount\":5000.00", "\"interestRate\":5.2500",
                "\"collateralValue\":7000.00");
    }
}
