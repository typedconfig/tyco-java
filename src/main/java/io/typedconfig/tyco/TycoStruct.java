package io.typedconfig.tyco;

import java.util.*;

/**
 * Represents a struct definition in Tyco with schema and instances
 */
public class TycoStruct {
    private TycoContext context;
    private String typeName;
    private Map<String, String> attrTypes;           // attrName -> typeName
    private List<String> primaryKeys;                // [attrName,...]
    private Set<String> nullableKeys;                // {attrName,...}
    private Set<String> arrayKeys;                   // {attrName,...}
    private List<TycoInstance> instances;            // [TycoInstance(),...]
    private Map<List<Object>, TycoInstance> mappedInstances; // {primaryKeyValues : TycoInstance}
    
    public TycoStruct(TycoContext context, String typeName) {
        this.context = context;
        this.typeName = typeName;
        this.attrTypes = new LinkedHashMap<>();
        this.primaryKeys = new ArrayList<>();
        this.nullableKeys = new HashSet<>();
        this.arrayKeys = new HashSet<>();
        this.instances = new ArrayList<>();
        this.mappedInstances = new HashMap<>();
    }
    
    public String getTypeName() {
        return typeName;
    }
    
    public List<String> getAttrNames() {
        return new ArrayList<>(attrTypes.keySet());
    }

    public boolean hasAttribute(String attrName) {
        return attrTypes.containsKey(attrName);
    }
    
    public List<String> getPrimaryKeys() {
        return new ArrayList<>(primaryKeys);
    }
    
    public List<TycoInstance> getInstances() {
        return new ArrayList<>(instances);
    }
    
    /**
     * Add an attribute to the schema
     */
    public void addAttribute(String attrName, String typeName, boolean isPrimary, boolean isNullable, boolean isArray) {
        if (attrTypes.containsKey(attrName)) {
            throw new TycoParseException("Duplicate attribute found for " + attrName + " in " + this.typeName);
        }
        
        attrTypes.put(attrName, typeName);
        
        if (isArray) {
            arrayKeys.add(attrName);
            if (isPrimary) {
                throw new TycoParseException("Cannot set a primary key on an array");
            }
        }
        
        if (isPrimary) {
            primaryKeys.add(attrName);
        } else if (isNullable) {
            nullableKeys.add(attrName);
        }
    }
    
    /**
     * Create and add an instance from arguments and defaults
     */
    public void createInstance(List<TycoAttribute> instArgs, Map<String, TycoAttribute> defaultKwargs) {
        TycoInstance inst = createInlineInstance(instArgs, defaultKwargs);
        instances.add(inst);
    }
    
    /**
     * Create an inline instance (not added to instances list)
     */
    public TycoInstance createInlineInstance(List<TycoAttribute> instArgs, Map<String, TycoAttribute> defaultKwargs) {
        Map<String, TycoAttribute> instKwargs = new LinkedHashMap<>();
        boolean kwargsOnly = false;
        List<String> attrNames = getAttrNames();
        
        for (int i = 0; i < instArgs.size(); i++) {
            TycoAttribute attr = instArgs.get(i);
            String attrName = attr.getAttrName();
            SourceLocation attrLocation = attr.getLocation();

            if (attrName == null) {
                if (kwargsOnly) {
                    throw new TycoParseException("Cannot use positional values after keyed values: " + instArgs, attrLocation);
                }
                if (i >= attrNames.size()) {
                    throw new TycoParseException("Too many positional arguments for " + typeName, attrLocation);
                }
                attrName = attrNames.get(i);
                attr.setAttrName(attrName);
            } else {
                kwargsOnly = true;
            }
            
            instKwargs.put(attrName, attr);
        }

        Map<String, TycoAttribute> completeKwargs = resolveCompleteKwargs(instKwargs, defaultKwargs);
        TycoInstance instance = new TycoInstance(context, typeName, completeKwargs);
        instance.setLocation(firstLocation(instArgs, null));
        return instance;
    }
    
    /**
     * Load primary keys for reference resolution
     */
    public void loadPrimaryKeys() {
        if (primaryKeys.isEmpty()) {
            return;
        }
        
        for (TycoInstance inst : instances) {
            List<Object> key = new ArrayList<>();
            for (String keyAttr : primaryKeys) {
                key.add(inst.getAttribute(keyAttr).getRendered());
            }

            if (mappedInstances.containsKey(key)) {
                throw new TycoParseException(key + " already found for " + typeName + ": " + mappedInstances.get(key), inst.getLocation());
            }
            mappedInstances.put(key, inst);
        }
    }
    
    /**
     * Load reference by primary key arguments
     */
    public TycoInstance loadReference(List<TycoAttribute> instArgs, SourceLocation referenceLocation) {
        Map<String, TycoAttribute> instKwargs = new HashMap<>();
        boolean kwargsOnly = false;

        for (int i = 0; i < instArgs.size(); i++) {
            TycoAttribute attr = instArgs.get(i);
            String attrName = attr.getAttrName();
            SourceLocation attrLocation = attr.getLocation();

            if (attrName == null) {
                if (kwargsOnly) {
                    throw new TycoParseException("Cannot use positional values after keyed values: " + instArgs, attrLocation);
                }
                if (i >= primaryKeys.size()) {
                    throw new TycoParseException("Too many arguments for reference to " + typeName, attrLocation);
                }
                attrName = primaryKeys.get(i);
                attr.setAttrName(attrName);
            } else {
                kwargsOnly = true;
            }
            
            String fieldTypeName = attrTypes.get(attrName);
            boolean isNullable = nullableKeys.contains(attrName);
            boolean isArray = arrayKeys.contains(attrName);
            
            attr.applySchemaInfo(fieldTypeName, attrName, isNullable, isArray);
            attr.renderBaseContent();
            instKwargs.put(attrName, attr);
        }
        
        List<Object> key = new ArrayList<>();
        for (String keyAttr : primaryKeys) {
            key.add(instKwargs.get(keyAttr).getRendered());
        }
        
        TycoInstance result = mappedInstances.get(key);
        if (result == null) {
            throw new TycoParseException("Unable to find reference of " + typeName + "(" + key + ")", firstLocation(instArgs, referenceLocation));
        }
        return result;
    }
    
    private Map<String, TycoAttribute> resolveCompleteKwargs(Map<String, TycoAttribute> instKwargs, Map<String, TycoAttribute> defaultKwargs) {
        Map<String, TycoAttribute> completeKwargs = new LinkedHashMap<>();
        
        for (String attrName : attrTypes.keySet()) {
            TycoAttribute attr;
            if (instKwargs.containsKey(attrName)) {
                attr = instKwargs.get(attrName);
            } else if (defaultKwargs != null && defaultKwargs.containsKey(attrName)) {
                attr = defaultKwargs.get(attrName).makeCopy();
            } else {
                throw new TycoParseException("Invalid attribute " + attrName + " for " + this);
            }
            
            String fieldTypeName = attrTypes.get(attrName);
            boolean isNullable = nullableKeys.contains(attrName);
            boolean isArray = arrayKeys.contains(attrName);
            
            attr.applySchemaInfo(fieldTypeName, attrName, isNullable, isArray);
            completeKwargs.put(attrName, attr);
        }
        
        return completeKwargs;
    }

    private SourceLocation firstLocation(List<TycoAttribute> attrs, SourceLocation fallback) {
        for (TycoAttribute attr : attrs) {
            if (attr != null && attr.getLocation() != null) {
                return attr.getLocation();
            }
        }
        return fallback;
    }
    
    @Override
    public String toString() {
        return "TycoStruct(" + typeName + ")";
    }
}
