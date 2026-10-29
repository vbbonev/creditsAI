package com.creditsai.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import com.creditsai.exception.UnsupportedFileTypeException;
import com.creditsai.dto.AttachmentUpload;
import com.creditsai.config.CreditProperties;
import com.creditsai.dto.ClientSummary;
import com.creditsai.dto.CreditCalculationRequest;
import com.creditsai.dto.CreditRequest;
import com.creditsai.dto.CreditResponse;
import com.creditsai.dto.CreditSummaryResponse;
import com.creditsai.dto.PaymentPlanResponse;
import com.creditsai.dto.PaymentRowResponse;
import com.creditsai.exception.BusinessRuleException;
import com.creditsai.exception.ResourceNotFoundException;
import com.creditsai.model.CreditCurrency;
import com.creditsai.model.CreditStatus;
import com.creditsai.model.CreditType;
import com.creditsai.security.SecurityConfig;
import com.creditsai.service.CreditService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(CreditController.class)
@Import({SecurityConfig.class, CreditControllerTest.PropertiesConfig.class})
class CreditControllerTest {

    @TestConfiguration
    @EnableConfigurationProperties(CreditProperties.class)
    static class PropertiesConfig {
    }

    private static final String VALID_BODY = """
            {"clientId":1,"type":"CONSUMER","amount":10000,"currency":"EUR","interestRate":12,
             "startDate":"2026-01-15","endDate":"2027-01-15","hasCollateral":true,
             "collateralValue":500,"collateralDescription":"Car"}""";

    private static final String CALC_BODY = """
            {"amount":10000,"currency":"EUR","interestRate":12,"startDate":"2026-01-15","endDate":"2027-01-15"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreditService creditService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static RequestPostProcessor user() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static PaymentPlanResponse plan() {
        return new PaymentPlanResponse(1, new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("10.00"),
                List.of(new PaymentRowResponse(1, LocalDate.of(2026, 2, 15), new BigDecimal("10.00"),
                        new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO)));
    }

    private static CreditResponse response(long id, boolean archived) {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        return new CreditResponse(id, "CR-2026-000001", new ClientSummary(1L, "Acme", "123"), CreditType.CONSUMER,
                new BigDecimal("10000.00"), CreditCurrency.EUR, new BigDecimal("12"), LocalDate.of(2026, 1, 15),
                LocalDate.of(2027, 1, 15), false, null, null, CreditStatus.ACTIVE, archived,
                archived ? now : null, now, now, plan());
    }

    private static CreditSummaryResponse summary(long id) {
        return new CreditSummaryResponse(id, "CR-2026-000001", new ClientSummary(1L, "Acme", "123"),
                CreditType.CONSUMER, new BigDecimal("10000.00"), CreditCurrency.EUR, new BigDecimal("12"),
                LocalDate.of(2026, 1, 15), LocalDate.of(2027, 1, 15), 12, new BigDecimal("888.49"),
                CreditStatus.ACTIVE, false, Instant.parse("2026-01-01T10:00:00Z"));
    }

    // ---- 401 ----

    @Test
    void endpoints_withoutToken_return401() throws Exception {
        // when / then
        mockMvc.perform(post("/api/credits/calculate").contentType(MediaType.APPLICATION_JSON).content(CALC_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/credits").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/credits")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/credits/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/credits/1").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/credits/1/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVE\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/credits/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/credits/1/restore")).andExpect(status().isUnauthorized());
        verifyNoInteractions(creditService);
    }

    // ---- calculate ----

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_USER", "ROLE_ADMIN"})
    void calculate_validRequest_returns200WithPlan(String role) throws Exception {
        // given
        given(creditService.calculate(any(CreditCalculationRequest.class))).willReturn(plan());

        // when / then
        mockMvc.perform(post("/api/credits/calculate").with(jwt().authorities(new SimpleGrantedAuthority(role)))
                        .contentType(MediaType.APPLICATION_JSON).content(CALC_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termMonths").value(1))
                .andExpect(jsonPath("$.rows.length()").value(1));
    }

    @Test
    void calculate_invalidBody_returns400WithFieldErrors() throws Exception {
        // when / then
        mockMvc.perform(post("/api/credits/calculate").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":-1,\"currency\":\"EUR\",\"interestRate\":101,"
                                + "\"startDate\":\"2026-01-15\",\"endDate\":\"2026-02-20\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").value("Amount must be greater than zero."))
                .andExpect(jsonPath("$.errors.interestRate").value("Interest rate must be between 0 and 100 percent."))
                .andExpect(jsonPath("$.errors.endDate").exists());
        verifyNoInteractions(creditService);
    }

    // ---- create ----

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_USER", "ROLE_ADMIN"})
    void create_validRequest_returns201WithLocationAndBody(String role) throws Exception {
        // given
        given(creditService.create(any(CreditRequest.class))).willReturn(response(5, false));

        // when / then
        mockMvc.perform(post("/api/credits").with(jwt().authorities(new SimpleGrantedAuthority(role)))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/credits/5"))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.creditNumber").value("CR-2026-000001"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.plan.rows.length()").value(1));
    }

    @Test
    void create_clientSentServerFields_areIgnored() throws Exception {
        // given
        given(creditService.create(any(CreditRequest.class))).willReturn(response(5, false));
        String body = VALID_BODY.replace("{\"clientId\":1,",
                "{\"clientId\":1,\"creditNumber\":\"HACK\",\"status\":\"INACTIVE\",\"archived\":true,"
                        + "\"monthlyPayment\":1,\"plan\":{\"rows\":[]},");

        // when
        mockMvc.perform(post("/api/credits").with(user()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        // then: the request record has no such components, so nothing reaches the service
        ArgumentCaptor<CreditRequest> captor = ArgumentCaptor.forClass(CreditRequest.class);
        verify(creditService).create(captor.capture());
        assertThat(captor.getValue().clientId()).isEqualTo(1L);
        assertThat(CreditRequest.class.getRecordComponents()).extracting(c -> c.getName())
                .doesNotContain("creditNumber", "status", "archived", "monthlyPayment", "plan");
    }

    @Test
    void create_emptyBody_returns400WithRequiredFieldMessages() throws Exception {
        // when / then
        mockMvc.perform(post("/api/credits").with(user()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.clientId").value("Client is required."))
                .andExpect(jsonPath("$.errors.type").value("Type is required."))
                .andExpect(jsonPath("$.errors.amount").value("Amount is required."))
                .andExpect(jsonPath("$.errors.currency").value("Currency is required."))
                .andExpect(jsonPath("$.errors.interestRate").value("Interest rate is required."))
                .andExpect(jsonPath("$.errors.startDate").value("Start date is required."))
                .andExpect(jsonPath("$.errors.endDate").value("End date is required."))
                .andExpect(jsonPath("$.errors.hasCollateral").value("Collateral flag is required."));
        verifyNoInteractions(creditService);
    }

    @Test
    void create_amountAboveLimit_returns400OnAmount() throws Exception {
        // when / then
        mockMvc.perform(post("/api/credits").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"amount\":10000", "\"amount\":100000001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").value("Amount must be at most 100000000."));
    }

    @Test
    void create_rateAboveLimit_returns400OnInterestRate() throws Exception {
        // when / then
        mockMvc.perform(post("/api/credits").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"interestRate\":12", "\"interestRate\":100.5")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.interestRate").value("Interest rate must be between 0 and 100 percent."));
    }

    @Test
    void create_termOver480Months_returns400OnEndDate() throws Exception {
        // when / then
        mockMvc.perform(post("/api/credits").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("2027-01-15", "2066-02-15")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.endDate").value("The term must be between 1 and 480 months."));
    }

    @Test
    void create_endDateNotWholeMonths_returns400OnEndDate() throws Exception {
        // when / then
        mockMvc.perform(post("/api/credits").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("2027-01-15", "2027-01-16")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.endDate").exists());
    }

    @Test
    void create_collateralFlagWithoutValue_returns400OnCollateralFields() throws Exception {
        // when / then
        mockMvc.perform(post("/api/credits").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replaceAll(",\\s*\"collateralValue\":500,\"collateralDescription\":\"Car\"", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.collateralValue")
                        .value("Collateral value is required when the credit has collateral."))
                .andExpect(jsonPath("$.errors.collateralDescription")
                        .value("Collateral description is required when the credit has collateral."));
    }

    @Test
    void create_collateralGivenWithoutFlag_returns400OnCollateralFields() throws Exception {
        // when / then
        mockMvc.perform(post("/api/credits").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"hasCollateral\":true", "\"hasCollateral\":false")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.collateralValue")
                        .value("Collateral value must be empty when the credit has no collateral."))
                .andExpect(jsonPath("$.errors.collateralDescription")
                        .value("Collateral description must be empty when the credit has no collateral."));
    }

    @Test
    void create_unknownEnumValue_returns400() throws Exception {
        // when / then
        mockMvc.perform(post("/api/credits").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("CONSUMER", "NOPE")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(creditService);
    }

    @Test
    void create_unknownClient_returns404() throws Exception {
        // given
        given(creditService.create(any())).willThrow(new ResourceNotFoundException("Client 1 was not found."));

        // when / then
        mockMvc.perform(post("/api/credits").with(user()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Client 1 was not found."));
    }

    @Test
    void create_archivedClient_returns409() throws Exception {
        // given
        given(creditService.create(any())).willThrow(new BusinessRuleException("The client is archived."));

        // when / then
        mockMvc.perform(post("/api/credits").with(user()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The client is archived."));
    }

    // ---- list ----

    @Test
    void list_asUser_returnsStablePageShapeWithDefaults() throws Exception {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        given(creditService.list(any(), any(), anyBoolean(), any()))
                .willReturn(new PageImpl<>(List.of(summary(1)), pageable, 1));

        // when
        mockMvc.perform(get("/api/credits").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].creditNumber").value("CR-2026-000001"))
                .andExpect(jsonPath("$.content[0].client.name").value("Acme"))
                .andExpect(jsonPath("$.content[0].plan").doesNotExist())
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        // then
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(creditService).list(isNull(), isNull(), eq(false), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        assertThat(captor.getValue().getSort().getOrderFor("creditNumber")).isNotNull();
    }

    @Test
    void list_searchAndStatus_arePassedToService() throws Exception {
        // given
        given(creditService.list(any(), any(), anyBoolean(), any()))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        // when
        mockMvc.perform(get("/api/credits?search=acme&status=INACTIVE").with(user())).andExpect(status().isOk());

        // then
        verify(creditService).list(eq("acme"), eq(CreditStatus.INACTIVE), eq(false), any());
    }

    @Test
    void list_unknownStatus_returns400() throws Exception {
        // when / then
        mockMvc.perform(get("/api/credits?status=BOGUS").with(user())).andExpect(status().isBadRequest());
    }

    @Test
    void list_includeArchivedAsUser_returns403() throws Exception {
        // when / then
        mockMvc.perform(get("/api/credits?includeArchived=true").with(user())).andExpect(status().isForbidden());
        verifyNoInteractions(creditService);
    }

    @Test
    void list_includeArchivedAsAdmin_returns200AndPassesFlag() throws Exception {
        // given
        given(creditService.list(any(), any(), anyBoolean(), any()))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        // when
        mockMvc.perform(get("/api/credits?includeArchived=true").with(admin())).andExpect(status().isOk());

        // then
        verify(creditService).list(isNull(), isNull(), eq(true), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"creditNumber", "startDate", "endDate", "amount", "createdAt"})
    void list_sortByAllowedField_passesSortToService(String field) throws Exception {
        // given
        given(creditService.list(any(), any(), anyBoolean(), any()))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        // when
        mockMvc.perform(get("/api/credits?sort=" + field + ",desc").with(user())).andExpect(status().isOk());

        // then
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(creditService).list(any(), any(), anyBoolean(), captor.capture());
        assertThat(captor.getValue().getSort().getOrderFor(field).isDescending()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"client", "monthlyPayment", "status", "password", "client.name"})
    void list_sortByFieldNotAllowed_returns400WithMessage(String field) throws Exception {
        // when / then
        mockMvc.perform(get("/api/credits?sort=" + field).with(user()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Sorting by '" + field
                        + "' is not supported. Allowed fields: creditNumber, startDate, endDate, amount, createdAt."));
        verifyNoInteractions(creditService);
    }

    @Test
    void list_sizeAboveMax_isClampedTo100() throws Exception {
        // given
        given(creditService.list(any(), any(), anyBoolean(), any()))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        // when
        mockMvc.perform(get("/api/credits?size=100000").with(user())).andExpect(status().isOk());

        // then
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(creditService).list(any(), any(), anyBoolean(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    // ---- get ----

    @Test
    void get_asUser_passesNonAdminFlag() throws Exception {
        // given
        given(creditService.get(5L, false)).willReturn(response(5, false));

        // when / then
        mockMvc.perform(get("/api/credits/5").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.plan.rows[0].number").value(1));
    }

    @Test
    void get_asAdmin_passesAdminFlag() throws Exception {
        // given
        given(creditService.get(5L, true)).willReturn(response(5, true));

        // when / then
        mockMvc.perform(get("/api/credits/5").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
    }

    @Test
    void get_missing_returns404() throws Exception {
        // given
        given(creditService.get(eq(9L), anyBoolean())).willThrow(new ResourceNotFoundException("Credit 9 was not found."));

        // when / then
        mockMvc.perform(get("/api/credits/9").with(user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Credit 9 was not found."));
    }

    @Test
    void get_nonNumericId_returns400() throws Exception {
        // when / then
        mockMvc.perform(get("/api/credits/abc").with(user())).andExpect(status().isBadRequest());
    }

    // ---- update ----

    @Test
    void update_asUser_returns403() throws Exception {
        // when / then
        mockMvc.perform(put("/api/credits/5").with(user()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(creditService);
    }

    @Test
    void update_asAdmin_returns200() throws Exception {
        // given
        given(creditService.update(eq(5L), any(CreditRequest.class))).willReturn(response(5, false));

        // when / then
        mockMvc.perform(put("/api/credits/5").with(admin()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void update_invalidBody_returns400() throws Exception {
        // when / then
        mockMvc.perform(put("/api/credits/5").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.clientId").value("Client is required."));
        verifyNoInteractions(creditService);
    }

    @Test
    void update_missing_returns404() throws Exception {
        // given
        given(creditService.update(eq(9L), any())).willThrow(new ResourceNotFoundException("Credit 9 was not found."));

        // when / then
        mockMvc.perform(put("/api/credits/9").with(admin()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    // ---- status ----

    @Test
    void changeStatus_asUser_returns403() throws Exception {
        // when / then
        mockMvc.perform(post("/api/credits/5/status").with(user()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVE\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(creditService);
    }

    @Test
    void changeStatus_asAdmin_returns200() throws Exception {
        // given
        given(creditService.changeStatus(5L, CreditStatus.INACTIVE)).willReturn(response(5, false));

        // when / then
        mockMvc.perform(post("/api/credits/5/status").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVE\"}")).andExpect(status().isOk());
    }

    @Test
    void changeStatus_missingStatus_returns400WithFieldError() throws Exception {
        // when / then
        mockMvc.perform(post("/api/credits/5/status").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").value("Status is required."));
    }

    @Test
    void changeStatus_unknownStatus_returns400() throws Exception {
        // when / then
        mockMvc.perform(post("/api/credits/5/status").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"BOGUS\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void changeStatus_activateArchived_returns409() throws Exception {
        // given
        given(creditService.changeStatus(5L, CreditStatus.ACTIVE))
                .willThrow(new BusinessRuleException("An archived credit cannot be activated. Restore it first."));

        // when / then
        mockMvc.perform(post("/api/credits/5/status").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An archived credit cannot be activated. Restore it first."));
    }

    // ---- archive / restore ----

    @Test
    void archive_asUser_returns403() throws Exception {
        // when / then
        mockMvc.perform(delete("/api/credits/5").with(user())).andExpect(status().isForbidden());
        verifyNoInteractions(creditService);
    }

    @Test
    void archive_asAdmin_returns204() throws Exception {
        // when / then
        mockMvc.perform(delete("/api/credits/5").with(admin())).andExpect(status().isNoContent());
        verify(creditService).archive(5L);
    }

    @Test
    void archive_activeCredit_returns409WithReadableMessage() throws Exception {
        // given
        doThrow(new BusinessRuleException("An active credit cannot be archived. Deactivate it first."))
                .when(creditService).archive(5L);

        // when / then
        mockMvc.perform(delete("/api/credits/5").with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An active credit cannot be archived. Deactivate it first."));
    }

    @Test
    void archive_missing_returns404() throws Exception {
        // given
        doThrow(new ResourceNotFoundException("Credit 9 was not found.")).when(creditService).archive(9L);

        // when / then
        mockMvc.perform(delete("/api/credits/9").with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void restore_asUser_returns403() throws Exception {
        // when / then
        mockMvc.perform(post("/api/credits/5/restore").with(user())).andExpect(status().isForbidden());
        verifyNoInteractions(creditService);
    }

    @Test
    void restore_asAdmin_returns200() throws Exception {
        // given
        given(creditService.restore(5L)).willReturn(response(5, false));

        // when / then
        mockMvc.perform(post("/api/credits/5/restore").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void restore_notArchived_returns409() throws Exception {
        // given
        given(creditService.restore(5L)).willThrow(new BusinessRuleException("This credit is not archived."));

        // when / then
        mockMvc.perform(post("/api/credits/5/restore").with(admin())).andExpect(status().isConflict());
    }

    @Test
    void calculate_datesOutOfBounds_returns400WithFieldErrors() throws Exception {
        // when / then
        mockMvc.perform(post("/api/credits/calculate").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000,\"currency\":\"EUR\",\"interestRate\":5,"
                                + "\"startDate\":\"1899-12-31\",\"endDate\":\"2201-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.startDate").value("Start date must be on or after 1900-01-01."))
                .andExpect(jsonPath("$.errors.endDate").value("End date must be on or before 2200-12-31."));
        verifyNoInteractions(creditService);
    }

    @Test
    void create_dateBoundaries_areInclusiveAndOneDayOutsideIsRejected() throws Exception {
        // given
        given(creditService.create(any(CreditRequest.class))).willReturn(response(5, false));
        String base = VALID_BODY.replace("2026-01-15", "1900-01-01").replace("2027-01-15", "1900-07-01");

        // when / then
        mockMvc.perform(post("/api/credits").with(user()).contentType(MediaType.APPLICATION_JSON).content(base))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/credits").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(base.replace("1900-01-01", "1899-12-31")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.startDate").exists());
        mockMvc.perform(post("/api/credits").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("2026-01-15", "2200-06-30").replace("2027-01-15", "2200-12-30")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/credits").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("2026-01-15", "2200-06-30").replace("2027-01-15", "2201-01-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.endDate").value("End date must be on or before 2200-12-31."));
    }

    @Test
    void create_absurdYear_returns400() throws Exception {
        // when / then
        mockMvc.perform(post("/api/credits").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("2027-01-15", "+999999-01-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.endDate").exists());
        mockMvc.perform(post("/api/credits").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("2026-01-15", "0001-01-01").replace("2027-01-15", "0001-07-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.startDate").exists());
        verifyNoInteractions(creditService);
    }

    @Test
    void create_amountWithElevenIntegerDigits_returns400() throws Exception {
        // when / then
        mockMvc.perform(post("/api/credits").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"amount\":10000", "\"amount\":10000000000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
    }

    @Test
    void create_collateralValueWithElevenIntegerDigits_returns400() throws Exception {
        // when / then
        mockMvc.perform(post("/api/credits").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"collateralValue\":500", "\"collateralValue\":10000000000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.collateralValue")
                        .value("Collateral value may have at most 10 digits and 2 decimals."));
    }

    // ---- multipart create (credit JSON part + files) ----

    private static MockMultipartFile creditPart(String json) {
        return new MockMultipartFile("credit", "", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile file(String name) {
        return new MockMultipartFile("files", name, "application/pdf", "data".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void createWithFiles_withoutToken_returns401() throws Exception {
        mockMvc.perform(multipart("/api/credits").file(creditPart(VALID_BODY))).andExpect(status().isUnauthorized());
        verifyNoInteractions(creditService);
    }

    @Test
    void createWithFiles_asUser_returns201AndPassesFilesAndUsername() throws Exception {
        // given
        given(creditService.create(any(CreditRequest.class), any(), eq("user1"))).willReturn(response(5, false));

        // when / then
        mockMvc.perform(multipart("/api/credits").file(creditPart(VALID_BODY)).file(file("a.pdf")).file(file("b.pdf"))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
                                .jwt(j -> j.claim("preferred_username", "user1"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/credits/5")))
                .andExpect(jsonPath("$.id").value(5));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AttachmentUpload>> captor = ArgumentCaptor.forClass(List.class);
        verify(creditService).create(any(CreditRequest.class), captor.capture(), eq("user1"));
        org.assertj.core.api.Assertions.assertThat(captor.getValue())
                .extracting(AttachmentUpload::fileName).containsExactly("a.pdf", "b.pdf");
    }

    @Test
    void createWithFiles_asAdminWithoutFiles_returns201() throws Exception {
        // given
        given(creditService.create(any(CreditRequest.class), eq(List.of()), any())).willReturn(response(5, false));

        // when / then
        mockMvc.perform(multipart("/api/credits").file(creditPart(VALID_BODY)).with(admin()))
                .andExpect(status().isCreated());
    }

    @Test
    void createWithFiles_invalidCreditPart_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(multipart("/api/credits")
                        .file(creditPart(VALID_BODY.replace("\"amount\":10000", "\"amount\":-5"))).with(user()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
        verifyNoInteractions(creditService);
    }

    @Test
    void createWithFiles_missingCreditPart_returns400() throws Exception {
        mockMvc.perform(multipart("/api/credits").file(file("a.pdf")).with(user()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithFiles_rejectedFile_returnsReadableProblem() throws Exception {
        // given
        given(creditService.create(any(CreditRequest.class), any(), any()))
                .willThrow(new UnsupportedFileTypeException("File 'a.pdf' has an unsupported type."))
                .willThrow(new BusinessRuleException("A credit can have at most 30 files."));

        // when / then
        mockMvc.perform(multipart("/api/credits").file(creditPart(VALID_BODY)).file(file("a.pdf")).with(user()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail").value("File 'a.pdf' has an unsupported type."));
        mockMvc.perform(multipart("/api/credits").file(creditPart(VALID_BODY)).file(file("a.pdf")).with(user()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A credit can have at most 30 files."));
    }

    @Test
    void create_jsonVariant_stillWorksAsBefore() throws Exception {
        // given
        given(creditService.create(any(CreditRequest.class))).willReturn(response(5, false));

        // when / then
        mockMvc.perform(post("/api/credits").with(user()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated());
        verify(creditService).create(any(CreditRequest.class));
    }
}
