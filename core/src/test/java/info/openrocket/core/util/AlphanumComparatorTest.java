package info.openrocket.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class AlphanumComparatorTest {

	private final AlphanumComparator comparator = new AlphanumComparator();

	@Test
	void sortsNumericSuffixesInNaturalOrder() {
		List<String> values = new ArrayList<>(Arrays.asList("file2", "file10", "file1"));
		values.sort(comparator);

		assertEquals(List.of("file1", "file2", "file10"), values);
	}

	@Test
	void compareHandlesInterleavedNumbersAndLetters() {
		assertTrue(comparator.compare("img2.png", "img12.png") < 0);
		assertTrue(comparator.compare("section9b", "section10a") < 0);
		assertTrue(comparator.compare("chapter10", "chapter10a") < 0);
	}

	@Test
	void alphabeticalChunksFollowCollatorOrdering() {
		assertTrue(comparator.compare("alpha", "beta") < 0);
		assertEquals(0, comparator.compare("gamma", "gamma"));
	}

	@Test
	void leadingZerosOrderByChunkLengthFirst() {
		assertTrue(comparator.compare("item2", "item002") < 0);
		assertTrue(comparator.compare("item002", "item2") > 0);
		assertTrue(comparator.compare("item01", "item002") < 0);
	}
}
