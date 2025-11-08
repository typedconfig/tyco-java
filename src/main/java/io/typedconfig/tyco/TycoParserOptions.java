package io.typedconfig.tyco;

/**
 * Parser options for customizing Tyco parsing behavior
 */
public class TycoParserOptions {
    private boolean strict = true;
    private int templateIterations = 10;

    /**
     * Default constructor with default options
     */
    public TycoParserOptions() {
    }

    /**
     * Constructor with custom options
     * @param strict Enable strict parsing mode
     * @param templateIterations Maximum template expansion iterations
     */
    public TycoParserOptions(boolean strict, int templateIterations) {
        this.strict = strict;
        this.templateIterations = templateIterations;
    }

    public boolean isStrict() {
        return strict;
    }

    public void setStrict(boolean strict) {
        this.strict = strict;
    }

    public int getTemplateIterations() {
        return templateIterations;
    }

    public void setTemplateIterations(int templateIterations) {
        this.templateIterations = templateIterations;
    }
}