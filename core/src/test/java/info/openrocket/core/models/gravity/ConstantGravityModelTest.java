package info.openrocket.core.models.gravity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import info.openrocket.core.util.WorldCoordinate;

/**
 * Test class for ConstantGravityModel.
 */
public class ConstantGravityModelTest {

    @Test
    public void testConstantGravityReturnsCorrectValue() {
        double expectedGravity = 9.807;
        ConstantGravityModel model = new ConstantGravityModel(expectedGravity);
        
        // Test at various locations - should always return the same value
        WorldCoordinate coord1 = new WorldCoordinate(0, 0, 0);
        WorldCoordinate coord2 = new WorldCoordinate(45, 90, 1000);
        WorldCoordinate coord3 = new WorldCoordinate(-45, -90, 5000);
        
        assertEquals(expectedGravity, model.getGravity(coord1), 1e-6);
        assertEquals(expectedGravity, model.getGravity(coord2), 1e-6);
        assertEquals(expectedGravity, model.getGravity(coord3), 1e-6);
    }

    @Test
    public void testGetConstantGravity() {
        double expectedGravity = 10.5;
        ConstantGravityModel model = new ConstantGravityModel(expectedGravity);
        
        assertEquals(expectedGravity, model.getConstantGravity(), 1e-6);
    }

    @Test
    public void testZeroGravity() {
        ConstantGravityModel model = new ConstantGravityModel(0.0);
        WorldCoordinate coord = new WorldCoordinate(0, 0, 0);
        
        assertEquals(0.0, model.getGravity(coord), 1e-6);
    }
}

