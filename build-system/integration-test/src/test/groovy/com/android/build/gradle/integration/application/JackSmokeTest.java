package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.utils.AndroidVersionMatcher.thatUsesArt;
import static org.junit.Assume.assumeTrue;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.AndroidVersionMatcher;
import com.android.ddmlib.IDevice;
import com.google.common.collect.ImmutableList;

import org.gradle.api.JavaVersion;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.util.List;

/**
 * Simple Jack test for a few test project.
 */
public class JackSmokeTest {

    @Rule
    public Adb adb = new Adb();

    @ClassRule
    public static GradleTestProject basic = GradleTestProject.builder().withName("basic")
            .fromTestProject("basic").create();

    @ClassRule
    public static GradleTestProject minify = GradleTestProject.builder().withName("minify")
            .fromTestProject("minify").create();

    @ClassRule
    public static GradleTestProject multiDex = GradleTestProject.builder().withName("multiDex")
            .fromTestProject("multiDex").create();

    private static final List<String> JACK_OPTIONS = ImmutableList
            .of("-Pcom.android.build.gradle.integratonTest.useJack=true",
                    "-PCUSTOM_BUILDTOOLS=" + GradleTestProject.UPCOMING_BUILD_TOOL_VERSION);

    @Before
    public void assumeJava7() {
        assumeTrue("Jack needs java 7", JavaVersion.current().isJava7Compatible());
    }

    @AfterClass
    public static void cleanUp() {
        basic = null;
        minify = null;
        multiDex = null;
    }

    @Test
    public void assembleBasicDebug() throws Exception {
        basic.execute(JACK_OPTIONS, "clean", "assembleDebug", "assembleDebugAndroidTest");
        TruthHelper.assertThatApk(basic.getApk("debug")).contains("classes.dex");
        TruthHelper.assertThat(basic.getStdout()).contains("transformJackWithJack");
    }

    @Test
    public void assembleMinifyDebug() {
        minify.execute(JACK_OPTIONS, "clean", "assembleDebug", "assembleDebugAndroidTest");
        TruthHelper.assertThat(minify.getStdout()).contains("transformJackWithJack");
    }

    @Test
    public void assembleMultiDexDebug() {
        multiDex.execute(JACK_OPTIONS, "clean",
                "assembleIcsDebugAndroidTest", "assembleDebug", "assembleLollipopDebugAndroidTest");
        TruthHelper.assertThat(multiDex.getStdout()).contains("transformJackWithJack");
    }

    @Test
    @Category(DeviceTests.class)
    public void basicConnectedCheck() {
        basic.executeConnectedCheck(JACK_OPTIONS);
    }

    @Test
    @Category(DeviceTests.class)
    public void multiDexConnectedCheck() throws IOException {
        multiDex.execute(JACK_OPTIONS, "assembleDebug",
                "assembleIcsDebugAndroidTest",
                "assembleLollipopDebugAndroidTest");
        adb.exclusiveAccess();
        multiDex.execute(JACK_OPTIONS, "connectedCheck");
    }

    @Test
    public void minifyUnitTestsWithJavac() {
        minify.execute("testMinified");
    }

    @Test
    public void minifyUnitTestsWithJack() {
        minify.execute(JACK_OPTIONS, "clean", "testMinified");

        // Make sure javac was run.
        TruthHelper.assertThat(minify.file("build/intermediates/classes/minified")).exists();

        // Make sure jack was not run.
        TruthHelper.assertThat(minify.file("build/intermediates/jill")).doesNotExist();
    }
}
