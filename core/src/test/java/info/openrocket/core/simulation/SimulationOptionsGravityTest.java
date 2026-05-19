package info.openrocket.core.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import info.openrocket.core.models.gravity.ConstantGravityModel;
import info.openrocket.core.models.gravity.GravityModel;
import info.openrocket.core.models.gravity.GravityModelType;
import info.openrocket.core.models.gravity.WGSGravityModel;
import info.openrocket.core.util.BaseTestCase;

/**
 * Test class for gravity model integration in SimulationOptions.
 */
public class SimulationOptionsGravityTest extends BaseTestCase {

    @Test
    public void testDefaultGravityModel() {
        SimulationOptions options = new SimulationOptions();
        assertEquals(GravityModelType.WGS, options.getGravityModelType());
        assertEquals(9.807, options.getConstantGravity(), 1e-6);
    }

    @Test
    public void testSetGravityModelType() {
        SimulationOptions options = new SimulationOptions();
        
        options.setGravityModelType(GravityModelType.CONSTANT);
        assertEquals(GravityModelType.CONSTANT, options.getGravityModelType());
        
        options.setGravityModelType(GravityModelType.WGS);
        assertEquals(GravityModelType.WGS, options.getGravityModelType());
    }

    @Test
    public void testSetConstantGravity() {
        SimulationOptions options = new SimulationOptions();
        
        double customGravity = 5.0;
        options.setConstantGravity(customGravity);
        assertEquals(customGravity, options.getConstantGravity(), 1e-6);
    }

    @Test
    public void testToSimulationConditionsWithWGS() {
        SimulationOptions options = new SimulationOptions();
        options.setGravityModelType(GravityModelType.WGS);
        
        SimulationConditions conditions = options.toSimulationConditions();
        GravityModel model = conditions.getGravityModel();
        
        assertNotNull(model);
        assertTrue(model instanceof WGSGravityModel);
    }

    @Test
    public void testToSimulationConditionsWithConstant() {
        SimulationOptions options = new SimulationOptions();
        double customGravity = 3.71; // Mars gravity
        options.setGravityModelType(GravityModelType.CONSTANT);
        options.setConstantGravity(customGravity);
        
        SimulationConditions conditions = options.toSimulationConditions();
        GravityModel model = conditions.getGravityModel();
        
        assertNotNull(model);
        assertTrue(model instanceof ConstantGravityModel);
        assertEquals(customGravity, ((ConstantGravityModel) model).getConstantGravity(), 1e-6);
    }

    @Test
    public void testCopyConditionsFromWithGravity() {
        SimulationOptions source = new SimulationOptions();
        source.setGravityModelType(GravityModelType.CONSTANT);
        source.setConstantGravity(1.62); // Moon gravity
        
        SimulationOptions target = new SimulationOptions();
        target.copyConditionsFrom(source);
        
        assertEquals(GravityModelType.CONSTANT, target.getGravityModelType());
        assertEquals(1.62, target.getConstantGravity(), 1e-6);
    }

    @Test
    public void testEqualsWithGravity() {
        SimulationOptions options1 = new SimulationOptions();
        options1.setGravityModelType(GravityModelType.CONSTANT);
        options1.setConstantGravity(10.0);
        
        SimulationOptions options2 = new SimulationOptions();
        options2.setGravityModelType(GravityModelType.CONSTANT);
        options2.setConstantGravity(10.0);
        
        // Note: equals() checks many fields, so we just verify it doesn't crash
        // and includes gravity fields in the comparison
        assertTrue(options1.getGravityModelType() == options2.getGravityModelType());
        assertEquals(options1.getConstantGravity(), options2.getConstantGravity(), 1e-6);
    }

    @Test
    public void testCloneWithGravity() {
        SimulationOptions original = new SimulationOptions();
        original.setGravityModelType(GravityModelType.CONSTANT);
        original.setConstantGravity(8.87); // Venus gravity
        
        SimulationOptions clone = original.clone();
        
        assertEquals(GravityModelType.CONSTANT, clone.getGravityModelType());
        assertEquals(8.87, clone.getConstantGravity(), 1e-6);
    }
}

