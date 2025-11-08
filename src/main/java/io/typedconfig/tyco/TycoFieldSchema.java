package io.typedconfig.tyco;

/**
 * Schema definition for a field within a Tyco struct
 */
public class TycoFieldSchema {
    private final TycoBaseType type;
    private final boolean isArray;
    private final boolean isNullable;
    private final boolean isPrimaryKey;
    private final Object defaultValue;
    private final boolean hasDefault;

    /**
     * Create a field schema
     * @param type Field type
     * @param isArray True if this is an array field
     * @param isNullable True if this field can be null
     * @param isPrimaryKey True if this is a primary key field
     * @param defaultValue Default value (can be null)
     * @param hasDefault True if a default value is specified
     */
    public TycoFieldSchema(TycoBaseType type, boolean isArray, boolean isNullable, 
                          boolean isPrimaryKey, Object defaultValue, boolean hasDefault) {
        this.type = type;
        this.isArray = isArray;
        this.isNullable = isNullable;
        this.isPrimaryKey = isPrimaryKey;
        this.defaultValue = defaultValue;
        this.hasDefault = hasDefault;
    }

    /**
     * Create a field schema without default value
     */
    public TycoFieldSchema(TycoBaseType type, boolean isArray, boolean isNullable, boolean isPrimaryKey) {
        this(type, isArray, isNullable, isPrimaryKey, null, false);
    }

    public TycoBaseType getType() {
        return type;
    }

    public boolean isArray() {
        return isArray;
    }

    public boolean isNullable() {
        return isNullable;
    }

    public boolean isPrimaryKey() {
        return isPrimaryKey;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public boolean hasDefault() {
        return hasDefault;
    }

    @Override
    public String toString() {
        return String.format("TycoFieldSchema{type=%s, isArray=%s, isNullable=%s, isPrimaryKey=%s, hasDefault=%s}", 
                type, isArray, isNullable, isPrimaryKey, hasDefault);
    }
}