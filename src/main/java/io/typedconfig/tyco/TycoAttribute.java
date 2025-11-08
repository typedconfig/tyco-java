package io.typedconfig.tyco;

import java.util.Map;

/**
 * Base interface for all Tyco attributes (values, instances, arrays, references)
 */
public interface TycoAttribute {
    
    /**
     * Apply schema information to this attribute
     */
    void applySchemaInfo(String typeName, String attrName, Boolean isNullable, Boolean isArray);
    
    /**
     * Set the parent object for template resolution
     */
    void setParent(Object parent);
    
    /**
     * Get the attribute name
     */
    String getAttrName();
    
    /**
     * Set the attribute name
     */
    void setAttrName(String attrName);
    
    /**
     * Render base content (parse strings, numbers, etc.)
     */
    void renderBaseContent();
    
    /**
     * Render references (resolve Type(args) references)
     */
    void renderReferences();
    
    /**
     * Render templates (substitute {field} placeholders)
     */
    void renderTemplates();
    
    /**
     * Get the rendered value
     */
    Object getRendered();
    
    /**
     * Get the final object representation
     */
    Object getObject();
    
    /**
     * Convert to JSON representation
     */
    Object toJson();
    
    /**
     * Create a copy of this attribute
     */
    TycoAttribute makeCopy();
}
