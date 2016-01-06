package com.android.build.gradle.integration.component;

import com.android.build.gradle.OptionalCompilationStep;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.google.common.base.Joiner;

import org.codehaus.groovy.runtime.ResourceGroovyMethods;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Simple test to ensure component model plugin do not crash when instant run is enabled.
 */
public class ComponentInstantRunTest {

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(project.getBuildFile(),
                "apply plugin: \"com.android.model.application\"\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n"
                        + "        buildToolsVersion \"" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "\"\n"
                        + "    }\n"
                        + "}\n");
    }

    @Test
    public void basicAssemble() {
        project.execute(getInstantRunArgs(), "assembleDebug");
        TruthHelper.assertThat(project.getApk("debug")).exists();
    }

    private static List<String> getInstantRunArgs(OptionalCompilationStep... flags) {
        String property = "-P" + AndroidProject.OPTIONAL_COMPILATION_STEPS + "="
                + OptionalCompilationStep.INSTANT_DEV + "," + Joiner.on(",").join(flags);
        return Collections.singletonList(property);
    }

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.noBuildFile()).forExperimentalPlugin(true).withoutNdk()
            .create();
}
