package info.openrocket.core.models.gravity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Test class for GravityModelType.
 */
public class GravityModelTypeTest {

    @Test
    public void testToStringValue() {
        assertEquals("WGS", GravityModelType.WGS.toStringValue());
        assertEquals("Constant", GravityModelType.CONSTANT.toStringValue());
    }

    @Test
    public void testFromString() {
        assertEquals(GravityModelType.WGS, GravityModelType.fromString("WGS"));
        assertEquals(GravityModelType.WGS, GravityModelType.fromString("wgs"));
        assertEquals(GravityModelType.CONSTANT, GravityModelType.fromString("Constant"));
        assertEquals(GravityModelType.CONSTANT, GravityModelType.fromString("constant"));
    }

    @Test
    public void testFromStringInvalid() {
        assertThrows(IllegalArgumentException.class, () -> {
            GravityModelType.fromString("invalid");
        });
    }

    @Test
    public void testToString() {
        assertEquals("WGS", GravityModelType.WGS.toString());
        assertEquals("Constant", GravityModelType.CONSTANT.toString());
    }
}

