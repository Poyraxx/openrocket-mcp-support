package info.openrocket.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

public class NumericComparatorTest {

	@Test
	public void compareHandlesNumbersAndStrings() {
		NumericComparator comparator = NumericComparator.INSTANCE;

		assertTrue(comparator.compare(1, 2) < 0);
		assertTrue(comparator.compare("10", "2") > 0);
		assertTrue(comparator.compare(5L, "5") == 0);
	}

	@Test
	public void compareFallsBackToLexicalForNaNValues() {
		NumericComparator comparator = NumericComparator.INSTANCE;

		assertTrue(comparator.compare("alpha", 5) > 0);
		assertTrue(comparator.compare("beta", "alpha") > 0);
	}

	@Test
	public void comparatorSortsMixedListNaturally() {
		List<Object> values = new ArrayList<>();
		values.add("3.14");
		values.add(2);
		values.add("alpha");
		values.add(1.5);

		Collections.sort(values, NumericComparator.INSTANCE);

		assertEquals(1.5, values.get(0));
		assertEquals(2, values.get(1));
		assertEquals("3.14", values.get(2));
		assertEquals("alpha", values.get(3));
	}
}
