package io.typedconfig.tyco;

/**
 * Enumeration of base Tyco types
 */
public enum TycoBaseType {
    STR("str"),
    INT("int"), 
    FLOAT("float"),
    BOOL("bool"),
    DATE("date"),
    TIME("time"),
    DATETIME("datetime");

    private final String typeName;

    TycoBaseType(String typeName) {
        this.typeName = typeName;
    }

    public String getTypeName() {
        return typeName;
    }

    /**
     * Parse a type name string to TycoBaseType
     */
    public static TycoBaseType fromString(String typeName) {
        for (TycoBaseType type : values()) {
            if (type.getTypeName().equals(typeName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown Tyco type: " + typeName);
    }

    @Override
    public String toString() {
        return typeName;
    }
}