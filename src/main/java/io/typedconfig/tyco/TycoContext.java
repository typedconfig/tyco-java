package io.typedconfig.tyco;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Context for storing parsed Tyco configuration data
 * Manages globals, struct definitions, and instances with reference resolution
 */
public class TycoContext {
    private final Map<String, TycoLexer> pathCache;
    private final Map<String, TycoStruct> structs;
    private final Map<String, TycoAttribute> globals;
    
    public TycoContext() {
        this.pathCache = new HashMap<>();
        this.structs = new LinkedHashMap<>();
        this.globals = new LinkedHashMap<>();
    }
    
    /**
     * Sets a global attribute
     */
    public void setGlobalAttribute(String attrName, TycoAttribute attr) {
        if (globals.containsKey(attrName)) {
            throw new TycoParseException("Duplicate global attribute: " + attrName);
        }
        globals.put(attrName, attr);
    }

    public void setGlobalAttr(String attrName, TycoAttribute attr) {
        setGlobalAttribute(attrName, attr);
    }
    
    /**
     * Gets a global attribute
     */
    public TycoAttribute getGlobalAttribute(String attrName) {
        return globals.get(attrName);
    }
    
    /**
     * Gets all global attributes
     */
    public Map<String, TycoAttribute> getGlobals() {
        return new HashMap<>(globals);
    }
    
    /**
     * Adds a struct definition
     */
    public TycoStruct addStruct(String typeName) {
        TycoStruct struct = new TycoStruct(this, typeName);
        structs.put(typeName, struct);
        return struct;
    }
    
    /**
     * Gets a struct by type name
     */
    public TycoStruct getStruct(String typeName) {
        return structs.get(typeName);
    }
    
    /**
     * Gets all structs
     */
    public Map<String, TycoStruct> getStructs() {
        return new HashMap<>(structs);
    }
    
    /**
     * Renders all content (sets parents, renders base content, loads primary keys, renders references and templates)
     */
    public void renderContent() {
        setParents();
        renderBaseContent();
        loadPrimaryKeys();
        renderReferences();
        renderTemplates();
    }
    
    private void setParents() {
        for (TycoAttribute attr : globals.values()) {
            attr.setParent(globals);
        }
        for (TycoStruct struct : structs.values()) {
            for (TycoInstance inst : struct.getInstances()) {
                inst.setParent();
            }
        }
    }
    
    private void renderBaseContent() {
        for (TycoAttribute attr : globals.values()) {
            attr.renderBaseContent();
        }
        for (TycoStruct struct : structs.values()) {
            for (TycoInstance inst : struct.getInstances()) {
                inst.renderBaseContent();
            }
        }
    }
    
    private void loadPrimaryKeys() {
        for (TycoStruct struct : structs.values()) {
            struct.loadPrimaryKeys();
        }
    }
    
    private void renderReferences() {
        for (TycoAttribute attr : globals.values()) {
            attr.renderReferences();
        }
        for (TycoStruct struct : structs.values()) {
            for (TycoInstance inst : struct.getInstances()) {
                inst.renderReferences();
            }
        }
    }
    
    private void renderTemplates() {
        for (TycoAttribute attr : globals.values()) {
            attr.renderTemplates();
        }
        for (TycoStruct struct : structs.values()) {
            for (TycoInstance inst : struct.getInstances()) {
                inst.renderTemplates();
            }
        }
    }
    
    /**
     * Materializes a single object (globals + struct arrays) analogous to the Python binding.
     */
    public Map<String, Object> toObject() {
        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<String, TycoAttribute> entry : globals.entrySet()) {
            result.put(entry.getKey(), entry.getValue().toJson());
        }

        for (TycoStruct struct : structs.values()) {
            if (struct.getPrimaryKeys().isEmpty()) {
                continue;
            }
            List<Map<String, Object>> instances = new ArrayList<>();
            for (TycoInstance instance : struct.getInstances()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> json = (Map<String, Object>) instance.toJson();
                instances.add(json);
            }
            result.put(struct.getTypeName(), instances);
        }

        return result;
    }

    /**
     * @deprecated Use {@link #toObject()} instead.
     */
    @Deprecated
    public Map<String, Object> toJson() {
        return toObject();
    }

    TycoLexer getCachedLexer(String path) {
        return pathCache.get(path);
    }

    void cacheLexer(String path, TycoLexer lexer) {
        pathCache.put(path, lexer);
    }
}
