package com.android.build.gradle.integration.common.fixture.app;

import com.google.common.collect.Maps;

import java.util.Map;

import groovy.lang.Closure;

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

    public static final Integer MEDIUM_NUMBER = 15;

    public static final Integer SMALL_NUMBER = 2;

    private final String template;

    private final Map<String, Integer> variantCounts;

    private final Map<String, Closure<String>> variantPostProcessors = Maps.newHashMap();

    /**
     * Create a VariantBuildScriptGenerator
     *
     * @param variantCounts a map where the key represents the string in template to replace and the
     *                      value represent the number of variants to replace with.
     * @param template a template for the build script.  Strings in the format "${key}" will be
     *                 replaced if the key exists in variantCounts.
     */
    public VariantBuildScriptGenerator(
            Map<String, Integer> variantCounts,
            String template) {
        this.template = template;
        this.variantCounts = variantCounts;
    }

    /**
     * Add a post processor to customize the output format of a specified variant.
     *
     * @param variant Name of the variant type.
     * @param postProcessor A Closure that accept the variant type name as String and return the
     *                      formatted String.
     */
    public void addPostProcessor(String variant, Closure<String> postProcessor) {
        variantPostProcessors.put(variant, postProcessor);
    }

    /**
     * Generate the string for a build.gradle script.
     */
    public String createBuildScript() {
        String buildScript = template;
        for (Map.Entry<String, Integer> variantCount : variantCounts.entrySet()) {
            String variantName = variantCount.getKey();
            StringBuilder variants = new StringBuilder();
            for (int i = 0; i < variantCount.getValue(); i++) {
                Closure<String> postProcessor = variantPostProcessors.get(variantName);
                if (postProcessor == null) {
                    variants.append(variantName);
                    variants.append(i);
                } else {
                    variants.append(postProcessor.call(variantName + i));
                }
                variants.append("\n");
            }
            buildScript = buildScript.replace("${" + variantName + "}", variants.toString());
        }

        return buildScript;
    }
}
