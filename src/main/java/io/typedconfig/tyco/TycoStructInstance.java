package io.typedconfig.tyco;

import java.util.*;

/**
 * Represents an instance of a Tyco struct
 */
public class TycoStructInstance {
    private final String typeName;
    private final Map<String, Object> fields;
    private final List<Object> primaryKeyValues;

    /**
     * Create a new struct instance
     * @param typeName Name of the struct type
     */
    public TycoStructInstance(String typeName) {
        this.typeName = typeName;
        this.fields = new LinkedHashMap<>();
        this.primaryKeyValues = new ArrayList<>();
    }

    /**
     * Set a field value
     * @param fieldName Field name
     * @param value Field value
     */
    public void setField(String fieldName, Object value) {
        fields.put(fieldName, value);
    }

    /**
     * Get a field value
     * @param fieldName Field name
     * @return Field value or null if not set
     */
    public Object getField(String fieldName) {
        return fields.get(fieldName);
    }

    /**
     * Add a primary key value
     * @param value Primary key value
     */
    public void addPrimaryKeyValue(Object value) {
        primaryKeyValues.add(value);
    }

    public String getTypeName() {
        return typeName;
    }

    public Map<String, Object> getFields() {
        return new LinkedHashMap<>(fields);
    }

    public List<Object> getPrimaryKeyValues() {
        return new ArrayList<>(primaryKeyValues);
    }

    /**
     * Convert this instance to a Map representation
     * @return Map containing all field values
     */
    public Map<String, Object> toMap() {
        return new LinkedHashMap<>(fields);
    }

    @Override
    public String toString() {
        return String.format("TycoStructInstance{typeName='%s', fields=%s, primaryKeyValues=%s}", 
                typeName, fields, primaryKeyValues);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        TycoStructInstance that = (TycoStructInstance) obj;
        return Objects.equals(typeName, that.typeName) &&
               Objects.equals(primaryKeyValues, that.primaryKeyValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeName, primaryKeyValues);
    }
}