package io.typedconfig.tyco;

import java.util.*;

/**
 * Represents an instance of a Tyco struct
 */
public class TycoInstance implements TycoAttribute {
    private TycoContext context;
    private String typeName;
    private Map<String, TycoAttribute> instKwargs;  // attrName -> TycoAttribute
    private String attrName;     // set later
    private Boolean isNullable;  // set later
    private Boolean isArray;     // set later
    protected Object parent;       // set later
    private Map<String, Object> objectCache;
    private SourceLocation location;
    
    public TycoInstance(TycoContext context, String typeName, Map<String, TycoAttribute> instKwargs) {
        this.context = context;
        this.typeName = typeName;
        this.instKwargs = new HashMap<>(instKwargs);
        this.objectCache = null;
    }
    
    public String getTypeName() {
        return typeName;
    }
    
    public TycoAttribute getAttribute(String attrName) {
        return instKwargs.get(attrName);
    }
    
    public String getAttrName() {
        return attrName;
    }
    
    public void setAttrName(String attrName) {
        this.attrName = attrName;
    }
    
    @Override
    public TycoAttribute makeCopy() {
        Map<String, TycoAttribute> copiedKwargs = new HashMap<>();
        for (Map.Entry<String, TycoAttribute> entry : instKwargs.entrySet()) {
            copiedKwargs.put(entry.getKey(), entry.getValue().makeCopy());
        }
        TycoInstance copy = new TycoInstance(context, typeName, copiedKwargs);
        copy.attrName = this.attrName;
        copy.isNullable = this.isNullable;
        copy.isArray = this.isArray;
        copy.parent = this.parent;
        copy.location = this.location;
        return copy;
    }

    @Override
    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    @Override
    public SourceLocation getLocation() {
        return location;
    }
    
    @Override
    public void applySchemaInfo(String typeName, String attrName, Boolean isNullable, Boolean isArray) {
        if (typeName != null && !this.typeName.equals(typeName)) {
            throw new TycoParseException("Expected " + typeName + " for " + parent + "." + attrName + " and instead have " + this, location);
        }
        if (attrName != null) {
            this.attrName = attrName;
        }
        if (isNullable != null) {
            this.isNullable = isNullable;
        }
        if (isArray != null) {
            this.isArray = isArray;
        }
        
        if (Boolean.TRUE.equals(this.isArray)) {
            throw new TycoParseException("Expected array for " + parent + "." + attrName + ", instead have " + this, location);
        }
    }
    
    @Override
    public void setParent(Object parent) {
        this.parent = parent;
        for (TycoAttribute attr : instKwargs.values()) {
            attr.setParent(this);
        }
    }
    
    public void setParent() {
        setParent(null);
    }
    

    @Override
    public void renderBaseContent() {
        for (TycoAttribute attr : instKwargs.values()) {
            attr.renderBaseContent();
        }
    }
    
    @Override
    public void renderReferences() {
        for (TycoAttribute attr : instKwargs.values()) {
            attr.renderReferences();
        }
    }
    
    @Override
    public void renderTemplates() {
        for (TycoAttribute attr : instKwargs.values()) {
            attr.renderTemplates();
        }
    }
    
    @Override
    public Object getRendered() {
        Map<String, Object> rendered = new LinkedHashMap<>();
        for (Map.Entry<String, TycoAttribute> entry : instKwargs.entrySet()) {
            rendered.put(entry.getKey(), entry.getValue().getRendered());
        }
        return rendered;
    }
    
    @Override
    public Object getObject() {
        if (objectCache == null) {
            Map<String, Object> kwargs = new LinkedHashMap<>();
            for (Map.Entry<String, TycoAttribute> entry : instKwargs.entrySet()) {
                kwargs.put(entry.getKey(), entry.getValue().getObject());
            }
            objectCache = kwargs;
        }
        return objectCache;
    }
    
    @Override
    public Object toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        for (Map.Entry<String, TycoAttribute> entry : instKwargs.entrySet()) {
            json.put(entry.getKey(), entry.getValue().toJson());
        }
        return json;
    }
    
    /**
     * Access attributes by name (for template resolution)
     */
    public TycoAttribute get(String attrName) {
        return instKwargs.get(attrName);
    }
    
    @Override
    public String toString() {
        return "TycoInstance(" + typeName + ", " + instKwargs + ")";
    }
    
    // Static method for creating objects dynamically
    public static class TycoStruct {
        private static Map<String, Class<?>> registry = new HashMap<>();
        
        public static Object createObject(String typeName, Map<String, Object> kwargs) {
            // For Java, we'll create a generic object that behaves like Python's SimpleNamespace
            return new DynamicTycoObject(typeName, kwargs);
        }
    }
    
    /**
     * Dynamic object that can hold arbitrary attributes like Python's SimpleNamespace
     */
    public static class DynamicTycoObject {
        private String typeName;
        private Map<String, Object> attributes;
        
        public DynamicTycoObject(String typeName, Map<String, Object> attributes) {
            this.typeName = typeName;
            this.attributes = new HashMap<>(attributes);
        }
        
        public Object get(String key) {
            return attributes.get(key);
        }
        
        public void set(String key, Object value) {
            attributes.put(key, value);
        }
        
        public String getTypeName() {
            return typeName;
        }
        
        public Map<String, Object> getAttributes() {
            return new HashMap<>(attributes);
        }
        
        @Override
        public String toString() {
            return typeName + "(" + attributes + ")";
        }
    }
}
