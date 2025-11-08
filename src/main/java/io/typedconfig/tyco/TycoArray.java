package io.typedconfig.tyco;

import java.util.*;

/**
 * Represents an array of Tyco attributes
 */
public class TycoArray implements TycoAttribute {
    private final TycoContext context;
    private final List<TycoAttribute> content;
    private String typeName;
    private String attrName;
    private Boolean isNullable;
    private Boolean isArray;
    private Object parent;
    private List<Object> objectCache;
    
    public TycoArray(TycoContext context, List<TycoAttribute> content) {
        this.context = context;
        this.content = new ArrayList<>(content);
        this.objectCache = null;
    }
    
    public String getAttrName() {
        return attrName;
    }
    
    public void setAttrName(String attrName) {
        this.attrName = attrName;
    }
    
    @Override
    public TycoAttribute makeCopy() {
        List<TycoAttribute> copiedContent = new ArrayList<>();
        for (TycoAttribute attr : content) {
            copiedContent.add(attr.makeCopy());
        }
        return new TycoArray(context, copiedContent);
    }
    
    @Override
    public void applySchemaInfo(String typeName, String attrName, Boolean isNullable, Boolean isArray) {
        if (typeName != null) {
            this.typeName = typeName;
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
        
        for (TycoAttribute item : content) {
            item.applySchemaInfo(this.typeName, this.attrName, Boolean.FALSE, Boolean.FALSE);
        }
        
        if (Boolean.FALSE.equals(this.isArray)) {
            throw new TycoParseException("Schema for " + parent + "." + attrName + " needs to indicate array with []");
        }
    }
    
    @Override
    public void setParent(Object parent) {
        this.parent = parent;
        for (TycoAttribute item : content) {
            item.setParent(parent);
        }
    }
    

    @Override
    public void renderBaseContent() {
        for (TycoAttribute item : content) {
            item.renderBaseContent();
        }
    }
    
    @Override
    public void renderReferences() {
        for (TycoAttribute item : content) {
            item.renderReferences();
        }
    }
    
    @Override
    public void renderTemplates() {
        for (TycoAttribute item : content) {
            item.renderTemplates();
        }
    }
    
    @Override
    public Object getRendered() {
        List<Object> rendered = new ArrayList<>();
        for (TycoAttribute item : content) {
            rendered.add(item.getRendered());
        }
        return rendered;
    }
    
    @Override
    public Object getObject() {
        if (objectCache == null) {
            objectCache = new ArrayList<>();
            for (TycoAttribute item : content) {
                objectCache.add(item.getObject());
            }
        }
        return objectCache;
    }
    
    @Override
    public Object toJson() {
        List<Object> json = new ArrayList<>();
        for (TycoAttribute item : content) {
            json.add(item.toJson());
        }
        return json;
    }
    
    @Override
    public String toString() {
        return "TycoArray(" + typeName + " " + attrName + ": " + content + ")";
    }
}
