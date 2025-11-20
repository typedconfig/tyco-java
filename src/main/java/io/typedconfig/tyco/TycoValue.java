package io.typedconfig.tyco;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents primitive values (str/int/bool/...) with template interpolation.
 */
public class TycoValue implements TycoAttribute {

    private static final Set<String> BASE_TYPES = Set.of("str", "int", "bool", "float", "decimal", "date", "time", "datetime");
    private static final Pattern TEMPLATE_REGEX = Pattern.compile("\\{([\\w\\.]+)\\}");
    private static final Object UNRENDERED = new Object();

    private final TycoContext context;
    private final String content;
    private SourceLocation location;

    private String typeName;
    private String attrName;
    private Boolean isNullable;
    private Boolean isArray;
    private Object parent;
    private boolean isLiteralStr;
    private Object rendered = UNRENDERED;

    public TycoValue(TycoContext context, String content) {
        this.context = context;
        this.content = content;
    }

    @Override
    public TycoAttribute makeCopy() {
        TycoValue copy = new TycoValue(context, content);
        copy.typeName = this.typeName;
        copy.attrName = this.attrName;
        copy.isNullable = this.isNullable;
        copy.isArray = this.isArray;
        copy.isLiteralStr = this.isLiteralStr;
        if (this.rendered != UNRENDERED) {
            copy.rendered = this.rendered;
        }
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
        if (typeName != null) {
            if (!BASE_TYPES.contains(typeName) && !"null".equals(typeName)) {
                throw new TycoParseException(typeName + " expected for " + content + ", likely needs " + typeName + "(" + content + ")", location);
            }
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

        if (Boolean.TRUE.equals(this.isArray) && !(Boolean.TRUE.equals(this.isNullable) && "null".equals(content))) {
            throw new TycoParseException("Array expected for " + parent + "." + attrName + ": " + this, location);
        }
    }

    @Override
    public void setParent(Object parent) {
        this.parent = parent;
    }

    @Override
    public String getAttrName() {
        return attrName;
    }

    @Override
    public void setAttrName(String attrName) {
        this.attrName = attrName;
    }

    @Override
    public void renderBaseContent() {
        if (typeName == null || attrName == null) {
            throw new TycoParseException("Attributes not set for " + attrName + ": " + this, location);
        }

        String raw = this.content;
        Object baseRendered;

        if (Boolean.TRUE.equals(isNullable) && "null".equals(raw)) {
            baseRendered = null;
        } else if ("str".equals(typeName)) {
            isLiteralStr = raw.startsWith("'");
            String text = raw;
            if (raw.startsWith("'''") || raw.startsWith("\"\"\"")) {
                text = raw.substring(3, raw.length() - 3);
                if (text.startsWith("\n")) {
                    text = text.substring(1);
                }
            } else if (raw.startsWith("'") || raw.startsWith("\"")) {
                text = raw.substring(1, raw.length() - 1);
            }
            baseRendered = text;
        } else if ("int".equals(typeName)) {
            String digits = raw;
            int sign = 1;
            if (digits.startsWith("-")) {
                sign = -1;
                digits = digits.substring(1);
            } else if (digits.startsWith("+")) {
                digits = digits.substring(1);
            }
            int base = 10;
            if (digits.startsWith("0x") || digits.startsWith("0X")) {
                base = 16;
                digits = digits.substring(2);
            } else if (digits.startsWith("0o") || digits.startsWith("0O")) {
                base = 8;
                digits = digits.substring(2);
            } else if (digits.startsWith("0b") || digits.startsWith("0B")) {
                base = 2;
                digits = digits.substring(2);
            }
            long parsed = Long.parseLong(digits, base);
            long value = sign * parsed;
            if (value <= Integer.MAX_VALUE && value >= Integer.MIN_VALUE) {
                baseRendered = (int) value;
            } else {
                baseRendered = value;
            }
        } else if ("float".equals(typeName)) {
            baseRendered = Double.parseDouble(raw);
        } else if ("decimal".equals(typeName)) {
            baseRendered = new BigDecimal(raw);
        } else if ("bool".equals(typeName)) {
            if ("true".equals(raw)) {
                baseRendered = Boolean.TRUE;
            } else if ("false".equals(raw)) {
                baseRendered = Boolean.FALSE;
            } else {
                throw new TycoParseException("Boolean " + attrName + " for " + parent + " not in (true, false): " + raw, location);
            }
        } else if ("date".equals(typeName)) {
            baseRendered = raw;
        } else if ("time".equals(typeName)) {
            baseRendered = TycoUtils.normalizeTimeLiteral(raw);
        } else if ("datetime".equals(typeName)) {
            baseRendered = TycoUtils.normalizeDateTimeLiteral(raw);
        } else {
            throw new TycoParseException("Unknown type: " + typeName, location);
        }

        this.rendered = baseRendered;
    }

    @Override
    public void renderReferences() {
        // primitives do not reference other objects
    }

    @Override
    public void renderTemplates() {
        if (!"str".equals(typeName) || isLiteralStr || rendered == null) {
            return;
        }

        String renderedStr = String.valueOf(rendered);
        Matcher matcher = TEMPLATE_REGEX.matcher(renderedStr);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String templateVar = matcher.group(1);
            Object obj = parent;

            if (templateVar.startsWith("..")) {
                templateVar = templateVar.substring(1);
                while (templateVar.startsWith(".")) {
                    if (obj instanceof TycoInstance) {
                        obj = ((TycoInstance) obj).parent;
                    } else {
                        obj = null;
                    }
                    if (obj == null) {
                        throw new TycoParseException("Traversing parents hit base instance", location);
                    }
                    templateVar = templateVar.substring(1);
                }
            }

            String[] parts = templateVar.split("\\.");
            if (parts.length == 0) {
                throw new TycoParseException("Empty template content", location);
            }
            Deque<String> queue = new ArrayDeque<>();
            for (String part : parts) {
                if (!part.isEmpty()) {
                    queue.addLast(part);
                }
            }
            if (queue.isEmpty()) {
                throw new TycoParseException("Empty template content", location);
            }
            String firstSegment = queue.peekFirst();
            while (!queue.isEmpty()) {
                String attr = queue.peekFirst();
                Object next = tryGetAttribute(obj, attr);
                if (next != null) {
                    obj = next;
                    queue.removeFirst();
                    continue;
                }
                if (queue.size() > 1) {
                    String merged = queue.removeFirst() + "." + queue.removeFirst();
                    queue.addFirst(merged);
                    continue;
                }
                if ("global".equals(attr) && Objects.equals(firstSegment, "global")) {
                    obj = context.getGlobals();
                    queue.removeFirst();
                    continue;
                }
                throw new TycoParseException("Cannot access attribute " + attr + " on " + obj, location);
            }

            String replacement;
            if (obj instanceof TycoAttribute) {
                Object attrRendered = ((TycoAttribute) obj).getRendered();
                if (!(attrRendered instanceof String || attrRendered instanceof Number)) {
                    throw new TycoParseException("Can not templatize objects other than strings or ints: " + obj + " (" + this + ")", location);
                }
                replacement = String.valueOf(attrRendered);
            } else {
                replacement = String.valueOf(obj);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(buffer);
        this.rendered = TycoUtils.subEscapeSequences(buffer.toString());
    }

    private Object tryGetAttribute(Object target, String attr) {
        if (target instanceof TycoInstance) {
            return ((TycoInstance) target).get(attr);
        }
        if (target instanceof TycoReference) {
            return ((TycoReference) target).get(attr);
        }
        if (target instanceof Map<?, ?>) {
            return ((Map<?, ?>) target).get(attr);
        }
        if (target instanceof TycoAttribute) {
            Object renderedAttr = ((TycoAttribute) target).getRendered();
            if (renderedAttr instanceof Map<?, ?>) {
                return ((Map<?, ?>) renderedAttr).get(attr);
            }
        }
        return null;
    }

    @Override
    public Object getRendered() {
        return rendered == UNRENDERED ? null : rendered;
    }

    @Override
    public Object getObject() {
        return getRendered();
    }

    @Override
    public Object toJson() {
        Object value = getRendered();
        if (value instanceof Map) {
            return new LinkedHashMap<>((Map<?, ?>) value);
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).doubleValue();
        }
        return value;
    }

    @Override
    public String toString() {
        return "TycoValue(" + typeName + ", " + content + (rendered == UNRENDERED ? "" : ", " + rendered) + ")";
    }
}
