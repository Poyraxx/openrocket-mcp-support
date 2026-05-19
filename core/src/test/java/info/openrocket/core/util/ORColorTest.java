package info.openrocket.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.awt.Color;

import org.junit.jupiter.api.Test;

public class ORColorTest {

	@Test
	public void gettersAndSettersMutateComponents() {
		ORColor color = new ORColor(10, 20, 30);
		color.setRed(40);
		color.setGreen(50);
		color.setBlue(60);
		color.setAlpha(70);

		assertEquals(40, color.getRed());
		assertEquals(50, color.getGreen());
		assertEquals(60, color.getBlue());
		assertEquals(70, color.getAlpha());
	}

	@Test
	public void conversionToAndFromAwtColorPreservesChannels() {
		ORColor color = new ORColor(1, 2, 3, 4);
		Color awt = color.toAWTColor();

		assertEquals(1, awt.getRed());
		assertEquals(2, awt.getGreen());
		assertEquals(3, awt.getBlue());
		assertEquals(4, awt.getAlpha());

		ORColor converted = ORColor.fromAWTColor(awt);
		assertNotSame(color, converted);
		assertEquals(color, converted);
	}

	@Test
	public void equalsComparesComponentValues() {
		ORColor first = new ORColor(10, 20, 30, 40);
		ORColor same = new ORColor(10, 20, 30, 40);
		ORColor different = new ORColor(10, 21, 30, 40);

		assertEquals(first, first);
		assertEquals(first, same);
		assertNotEquals(first, different);
		assertNotEquals("color", first);
	}
}
