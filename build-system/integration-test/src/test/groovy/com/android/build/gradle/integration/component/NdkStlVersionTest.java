package com.android.build.gradle.integration.component;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;

import org.codehaus.groovy.runtime.ResourceGroovyMethods;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;

/**
 * Test STL version.
 *
 * This test will need to be updated as we update NDK version.
 */
public class NdkStlVersionTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldJniApp.builder().useCppSource(true).build())
            .forExperimentalPlugin(true).create();

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(project.getBuildFile(),
                "apply plugin: 'com.android.model.application'\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n"
                        + "        buildToolsVersion \"" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "\"\n"
                        + "        ndk {\n"
                        + "            moduleName \"hello-jni\"\n"
                        + "            abiFilters.addAll([\"x86\", \"armeabi-v7a\", \"mips\"])\n"
                        + "            stl \"gnustl_shared\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
    }

    @Test
    public void checkDefaultStlVersion() {
        project.execute("clean", "assembleDebug");
        File cppOptions = project.file("build/tmp/compileHello-jniX86DebugSharedLibraryHello-jniMainCpp/options.txt");
        assertThat(cppOptions).containsAllOf("gnu-libstdc++/4.9/");
    }

    @Test
    public void checkCustomStlVersion() throws IOException {
        TestFileUtils.appendToFile(project.getBuildFile(),
                "apply plugin: 'com.android.model.application'\n"
                        + "model {\n"
                        + "    android {"
                        + "        ndk {\n"
                        + "            stlVersion = \"4.8\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        project.execute("clean", "assembleDebug");
        File cppOptions = project.file("build/tmp/compileHello-jniX86DebugSharedLibraryHello-jniMainCpp/options.txt");
        assertThat(cppOptions).containsAllOf("gnu-libstdc++/4.8/");
    }
}
