package com.android.build.gradle.integration.common.fixture.app;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.TestProject;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * A TestProject containing multiple TestProject as modules.
 */
public class MultiModuleTestProject implements TestProject {

    private Map<String, TestProject> subprojects;

    /**
     * Creates a MultiModuleTestProject.
     *
     * @param subprojects a map with gradle project path as key and the corresponding TestProject as
     *                    value.
     */
    public MultiModuleTestProject(Map<String, TestProject> subprojects) {
        this.subprojects = Maps.newHashMap(subprojects);
    }

    /**
     * Creates a MultiModuleTestProject with multiple subproject of the same TestProject.
     *
     * @param baseName Base name of the subproject.  Actual project name will be baseName + index.
     * @param subproject A TestProject.
     * @param count Number of subprojects to create.
     */
    public MultiModuleTestProject(String baseName, TestProject subproject, int count) {
        subprojects = Maps.newHashMapWithExpectedSize(count);
        for (int i = 0; i < count; i++) {
            subprojects.put(baseName + i, subproject);
        }
    }

    /**
     * Return the test project with the given project path.
     */
    public TestProject getSubproject(String subprojectPath) {
        return subprojects.get(subprojectPath);
    }

    @Override
    public void write(
            @NonNull final File projectDir,
            @Nullable final String buildScriptContent)  throws IOException {
        for (Map.Entry<String, ? extends TestProject> entry : subprojects.entrySet()) {
            String subprojectPath = entry.getKey();
            TestProject subproject = entry.getValue();
            File subprojectDir = new File(projectDir, convertGradlePathToDirectory(subprojectPath));
            if (!subprojectDir.exists()) {
                subprojectDir.mkdirs();
                assert subprojectDir.isDirectory();
            }
            subproject.write(subprojectDir, null);
        }

        StringBuilder builder = new StringBuilder();
        for (String subprojectName : subprojects.keySet()) {
            builder.append("include '").append(subprojectName).append("'\n");
        }
        Files.write(builder.toString(),
                new File(projectDir, "settings.gradle"),
                Charset.defaultCharset());

        Files.write(buildScriptContent,
                new File(projectDir, "build.gradle"),
                Charset.defaultCharset());
    }

    @Override
    public boolean containsFullBuildScript() {
        return false;
    }

    private static String convertGradlePathToDirectory(String gradlePath) {
        return gradlePath.replace(":", "/");
    }
}
