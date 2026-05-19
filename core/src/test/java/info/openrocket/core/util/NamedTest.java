package info.openrocket.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class NamedTest {

	@Test
	public void namedHoldsValueAndName() {
		Named<String> named = new Named<>("payload", "Payload Section");

		assertEquals("payload", named.get());
		assertEquals("Payload Section", named.toString());
	}

	@Test
	public void compareToUsesCollatorOrdering() {
		Named<String> alpha = new Named<>("a", "Alpha");
		Named<String> beta = new Named<>("b", "Beta");
		Named<String> alphaCopy = new Named<>("c", "Alpha");

		assertTrue(alpha.compareTo(beta) < 0);
		assertTrue(beta.compareTo(alpha) > 0);
		assertEquals(0, alpha.compareTo(alphaCopy));
	}
}
