package info.openrocket.core.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class UtilsTest {

	@Test
	public void equalsHandlesNullsSymmetrically() {
		assertTrue(Utils.equals(null, null));
		assertFalse(Utils.equals(null, "value"));
		assertFalse(Utils.equals("value", null));
		assertTrue(Utils.equals("same", "same"));
	}

	@Test
	public void containsFindsMatchingElement() {
		String[] values = new String[] { "alpha", "beta", null };

		assertTrue(Utils.contains(values, "beta"));
		assertTrue(Utils.contains(values, null));
		assertFalse(Utils.contains(values, "gamma"));
	}
}
