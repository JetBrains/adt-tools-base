package com.android.build.gradle.integration.common.fixture.app;

import java.util.Map;

/**
 * Generator to create build.gradle with arbitrary number of variants.
 *
 * The build.gradle is created from the given template and a map of strings to replace.  E.g., if
 * the map contains an entry ("buildTypes" : 3), the all instances of "${buildTypes}" in the
 * template will be replace by:
 * buildType0
 * buildType1
 * buildType2
 * This allows arbitrary number of build types and product flavors to be generated easily.
 */
public class VariantBuildScriptGenerator {

    public static final Integer LARGE_NUMBER = 20;

    public static final Integer MEDIUM_NUMBER = 5;

    public static final Integer SMALL_NUMBER = 2;

    private final String template;

    private final Map<String, Integer> variantCounts;

    /**
     * Create a VariantBuildScriptGenerator
     *
     * @param variantCounts a map where the key represents the string in template to replace and the
     *                      value represent the number of variants to replace with.
     * @param template a template for the build script.  Strings in the format "${key}" will be
     *                 replaced if the key exists in variantCounts.
     */
    public VariantBuildScriptGenerator(Map<String, Integer> variantCounts, String template) {
        this.template = template;
        this.variantCounts = variantCounts;
    }

    /**
     * Generate the string for a build.gradle script.
     */
    public String createBuildScript() {
        String buildScript = template;
        System.out.println(template);
        for (Map.Entry<String, Integer> variantCount : variantCounts.entrySet()) {
            String variantName = variantCount.getKey();
            StringBuilder variants = new StringBuilder();
            for (int i = 0; i < variantCount.getValue(); i++) {
                variants.append(variantName);
                variants.append(i);
                variants.append("\n");
            }
            buildScript = buildScript.replace("${" + variantName + "}", variants.toString());
        }

        return buildScript;
    }
}
