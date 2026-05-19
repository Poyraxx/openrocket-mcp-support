package info.openrocket.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class MutableCoordinateTest {

	@Test
	void basicSetAndAddOperationsWork() {
		MutableCoordinate mutable = new MutableCoordinate().set(1, 2, 3, 0.5);
		mutable.add(1, -1, 0.5);

		assertEquals(2.0, mutable.getX(), 1e-9);
		assertEquals(1.0, mutable.getY(), 1e-9);
		assertEquals(3.5, mutable.getZ(), 1e-9);
		assertEquals(0.5, mutable.getWeight(), 1e-9);
	}

	@Test
	void addScaledMatchesExpectations() {
		MutableCoordinate mutable = new MutableCoordinate().set(0, 0, 0);
		CoordinateIF coord = new Coordinate(2, 4, -1, 0.25);

		mutable.addScaled(coord, 0.5).addScaled(coord, 1.5);

		assertEquals(4.0, mutable.getX(), 1e-9);
		assertEquals(8.0, mutable.getY(), 1e-9);
		assertEquals(-2.0, mutable.getZ(), 1e-9);
		assertEquals(0.5, mutable.getWeight(), 1e-9);
	}

	@Test
	void toCoordinateCreatesIndependentImmutableInstance() {
		MutableCoordinate mutable = new MutableCoordinate().set(1, 1, 1);
		CoordinateIF first = mutable.toImmutable();

		mutable.add(1, 0, 0);
		CoordinateIF second = mutable.toImmutable();

		assertNotSame(first, second);
		assertEquals(1.0, first.getX(), 1e-9);
		assertEquals(2.0, second.getX(), 1e-9);
	}

	@Test
	void normalizeThrowsForZeroVector() {
		MutableCoordinate zero = new MutableCoordinate();
		assertThrows(IllegalStateException.class, zero::normalize);
	}

	@Test
	void normalizeScalesComponentsToUnitLength() {
		MutableCoordinate coord = new MutableCoordinate(3, 4, 0, 2.0);
		coord.normalize();

		assertEquals(0.6, coord.getX(), 1e-12);
		assertEquals(0.8, coord.getY(), 1e-12);
		assertEquals(0.0, coord.getZ(), 1e-12);
		assertEquals(2.0, coord.getWeight(), 1e-12);
	}

	@Test
	void crossProductMutatesReceiver() {
		MutableCoordinate a = new MutableCoordinate(1, 0, 0);
		MutableCoordinate b = new MutableCoordinate(0, 1, 0);

		a.cross(b);

		assertEquals(0.0, a.getX(), 1e-12);
		assertEquals(0.0, a.getY(), 1e-12);
		assertEquals(1.0, a.getZ(), 1e-12);
	}

	@Test
	void staticCrossProducesNewMutableCoordinate() {
		CoordinateIF result = MutableCoordinate.cross(new Coordinate(0, 1, 0), new Coordinate(1, 0, 0));
		assertEquals(0.0, result.getX(), 1e-12);
		assertEquals(0.0, result.getY(), 1e-12);
		assertEquals(-1.0, result.getZ(), 1e-12);
	}

	@Test
	void averageUsesWeightsWhenPresent() {
		MutableCoordinate coord = new MutableCoordinate(1, 1, 1, 2.0);
		CoordinateIF other = new Coordinate(3, 3, 3, 2.0);

		coord.average(other);

		assertEquals(2.0, coord.getX(), 1e-12);
		assertEquals(2.0, coord.getY(), 1e-12);
		assertEquals(2.0, coord.getZ(), 1e-12);
		assertEquals(4.0, coord.getWeight(), 1e-12);
	}

	@Test
	void averageFallsBackToUnweightedWhenWeightsCancelOut() {
		MutableCoordinate coord = new MutableCoordinate(4, 0, 0, 0.1);
		CoordinateIF other = new Coordinate(0, 4, 0, -0.1);

		coord.average(other);

		assertEquals(2.0, coord.getX(), 1e-12);
		assertEquals(2.0, coord.getY(), 1e-12);
		assertEquals(0.0, coord.getZ(), 1e-12);
		assertEquals(0.0, coord.getWeight(), 1e-12);
	}

	@Test
	void interpolateBlendsComponentsLinearly() {
		MutableCoordinate coord = new MutableCoordinate(0, 0, 0, 0);
		CoordinateIF target = new Coordinate(10, -10, 5, 2.0);

		coord.interpolate(target, 0.25);

		assertEquals(2.5, coord.getX(), 1e-12);
		assertEquals(-2.5, coord.getY(), 1e-12);
		assertEquals(1.25, coord.getZ(), 1e-12);
		assertEquals(0.5, coord.getWeight(), 1e-12);
	}

	@Test
	void equalsUsesToleranceComparison() {
		MutableCoordinate base = new MutableCoordinate(1, 2, 3, 4);
		MutableCoordinate near = new MutableCoordinate(1 + MathUtil.EPSILON / 4,
				2 - MathUtil.EPSILON / 4,
				3 + MathUtil.EPSILON / 4,
				4 - MathUtil.EPSILON / 4);

		assertTrue(base.equals(near));
		assertTrue(near.equals(base));

		MutableCoordinate far = new MutableCoordinate(1 + MathUtil.EPSILON * 10, 2, 3, 4);
		assertFalse(base.equals(far));
		assertFalse(base.equals("not a coordinate"));
	}

	@Test
	void hashCodeVariesWithComponents() {
		MutableCoordinate first = new MutableCoordinate(1, 2, 3, 4);
		MutableCoordinate second = new MutableCoordinate(2, 2, 3, 4);
		assertNotEquals(first.hashCode(), second.hashCode());
	}

	@Test
	void cloneProducesIndependentInstance() {
		MutableCoordinate coord = new MutableCoordinate(1, 2, 3, 4);
		CoordinateIF copy = coord.clone();

		assertNotSame(coord, copy);
		assertEquals(1.0, copy.getX(), 1e-12);
		assertEquals(2.0, copy.getY(), 1e-12);
		assertEquals(3.0, copy.getZ(), 1e-12);
		assertEquals(4.0, copy.getWeight(), 1e-12);
	}
}
