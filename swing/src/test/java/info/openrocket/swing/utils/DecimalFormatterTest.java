package info.openrocket.swing.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.DecimalFormat;

import org.junit.jupiter.api.Test;

class DecimalFormatterTest {

    @Test
    void returnsWholeNumberPatternWhenDecimalsAreNonPositive() {
        DecimalFormat format = DecimalFormatter.df(12.34, 0, false);

        assertEquals("#", format.toPattern());
        assertEquals("12", format.format(12.34));
    }

    @Test
    void respectsDisplayZerosFlag() {
        DecimalFormat withZeros = DecimalFormatter.df(8.0, 2, true);
        DecimalFormat withoutZeros = DecimalFormatter.df(8.0, 2, false);
        String expectedWithZeros = "8" + withZeros.getDecimalFormatSymbols().getDecimalSeparator() + "00";

        assertEquals(expectedWithZeros, withZeros.format(8.0));
        assertEquals("8", withoutZeros.format(8.0));
        assertEquals(2, withZeros.getMinimumFractionDigits());
        assertEquals(0, withoutZeros.getMinimumFractionDigits());
    }

    @Test
    void negativeValuesPreserveSignAndFractionDigits() {
        DecimalFormat format = DecimalFormatter.df(-8.75, 2, false);
        char separator = format.getDecimalFormatSymbols().getDecimalSeparator();

        assertEquals("-8" + separator + "75", format.format(-8.75));
        assertEquals(2, format.getMaximumFractionDigits());
    }

    @Test
    void largeValuesDoNotTriggerExtraPrecision() {
        DecimalFormat format = DecimalFormatter.df(123.456, 2, true);

        assertEquals(2, format.getMaximumFractionDigits());
        assertEquals("#.00", format.toPattern());
    }

    @Test
    void displayZerosFalseStillHonorsActualFractionalPart() {
        DecimalFormat format = DecimalFormatter.df(8.5, 2, false);
        char separator = format.getDecimalFormatSymbols().getDecimalSeparator();

        assertEquals("8" + separator + "5", format.format(8.5));
        assertEquals(0, format.getMinimumFractionDigits());
    }

    @Test
    void expandsPrecisionForSmallNonZeroValues() {
        DecimalFormat format = DecimalFormatter.df(0.00345, 2, false);

        assertEquals(4, format.getMaximumFractionDigits());
    }

    @Test
    void zeroValueDoesNotForceExtraPrecision() {
        DecimalFormat format = DecimalFormatter.df(0.0, 3, true);

        assertEquals("#.000", format.toPattern());
        assertEquals(3, format.getMaximumFractionDigits());
    }
}
