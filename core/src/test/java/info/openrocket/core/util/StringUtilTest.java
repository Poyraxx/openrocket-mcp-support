package info.openrocket.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A class that tests
 * {@link StringUtils}.
 */
public class StringUtilTest {
    @Test
    public void testStrings() {
        assertTrue(StringUtils.isEmpty(""));
        assertTrue(StringUtils.isEmpty(new StringBuilder().toString())); // ""
        assertTrue(StringUtils.isEmpty(" "));
        assertTrue(StringUtils.isEmpty("  "));
        assertTrue(StringUtils.isEmpty("       "));
        assertTrue(StringUtils.isEmpty(null));

        assertFalse(StringUtils.isEmpty("A"));
        assertFalse(StringUtils.isEmpty("         .        "));
    }

    @Test
    public void testConvertToDouble() {
        assertEquals(0.2, StringUtils.convertToDouble(".2"), MathUtil.EPSILON);
        assertEquals(0.2, StringUtils.convertToDouble(",2"), MathUtil.EPSILON);
        assertEquals(1, StringUtils.convertToDouble("1,"), MathUtil.EPSILON);
        assertEquals(2, StringUtils.convertToDouble("2."), MathUtil.EPSILON);
        assertEquals(1, StringUtils.convertToDouble("1"), MathUtil.EPSILON);
        assertEquals(1.52, StringUtils.convertToDouble("1.52"), MathUtil.EPSILON);
        assertEquals(1.52, StringUtils.convertToDouble("1,52"), MathUtil.EPSILON);
        assertEquals(1.5, StringUtils.convertToDouble("1.500"), MathUtil.EPSILON);
        assertEquals(1.5, StringUtils.convertToDouble("1,500"), MathUtil.EPSILON);
        assertEquals(1500.61, StringUtils.convertToDouble("1.500,61"), MathUtil.EPSILON);
        assertEquals(1500.61, StringUtils.convertToDouble("1,500.61"), MathUtil.EPSILON);
        assertEquals(1500.2, StringUtils.convertToDouble("1,500,200"), MathUtil.EPSILON);
        assertEquals(1500.2, StringUtils.convertToDouble("1.500.200"), MathUtil.EPSILON);
        assertEquals(1500200.23, StringUtils.convertToDouble("1500200.23"), MathUtil.EPSILON);
        assertEquals(1500200.23, StringUtils.convertToDouble("1500200,23"), MathUtil.EPSILON);
        assertEquals(1500200.23, StringUtils.convertToDouble("1,500,200.23"), MathUtil.EPSILON);
        assertEquals(1500200.23, StringUtils.convertToDouble("1.500.200,23"), MathUtil.EPSILON);
    }

    @Test
    public void testJoinArrayValues() {
        assertEquals("", StringUtils.join(",", (Object[]) null));
        assertEquals("", StringUtils.join(",", new Object[0]));
        assertEquals("a,b,c", StringUtils.join(",", new Object[] {"a", "b", "c"}));
        assertEquals("1-true-null", StringUtils.join("-", new Object[] {1, true, null}));
    }

    @Test
    public void testJoinListValues() {
        List<String> values = Arrays.asList("one", "two", "three");
        assertEquals("one;two;three", StringUtils.join(";", values));
        assertEquals("", StringUtils.join(",", Collections.emptyList()));
    }

    @Test
    public void testEscapeCsvHandlesSpecialCharacters() {
        assertEquals("", StringUtils.escapeCSV(null));
        assertEquals("plain", StringUtils.escapeCSV("plain"));
        assertEquals("\"quoted,comma\"", StringUtils.escapeCSV("quoted,comma"));
        assertEquals("\"contains\"\"quote\"\"\"", StringUtils.escapeCSV("contains\"quote\""));
        assertEquals("\"line\nbreak\"", StringUtils.escapeCSV("line\nbreak"));
    }

    @Test
    public void testRemoveHtmlTags() {
        assertNull(StringUtils.removeHTMLTags(null));
        assertEquals("plain text", StringUtils.removeHTMLTags("plain text"));
        assertEquals("Hello world", StringUtils.removeHTMLTags("<p>Hello <b>world</b></p>"));
        assertEquals("nested", StringUtils.removeHTMLTags("<div><span>nested</span></div>"));
    }
}
