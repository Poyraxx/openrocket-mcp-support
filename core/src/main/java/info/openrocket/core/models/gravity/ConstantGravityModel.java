package info.openrocket.core.models.gravity;

import info.openrocket.core.util.ModID;
import info.openrocket.core.util.WorldCoordinate;

/**
 * A gravity model that returns a constant gravitational acceleration value
 * regardless of location.
 *
 * @author OpenRocket Team
 */
public record ConstantGravityModel(double gravity) implements GravityModel {

	/**
	 * Creates a constant gravity model with the specified gravity value.
	 *
	 * @param gravity the gravitational acceleration in m/s²
	 */
	public ConstantGravityModel {
	}

	@Override
	public double getGravity(WorldCoordinate wc) {
		return gravity;
	}

	@Override
	public ModID getModID() {
		// The model is immutable, so it can return a constant mod ID
		return ModID.ZERO;
	}

	/**
	 * Gets the constant gravity value.
	 *
	 * @return the gravitational acceleration in m/s²
	 */
	public double getConstantGravity() {
		return gravity;
	}

}

