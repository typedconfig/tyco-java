package io.typedconfig.tyco;

import java.util.*;

/**
 * Schema definition for a Tyco struct
 */
public class TycoStructSchema {
    private final String name;
    private final Map<String, TycoFieldSchema> fields;
    private final List<String> primaryKeys;

    /**
     * Create a new struct schema
     * @param name Struct name
     */
    public TycoStructSchema(String name) {
        this.name = name;
        this.fields = new LinkedHashMap<>(); // Preserve field order
        this.primaryKeys = new ArrayList<>();
    }

    /**
     * Add a field to the struct schema
     * @param fieldName Field name
     * @param schema Field schema
     */
    public void addField(String fieldName, TycoFieldSchema schema) {
        fields.put(fieldName, schema);
        if (schema.isPrimaryKey()) {
            primaryKeys.add(fieldName);
        }
    }

    /**
     * Get a field schema by name
     * @param fieldName Field name
     * @return Field schema or null if not found
     */
    public TycoFieldSchema getField(String fieldName) {
        return fields.get(fieldName);
    }

    public String getName() {
        return name;
    }

    public Map<String, TycoFieldSchema> getFields() {
        return new LinkedHashMap<>(fields);
    }

    public List<String> getPrimaryKeys() {
        return new ArrayList<>(primaryKeys);
    }

    public List<String> getFieldNames() {
        return new ArrayList<>(fields.keySet());
    }

    @Override
    public String toString() {
        return String.format("TycoStructSchema{name='%s', fields=%s, primaryKeys=%s}", 
                name, fields.keySet(), primaryKeys);
    }
}