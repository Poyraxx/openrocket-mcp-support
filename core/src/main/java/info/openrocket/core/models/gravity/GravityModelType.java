package info.openrocket.core.models.gravity;

/**
 * Enum representing different types of gravity models available in OpenRocket.
 * 
 * @author OpenRocket Team
 */
public enum GravityModelType {
    WGS("WGS"),
    CONSTANT("Constant");

    private final String stringValue;

    GravityModelType(String stringValue) {
        this.stringValue = stringValue;
    }

    public String toStringValue() {
        return stringValue;
    }

    public static GravityModelType fromString(String stringValue) {
        for (GravityModelType type : GravityModelType.values()) {
            if (type.stringValue.equalsIgnoreCase(stringValue)) {
                return type;
            }
        }
        throw new IllegalArgumentException("No enum constant " + GravityModelType.class.getCanonicalName() + " for string value: " + stringValue);
    }

    @Override
    public String toString() {
        return stringValue;
    }
}

