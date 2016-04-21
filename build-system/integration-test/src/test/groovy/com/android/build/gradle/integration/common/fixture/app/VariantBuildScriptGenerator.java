package com.android.build.gradle.integration.common.fixture.app;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;

import java.io.IOException;

/**
 * Creates a build.gradle with the specified number of build types and product flavors.
 */
public class VariantBuildScriptGenerator {

    public static final Integer LARGE_NUMBER = 20;

    public static final Integer MEDIUM_NUMBER = 5;

    public static final Integer SMALL_NUMBER = 2;

    private int buildTypes = -1;
    private int productFlavors = -1;
    private boolean componentPlugin = false;

    public VariantBuildScriptGenerator withNumberOfBuildTypes(int buildTypes) {
        this.buildTypes = buildTypes;
        return this;
    }

    public VariantBuildScriptGenerator withNumberOfProductFlavors(int productFlavors) {
        this.productFlavors = productFlavors;
        return this;
    }

    public VariantBuildScriptGenerator forComponentPlugin() {
        componentPlugin = true;
        return this;
    }

    public String createBuildScript() {
        if (buildTypes < 0 || productFlavors < 0) {
            throw new IllegalStateException(
                    "Number of build types and product flavors must be set.");
        }
        StringBuilder script = new StringBuilder();
        if (componentPlugin) {
            script.append("apply plugin: 'com.android.model.application'\n"
                    + "model {\n");
        } else {
            script.append("apply plugin: \"com.android.application\"\n");
        }
        script.append("\n"
                + "    android {\n"
                + "        compileSdkVersion = ")
                .append(GradleTestProject.DEFAULT_COMPILE_SDK_VERSION).append("\n"
                + "        buildToolsVersion = '")
                .append(GradleTestProject.DEFAULT_BUILD_TOOL_VERSION).append("'\n"
                + "    }\n");

        script.append("    android.buildTypes {\n");
        appendVariants(script, "buildType", buildTypes);

        script.append("    }\n"
                + "    android.productFlavors {\n");

        appendVariants(script, "productFlavor", productFlavors);
        script.append("    }\n");

        if (componentPlugin) {
            script.append("}\n");
        }

        return script.toString();
    }

    public void writeBuildScript(GradleTestProject project) throws IOException {
        TestFileUtils.appendToFile(project.getBuildFile(), createBuildScript());
    }


    private void appendVariants(
            @NonNull StringBuilder script, @NonNull String name, int count) {
        if (componentPlugin) {
            for (int i = 0; i < count; i++) {
                script.append("            create('").append(name).append(i).append("')\n");
            }
        } else {
            for (int i = 0; i < count; i++) {
                script.append("            ").append(name).append(i).append("\n");
            }
        }
    }
}
