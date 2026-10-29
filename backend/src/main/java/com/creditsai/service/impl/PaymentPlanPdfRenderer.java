package com.creditsai.service.impl;

import com.creditsai.dto.CreditResponse;
import com.creditsai.dto.PaymentPlanPdf;
import com.creditsai.dto.PaymentPlanResponse;
import com.creditsai.dto.PaymentRowResponse;
import com.creditsai.model.CreditCurrency;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfTemplate;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

/**
 * Renders a credit and its payment plan as an A4 PDF (OpenPDF). It works only on the
 * {@link CreditResponse} it is given, so it needs no database access. Text uses the embedded
 * Liberation Sans fonts with Identity-H encoding, so Cyrillic names are shown correctly. The
 * footer time comes from the injected {@link Clock}.
 */
@Component
@RequiredArgsConstructor
public class PaymentPlanPdfRenderer {

    private static final String FONT_REGULAR = "liberation/LiberationSans-Regular.ttf";
    private static final String FONT_BOLD = "liberation/LiberationSans-Bold.ttf";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final String[] COLUMNS = {"#", "Date", "Payment", "Principal", "Interest", "Remaining balance"};
    private static final float[] COLUMN_WIDTHS = {6f, 14f, 17f, 17f, 17f, 19f};

    private final Clock clock;

    /**
     * Renders the payment plan document of a credit.
     *
     * @param credit the credit, with its payment plan
     * @return the suggested file name and the PDF bytes
     * @throws IllegalStateException if the document cannot be built (a font is missing, for example)
     */
    public PaymentPlanPdf render(CreditResponse credit) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 36, 36, 36, 54);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            Fonts fonts = new Fonts(
                    BaseFont.createFont(FONT_REGULAR, BaseFont.IDENTITY_H, BaseFont.EMBEDDED),
                    BaseFont.createFont(FONT_BOLD, BaseFont.IDENTITY_H, BaseFont.EMBEDDED));
            writer.setPageEvent(new FooterEvent(fonts.regularBase(),LocalDateTime.now(clock).format(TIMESTAMP)));
            document.open();
            addTitle(document, credit, fonts);
            addDetails(document, credit, fonts);
            addTotals(document, credit, fonts);
            addTable(document, credit, fonts);
            document.close();
            return new PaymentPlanPdf(credit.creditNumber() + "-payment-plan.pdf", out.toByteArray());
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Could not generate the payment plan PDF.", e);
        }
    }

    /**
     * Adds the title and the credit number.
     *
     * @param document the open document
     * @param credit the credit
     * @param fonts the fonts
     * @throws DocumentException if the content cannot be added
     */
    private void addTitle(Document document, CreditResponse credit, Fonts fonts) throws DocumentException {
        Paragraph title = new Paragraph("Payment plan", fonts.bold(18));
        document.add(title);
        Paragraph number = new Paragraph(credit.creditNumber(), fonts.regular(12));
        number.setSpacingAfter(10);
        document.add(number);
    }

    /**
     * Adds the credit details as label/value pairs, two pairs per row.
     *
     * @param document the open document
     * @param credit the credit
     * @param fonts the fonts
     * @throws DocumentException if the content cannot be added
     */
    private void addDetails(Document document, CreditResponse credit, Fonts fonts) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[] {20f, 30f, 20f, 30f});
        table.setWidthPercentage(100);
        String status = credit.status().name().charAt(0) + credit.status().name().substring(1).toLowerCase(Locale.ROOT);
        if (credit.archived()) {
            status += " (Archived)";
        }
        String type = credit.type().name().charAt(0) + credit.type().name().substring(1).toLowerCase(Locale.ROOT);
        String[][] pairs = {
                {"Credit number", credit.creditNumber()},
                {"Status", status},
                {"Client", credit.client().name()},
                {"Client identifier", credit.client().identifier()},
                {"Type", type},
                {"Amount", money(credit.amount(), credit.currency())},
                {"Annual interest rate", rate(credit.interestRate())},
                {"Term", credit.plan().termMonths() + " months"},
                {"Start date", date(credit.startDate())},
                {"End date", date(credit.endDate())},
                {"Collateral", collateral(credit)},
                {"", ""}};
        for (String[] pair : pairs) {
            table.addCell(plainCell(pair[0], fonts.bold(9)));
            table.addCell(plainCell(pair[1], fonts.regular(9)));
        }
        table.setSpacingAfter(8);
        document.add(table);
    }

    /**
     * Adds the monthly payment and the totals.
     *
     * @param document the open document
     * @param credit the credit
     * @param fonts the fonts
     * @throws DocumentException if the content cannot be added
     */
    private void addTotals(Document document, CreditResponse credit, Fonts fonts) throws DocumentException {
        PaymentPlanResponse plan = credit.plan();
        CreditCurrency currency = credit.currency();
        PdfPTable table = new PdfPTable(new float[] {20f, 30f, 20f, 30f});
        table.setWidthPercentage(100);
        table.addCell(plainCell("Monthly payment", fonts.bold(10)));
        table.addCell(plainCell(money(plan.monthlyPayment(), currency), fonts.regular(10)));
        table.addCell(plainCell("Total interest", fonts.bold(10)));
        table.addCell(plainCell(money(plan.totalInterest(), currency), fonts.regular(10)));
        table.addCell(plainCell("Total paid", fonts.bold(10)));
        table.addCell(plainCell(money(plan.totalPaid(), currency), fonts.regular(10)));
        table.addCell(plainCell("", fonts.regular(10)));
        table.addCell(plainCell("", fonts.regular(10)));
        table.setSpacingAfter(10);
        document.add(table);
    }

    /**
     * Adds the instalments table; its header row repeats on every page.
     *
     * @param document the open document
     * @param credit the credit
     * @param fonts the fonts
     * @throws DocumentException if the content cannot be added
     */
    private void addTable(Document document, CreditResponse credit, Fonts fonts) throws DocumentException {
        PdfPTable table = new PdfPTable(COLUMN_WIDTHS);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (int i = 0; i < COLUMNS.length; i++) {
            PdfPCell cell = new PdfPCell(new Phrase(COLUMNS[i], fonts.bold(9)));
            cell.setHorizontalAlignment(i == 0 || i == 1 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            cell.setBackgroundColor(new java.awt.Color(230, 230, 230));
            cell.setPadding(4);
            table.addCell(cell);
        }
        Font body = fonts.regular(8.5f);
        for (PaymentRowResponse row : credit.plan().rows()) {
            table.addCell(bodyCell(String.valueOf(row.number()), body, Element.ALIGN_LEFT));
            table.addCell(bodyCell(date(row.date()), body, Element.ALIGN_LEFT));
            table.addCell(bodyCell(money(row.payment(), credit.currency()), body, Element.ALIGN_RIGHT));
            table.addCell(bodyCell(money(row.principal(), credit.currency()), body, Element.ALIGN_RIGHT));
            table.addCell(bodyCell(money(row.interest(), credit.currency()), body, Element.ALIGN_RIGHT));
            table.addCell(bodyCell(money(row.remainingBalance(), credit.currency()), body, Element.ALIGN_RIGHT));
        }
        document.add(table);
    }

    /**
     * Creates a borderless cell.
     *
     * @param text the cell text
     * @param font the font
     * @return the cell
     */
    private PdfPCell plainCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(2);
        return cell;
    }

    /**
     * Creates a table body cell with a thin bottom border.
     *
     * @param text the cell text
     * @param font the font
     * @param alignment an {@link Element} alignment constant
     * @return the cell
     */
    private PdfPCell bodyCell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(new java.awt.Color(210, 210, 210));
        cell.setPadding(3);
        return cell;
    }

    /**
     * Describes the collateral: "No", or its value and description.
     *
     * @param credit the credit
     * @return the text to show
     */
    private String collateral(CreditResponse credit) {
        if (!credit.hasCollateral()) {
            return "No";
        }
        String value = credit.collateralValue() == null ? "" : money(credit.collateralValue(), credit.currency());
        String description = credit.collateralDescription() == null ? "" : credit.collateralDescription();
        return (value + " " + description).trim();
    }

    /**
     * Formats an amount like the UI: symbol or code, en-US grouping and two decimals.
     *
     * @param amount the amount
     * @param currency its currency
     * @return e.g. {@code €10,000.00}, {@code $10,000.00} or {@code CHF 10,000.00}
     */
    static String money(BigDecimal amount, CreditCurrency currency) {
        String prefix = switch (currency) {
            case EUR -> "€";
            case USD -> "$";
            case CHF -> "CHF ";
        };
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return prefix + format.format(amount);
    }

    /**
     * Formats a percentage with at least two decimals and no further trailing zeros.
     *
     * @param rate the annual rate in percent
     * @return e.g. {@code 5.25 %} or {@code 12.00 %}
     */
    static String rate(BigDecimal rate) {
        BigDecimal value = rate.stripTrailingZeros();
        if (value.scale() < 2) {
            value = value.setScale(2);
        }
        return value.toPlainString() + " %";
    }

    /**
     * Formats a date as dd.MM.yyyy.
     *
     * @param date the date
     * @return the text
     */
    static String date(LocalDate date) {
        return DATE.format(date);
    }

    /**
     * The two embedded base fonts, from which sized fonts are made.
     *
     * @param regularBase the regular face
     * @param boldBase the bold face
     */
    private record Fonts(BaseFont regularBase, BaseFont boldBase) {

        /**
         * Returns the regular font in a size.
         *
         * @param size the size in points
         * @return the font
         */
        Font regular(float size) {
            return new Font(regularBase, size);
        }

        /**
         * Returns the bold font in a size.
         *
         * @param size the size in points
         * @return the font
         */
        Font bold(float size) {
            return new Font(boldBase, size);
        }
    }

    /** Draws "Generated ..." and "Page X of Y" at the bottom of every page; Y is filled in at the end. */
    private static final class FooterEvent extends PdfPageEventHelper {

        private final BaseFont font;
        private final String generated;
        private PdfTemplate total;

        /**
         * Creates the footer event.
         *
         * @param font the footer font
         * @param generated the formatted generation time
         */
        FooterEvent(BaseFont font, String generated) {
            this.font = font;
            this.generated = generated;
        }

        /** {@inheritDoc} */
        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            total = writer.getDirectContent().createTemplate(30, 12);
        }

        /** {@inheritDoc} */
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float y = document.bottom() - 24;
            cb.beginText();
            cb.setFontAndSize(font, 8);
            cb.setTextMatrix(document.left(), y);
            cb.showText("Generated " + generated);
            cb.endText();
            String label = "Page " + writer.getPageNumber() + " of ";
            float labelWidth = font.getWidthPoint(label, 8);
            float x = document.right() - labelWidth - 12;
            cb.beginText();
            cb.setFontAndSize(font, 8);
            cb.setTextMatrix(x, y);
            cb.showText(label);
            cb.endText();
            cb.addTemplate(total, x + labelWidth, y);
        }

        /** {@inheritDoc} */
        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            total.beginText();
            total.setFontAndSize(font, 8);
            total.setTextMatrix(0, 0);
            total.showText(String.valueOf(writer.getPageNumber() - 1));
            total.endText();
        }
    }
}
