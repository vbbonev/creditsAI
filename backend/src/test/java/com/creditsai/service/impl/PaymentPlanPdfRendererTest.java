package com.creditsai.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.creditsai.dto.ClientSummary;
import com.creditsai.dto.CreditResponse;
import com.creditsai.dto.PaymentPlanPdf;
import com.creditsai.mapper.CreditMapper;
import com.creditsai.model.CreditCurrency;
import com.creditsai.model.CreditStatus;
import com.creditsai.model.CreditType;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

class PaymentPlanPdfRendererTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-29T10:30:00Z"), ZoneId.of("UTC"));
    private final PaymentPlanPdfRenderer renderer = new PaymentPlanPdfRenderer(CLOCK);

    private static CreditResponse credit(int months, CreditCurrency currency, boolean collateral, boolean archived) {
        LocalDate start = LocalDate.of(2026, 1, 15);
        PaymentPlan plan = PaymentPlanCalculator.calculate(new BigDecimal("10000.00"), new BigDecimal("5.2500"),
                start, start.plusMonths(months));
        CreditMapper mapper = Mappers.getMapper(CreditMapper.class);
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        return new CreditResponse(1L, "CR-2026-000123", new ClientSummary(1L, "Иван Петров ЕООД", "BG123456789"),
                CreditType.MORTGAGE, new BigDecimal("10000.00"), currency, new BigDecimal("5.2500"), start,
                start.plusMonths(months), collateral, collateral ? new BigDecimal("25000.00") : null,
                collateral ? "Апартамент в София" : null, CreditStatus.ACTIVE, archived, archived ? now : null, now,
                now, mapper.toPlanResponse(plan));
    }

    private static String[] pages(PaymentPlanPdf pdf) throws IOException {
        PdfReader reader = new PdfReader(pdf.content());
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        String[] texts = new String[reader.getNumberOfPages()];
        for (int i = 0; i < texts.length; i++) {
            texts[i] = extractor.getTextFromPage(i + 1);
        }
        reader.close();
        return texts;
    }

    @Test
    void render_shortPlan_containsHeaderTotalsAndFormattedValues() throws IOException {
        // given
        CreditResponse credit = credit(12, CreditCurrency.EUR, true, false);

        // when
        PaymentPlanPdf pdf = renderer.render(credit);

        // then
        assertThat(pdf.fileName()).isEqualTo("CR-2026-000123-payment-plan.pdf");
        String[] pages = pages(pdf);
        assertThat(pages).hasSize(1);
        String text = pages[0];
        assertThat(text).contains("Payment plan", "CR-2026-000123", "Иван Петров ЕООД", "BG123456789",
                "€10,000.00", "5.25 %", "15.01.2026", "15.01.2027", "12 months", "€25,000.00",
                "Апартамент в София", "Monthly payment", "Total interest", "Total paid", "Remaining balance",
                "15.02.2026", "Generated 29.10.2026 10:30", "Page 1 of 1");
        assertThat(text).doesNotContain("Archived");
    }

    @Test
    void render_noCollateralArchivedUsd_showsNoArchivedAndDollars() throws IOException {
        // when
        String text = pages(renderer.render(credit(12, CreditCurrency.USD, false, true)))[0];

        // then
        assertThat(text).contains("Collateral No", "$10,000.00", "Archived");
    }

    @Test
    void render_chf_usesCodePrefix() throws IOException {
        // when
        String text = pages(renderer.render(credit(12, CreditCurrency.CHF, false, false)))[0];

        // then
        assertThat(text).contains("CHF 10,000.00");
    }

    @Test
    void render_480MonthPlan_spansPagesRepeatsHeaderAndNumbersPages() throws IOException {
        // when
        String[] pages = pages(renderer.render(credit(480, CreditCurrency.EUR, false, false)));

        // then
        assertThat(pages.length).isBetween(5, 20);
        for (int i = 0; i < pages.length; i++) {
            assertThat(pages[i]).contains("Remaining balance", "Page " + (i + 1) + " of " + pages.length,
                    "Generated 29.10.2026 10:30");
        }
        assertThat(pages[pages.length - 1]).contains("480");
    }

    @Test
    void rate_variousScales_keepsAtLeastTwoDecimalsWithoutTrailingZeros() {
        // when / then
        assertThat(PaymentPlanPdfRenderer.rate(new BigDecimal("5.2500"))).isEqualTo("5.25 %");
        assertThat(PaymentPlanPdfRenderer.rate(new BigDecimal("12"))).isEqualTo("12.00 %");
        assertThat(PaymentPlanPdfRenderer.rate(new BigDecimal("0.0000"))).isEqualTo("0.00 %");
        assertThat(PaymentPlanPdfRenderer.rate(new BigDecimal("5.125"))).isEqualTo("5.125 %");
        assertThat(PaymentPlanPdfRenderer.rate(new BigDecimal("100"))).isEqualTo("100.00 %");
    }
}
