package com.creditsai.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.creditsai.config.CreditProperties;
import com.creditsai.dto.CreditCalculationRequest;
import com.creditsai.dto.CreditRequest;
import com.creditsai.model.CreditCurrency;
import com.creditsai.model.CreditType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Runs the real Bean Validation engine with the validators built from default CreditProperties. */
class CreditValidatorsTest {

    private static final CreditProperties PROPERTIES = new CreditProperties(
            new BigDecimal("100000000"), new BigDecimal("100"), 1, 480,
            java.time.LocalDate.of(1900, 1, 1), java.time.LocalDate.of(2200, 12, 31));

    private static final Validator VALIDATOR = Validation.byDefaultProvider().configure()
            .constraintValidatorFactory(new ConstraintValidatorFactory() {
                @Override
                @SuppressWarnings("unchecked")
                public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
                    if (key == CreditTermsValidator.class) {
                        return (T) new CreditTermsValidator(PROPERTIES);
                    }
                    try {
                        return key.getDeclaredConstructor().newInstance();
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }
                }

                @Override
                public void releaseInstance(ConstraintValidator<?, ?> instance) {
                }
            })
            .buildValidatorFactory().getValidator();

    private static CreditRequest request(String amount, String rate, LocalDate start, LocalDate end,
            Boolean hasCollateral, String collateralValue, String collateralDescription) {
        return new CreditRequest(1L, CreditType.CONSUMER, amount == null ? null : new BigDecimal(amount),
                CreditCurrency.EUR, rate == null ? null : new BigDecimal(rate), start, end, hasCollateral,
                collateralValue == null ? null : new BigDecimal(collateralValue), collateralDescription);
    }

    private static CreditRequest terms(String amount, String rate, LocalDate start, LocalDate end) {
        return request(amount, rate, start, end, false, null, null);
    }

    private static Map<String, String> errors(Object target) {
        Set<ConstraintViolation<Object>> violations = VALIDATOR.validate(target);
        return violations.stream().collect(Collectors.toMap(v -> v.getPropertyPath().toString(),
                ConstraintViolation::getMessage, (a, b) -> a));
    }

    private static final LocalDate START = LocalDate.of(2026, 1, 15);

    // ---- terms ----

    @Test
    void terms_validValues_haveNoErrors() {
        assertThat(errors(terms("1000", "5", START, START.plusMonths(12)))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"100000000", "0.01"})
    void terms_amountAtLimits_isValid(String amount) {
        assertThat(errors(terms(amount, "5", START, START.plusMonths(12)))).isEmpty();
    }

    @Test
    void terms_amountAboveMax_failsOnAmount() {
        assertThat(errors(terms("100000000.01", "5", START, START.plusMonths(12))))
                .containsKey("amount").hasSize(1);
        assertThat(errors(terms("100000001", "5", START, START.plusMonths(12))).get("amount"))
                .isEqualTo("Amount must be at most 100000000.");
    }

    @ParameterizedTest
    @CsvSource({"0", "-5"})
    void terms_nonPositiveAmount_failsOnAmount(String amount) {
        assertThat(errors(terms(amount, "5", START, START.plusMonths(12))))
                .containsEntry("amount", "Amount must be greater than zero.");
    }

    @ParameterizedTest
    @CsvSource({"0", "100", "99.9999"})
    void terms_rateWithinRange_isValid(String rate) {
        assertThat(errors(terms("1000", rate, START, START.plusMonths(12)))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"-0.0001", "100.0001"})
    void terms_rateOutOfRange_failsOnInterestRate(String rate) {
        assertThat(errors(terms("1000", rate, START, START.plusMonths(12))))
                .containsEntry("interestRate", "Interest rate must be between 0 and 100 percent.");
    }

    @Test
    void terms_rateWithFiveDecimals_failsOnInterestRate() {
        assertThat(errors(terms("1000", "5.12345", START, START.plusMonths(12))))
                .containsEntry("interestRate", "Interest rate may have at most 4 decimals.");
    }

    @Test
    void terms_endBeforeOrEqualStart_failsOnEndDate() {
        assertThat(errors(terms("1000", "5", START, START))).containsEntry("endDate", "End date must be after the start date.");
        assertThat(errors(terms("1000", "5", START, START.minusMonths(1))))
                .containsEntry("endDate", "End date must be after the start date.");
    }

    @Test
    void terms_notWholeMonths_failsOnEndDate() {
        assertThat(errors(terms("1000", "5", START, START.plusMonths(3).plusDays(1))).get("endDate"))
                .contains("whole number of months");
    }

    @Test
    void terms_termOf480Months_isValidAnd481IsNot() {
        assertThat(errors(terms("1000", "5", START, START.plusMonths(480)))).isEmpty();
        assertThat(errors(terms("1000", "5", START, START.plusMonths(481))))
                .containsEntry("endDate", "The term must be between 1 and 480 months.");
    }

    @Test
    void terms_oneMonthTerm_isValid() {
        assertThat(errors(terms("1000", "5", START, START.plusMonths(1)))).isEmpty();
    }

    @Test
    void terms_pastStartDate_isValid() {
        LocalDate past = LocalDate.of(2015, 3, 1);
        assertThat(errors(terms("1000", "5", past, past.plusMonths(24)))).isEmpty();
    }

    @Test
    void terms_monthEndStart_isValid() {
        assertThat(errors(terms("1000", "5", LocalDate.of(2024, 1, 31), LocalDate.of(2024, 2, 29)))).isEmpty();
    }

    @Test
    void terms_calculationRequest_isValidatedToo() {
        // given
        CreditCalculationRequest bad = new CreditCalculationRequest(new BigDecimal("200000000"), CreditCurrency.EUR,
                new BigDecimal("101"), START, START.plusDays(5));

        // when / then
        assertThat(errors(bad)).containsKeys("amount", "interestRate", "endDate");
    }

    @Test
    void terms_nullFields_reportOnlyRequiredErrorsWithoutFailing() {
        // when
        Map<String, String> errors = errors(terms(null, null, null, null));

        // then
        assertThat(errors).containsEntry("amount", "Amount is required.")
                .containsEntry("interestRate", "Interest rate is required.")
                .containsEntry("startDate", "Start date is required.")
                .containsEntry("endDate", "End date is required.");
    }

    // ---- collateral ----

    @Test
    void collateral_withValueAndDescription_isValid() {
        assertThat(errors(request("1000", "5", START, START.plusMonths(12), true, "500", "Car"))).isEmpty();
    }

    @Test
    void collateral_requiredButMissing_failsOnBothFields() {
        assertThat(errors(request("1000", "5", START, START.plusMonths(12), true, null, null)))
                .containsEntry("collateralValue", "Collateral value is required when the credit has collateral.")
                .containsEntry("collateralDescription",
                        "Collateral description is required when the credit has collateral.");
    }

    @Test
    void collateral_blankDescription_failsOnDescription() {
        assertThat(errors(request("1000", "5", START, START.plusMonths(12), true, "500", "   ")))
                .containsOnlyKeys("collateralDescription");
    }

    @Test
    void collateral_notAllowedButGiven_failsOnBothFields() {
        assertThat(errors(request("1000", "5", START, START.plusMonths(12), false, "500", "Car")))
                .containsEntry("collateralValue", "Collateral value must be empty when the credit has no collateral.")
                .containsEntry("collateralDescription",
                        "Collateral description must be empty when the credit has no collateral.");
    }

    @Test
    void collateral_notAllowedAndEmpty_isValid() {
        assertThat(errors(request("1000", "5", START, START.plusMonths(12), false, null, null))).isEmpty();
    }

    @Test
    void collateral_flagMissing_onlyFlagIsReported() {
        assertThat(errors(request("1000", "5", START, START.plusMonths(12), null, "500", "Car")))
                .containsOnlyKeys("hasCollateral");
    }

    @Test
    void collateral_valueZero_failsOnPositive() {
        assertThat(errors(request("1000", "5", START, START.plusMonths(12), true, "0", "Car")))
                .containsEntry("collateralValue", "Collateral value must be greater than zero.");
    }

    @Test
    void collateral_descriptionOver500_failsOnSize() {
        assertThat(errors(request("1000", "5", START, START.plusMonths(12), true, "5", "x".repeat(501))))
                .containsEntry("collateralDescription", "Collateral description must be at most 500 characters.");
    }

    @Test
    void dates_startAtMinimum_isValidAndOneDayBeforeFails() {
        LocalDate min = LocalDate.of(1900, 1, 1);
        assertThat(errors(terms("1000", "5", min, min.plusMonths(12)))).isEmpty();
        assertThat(errors(terms("1000", "5", min.minusDays(1), min.plusMonths(12))))
                .containsOnlyKeys("startDate")
                .containsEntry("startDate", "Start date must be on or after 1900-01-01.");
    }

    @Test
    void dates_endAtMaximum_isValidAndOneDayAfterFails() {
        LocalDate max = LocalDate.of(2200, 12, 31);
        assertThat(errors(terms("1000", "5", max.minusMonths(12), max))).isEmpty();
        assertThat(errors(terms("1000", "5", max.minusMonths(12), max.plusDays(1))))
                .containsOnlyKeys("endDate")
                .containsEntry("endDate", "End date must be on or before 2200-12-31.");
    }

    @Test
    void dates_absurdValues_failOnBothFieldsWithoutThrowing() {
        assertThat(errors(terms("1000", "5", LocalDate.of(1, 1, 1), LocalDate.of(999999, 1, 1))))
                .containsKeys("startDate", "endDate");
        assertThat(errors(terms("1000", "5", LocalDate.MIN, LocalDate.MAX))).containsKeys("startDate", "endDate");
    }

    @Test
    void amount_elevenIntegerDigits_failsOnDigits() {
        assertThat(errors(terms("10000000000", "5", START, START.plusMonths(12))))
                .containsKey("amount");
    }

    @Test
    void amount_tenIntegerDigits_failsOnlyOnMaximum() {
        assertThat(errors(terms("9999999999.99", "5", START, START.plusMonths(12))))
                .containsEntry("amount", "Amount must be at most 100000000.");
    }

    @Test
    void collateralValue_elevenIntegerDigits_failsOnDigits() {
        assertThat(errors(request("1000", "5", START, START.plusMonths(12), true, "10000000000", "Car")))
                .containsEntry("collateralValue", "Collateral value may have at most 10 digits and 2 decimals.");
    }
}
