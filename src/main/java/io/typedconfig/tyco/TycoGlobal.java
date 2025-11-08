package io.typedconfig.tyco;

/**
 * Represents a global variable in Tyco configuration
 */
public class TycoGlobal {
    private final TycoBaseType type;
    private final Object value;
    private final boolean isArray;
    private final boolean isNullable;
    private final String raw;

    /**
     * Create a new global variable
     * @param type Variable type
     * @param value Variable value
     * @param isArray True if this is an array type
     * @param isNullable True if this can be null
     * @param raw Raw string representation
     */
    public TycoGlobal(TycoBaseType type, Object value, boolean isArray, boolean isNullable, String raw) {
        this.type = type;
        this.value = value;
        this.isArray = isArray;
        this.isNullable = isNullable;
        this.raw = raw;
    }

    public TycoBaseType getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }

    public boolean isArray() {
        return isArray;
    }

    public boolean isNullable() {
        return isNullable;
    }

    public String getRaw() {
        return raw;
    }

    @Override
    public String toString() {
        return String.format("TycoGlobal{type=%s, value=%s, isArray=%s, isNullable=%s}", 
                type, value, isArray, isNullable);
    }
}