package info.openrocket.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class MarkdownUtilTest {

	@Test
	public void toHtmlReturnsEmptyStringForNullInput() {
		assertEquals("", MarkdownUtil.toHtml(null));
	}

	@Test
	public void toHtmlRendersBasicMarkdown() {
		String html = MarkdownUtil.toHtml("# Title\n\nThis is **bold** text.");

		assertTrue(html.contains("<h1>Title</h1>"));
		assertTrue(html.contains("<p>This is <strong>bold</strong> text.</p>"));
	}

	@Test
	public void toHtmlNormalizesEscapedNewlines() {
		String html = MarkdownUtil.toHtml("Line one\\r\\nLine two");

		assertTrue(html.contains("Line one"));
		assertTrue(html.contains("Line two"));
		assertFalse(html.contains("\\r"));
	}
}
