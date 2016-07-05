package com.android.build.gradle.integration.component;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.NativeModelHelper;
import com.android.build.gradle.integration.common.utils.NdkHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.repository.Revision;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Integration test for platform version.
 */
public class NdkPlatformVersionTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldJniApp.builder().build())
            .useExperimentalGradleVersion(true)
            .create();


    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(project.getBuildFile(),
                "apply plugin: 'com.android.model.application'\n"
                        + "\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n"
                        + "        buildToolsVersion \"" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "\"\n"
                        + "        ndk {\n"
                        + "            moduleName \"hello-jni\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
    }

    @Test
    public void testDefaultPlatformVersion() throws IOException {
        TestFileUtils.appendToFile(project.getBuildFile(),
                "model {\n"
                        + "    android {\n"
                        + "        ndk {\n"
                        + "            platformVersion 19\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        NativeAndroidProject model =
                project.executeAndReturnModel(NativeAndroidProject.class, "assembleDebug");

        // Verify .so are built for all platform.
        File apk = project.getApk("debug");
        assertThatZip(apk).contains("lib/x86/libhello-jni.so");
        assertThatZip(apk).contains("lib/mips/libhello-jni.so");
        assertThatZip(apk).contains("lib/armeabi/libhello-jni.so");
        assertThatZip(apk).contains("lib/armeabi-v7a/libhello-jni.so");

        // 64-bits binaries will not be produced if platform version 19 is used.
        assertThatZip(apk).doesNotContain("lib/x86_64/libhello-jni.so");
        assertThatZip(apk).doesNotContain("lib/arm64-v8a/libhello-jni.so");
        assertThatZip(apk).doesNotContain("lib/mips64/libhello-jni.so");

        assertThat(model.getArtifacts()).hasSize(8); // 4 ABI * 2 variants.
        for (NativeArtifact artifact : model.getArtifacts()) {
            List<String> flags = NativeModelHelper.getFlatCFlags(model, artifact);
            Optional<String> sysrootFlag = flags.stream().filter(f -> f.contains("sysroot")).findFirst();
            assertThat(sysrootFlag.isPresent()).isTrue();
            assertThat(sysrootFlag.get()).contains("android-19");
        }
    }


    @Test
    public void testAbiSpecificPlatformVersion() throws IOException {
        TestFileUtils.appendToFile(project.getBuildFile(),
                "model {\n"
                        + "    android {\n"
                        + "        abis {\n"
                        + "            create(\"x86\") {\n"
                        + "                platformVersion \"android-19\"\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        NativeAndroidProject model = project.executeAndReturnModel(NativeAndroidProject.class, "assembleDebug");

        assertThat(model.getArtifacts()).hasSize(Abi.values().length * 2); // 4 ABI * 2 variants.
        for (NativeArtifact artifact : model.getArtifacts()) {
            List<String> flags = NativeModelHelper.getFlatCFlags(model, artifact);
            Optional<String> sysrootFlag = flags.stream().filter(f -> f.contains("sysroot")).findFirst();
            assertThat(sysrootFlag.isPresent()).isTrue();
            if (artifact.getName().endsWith("x86")) {
                assertThat(sysrootFlag.get()).contains("android-19");
            } else {
                int expected =
                        Math.min(
                                NdkHelper.getMaxPlatformSupported(project.getNdkDir()),
                                GradleTestProject.DEFAULT_COMPILE_SDK_VERSION);
                assertThat(sysrootFlag.get()).contains("android-" + expected);
            }
        }
    }
}
