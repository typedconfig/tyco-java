package io.typedconfig.tyco;

import java.util.*;

/**
 * Represents a reference to another Tyco instance (Type(args))
 */
public class TycoReference implements TycoAttribute {
    private static final Object UNRENDERED = new Object();
    
    private TycoContext context;
    private List<TycoAttribute> instArgs;
    private String typeName;
    private String attrName;
    private Boolean isNullable;
    private Boolean isArray;
    private Object parent;
    private Object rendered = UNRENDERED;
    
    public TycoReference(TycoContext context, List<TycoAttribute> instArgs, String typeName) {
        this.context = context;
        this.instArgs = new ArrayList<>(instArgs);
        this.typeName = typeName;
    }
    
    public String getAttrName() {
        return attrName;
    }
    
    public void setAttrName(String attrName) {
        this.attrName = attrName;
    }
    
    @Override
    public TycoAttribute makeCopy() {
        List<TycoAttribute> copiedArgs = new ArrayList<>();
        for (TycoAttribute arg : instArgs) {
            copiedArgs.add(arg.makeCopy());
        }
        return new TycoReference(context, copiedArgs, typeName);
    }
    
    @Override
    public void applySchemaInfo(String typeName, String attrName, Boolean isNullable, Boolean isArray) {
        if (typeName != null && !this.typeName.equals(typeName)) {
            throw new TycoParseException("Expected " + typeName + " for " + parent + "." + attrName + " and instead have " + this);
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
            throw new TycoParseException("Expected array for " + parent + "." + attrName + ", instead have " + this);
        }
    }
    
    @Override
    public void setParent(Object parent) {
        this.parent = parent;
    }
    

    @Override
    public void renderBaseContent() {
        // References don't render base content
    }
    
    @Override
    public void renderReferences() {
        if (rendered != UNRENDERED) {
            throw new TycoParseException("Rendered multiple times " + this);
        }
        
        TycoStruct struct = context.getStruct(typeName);
        if (struct == null) {
            throw new TycoParseException("Bad type name for reference: " + typeName + " " + instArgs);
        }
        
        rendered = struct.loadReference(instArgs);
    }
    
    @Override
    public void renderTemplates() {
        // References don't contain templates directly
    }
    
    @Override
    public Object getRendered() {
        if (rendered instanceof TycoInstance) {
            return ((TycoInstance) rendered).getRendered();
        }
        return rendered;
    }
    
    @Override
    public Object getObject() {
        if (rendered instanceof TycoInstance) {
            return ((TycoInstance) rendered).getObject();
        }
        return rendered;
    }
    
    @Override
    public Object toJson() {
        if (rendered instanceof TycoInstance) {
            return ((TycoInstance) rendered).toJson();
        }
        return rendered;
    }
    
    /**
     * Access attributes by name (for template resolution)
     */
    public TycoAttribute get(String attrName) {
        if (rendered instanceof TycoInstance) {
            return ((TycoInstance) rendered).get(attrName);
        }
        throw new TycoParseException("Cannot access attribute on unrendered reference");
    }
    
    @Override
    public String toString() {
        return "TycoReference(" + typeName + ", " + instArgs + ", " + rendered + ")";
    }
}
