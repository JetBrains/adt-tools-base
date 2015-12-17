package com.android.build.gradle.integration.library
import com.android.SdkConstants
import com.android.annotations.NonNull
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.EmptyAndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.build.gradle.integration.common.truth.AbstractAndroidSubject
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.google.common.base.Joiner
import com.google.common.io.Files
import org.apache.commons.io.FileUtils
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import java.util.zip.ZipEntry
import java.util.zip.ZipFile

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatAar
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue
/**
  * Integration test to check that libraries included directly as jar files are correctly handled
  * when using proguard.
 */
class ProguardAarPackagingTest {

    static public AndroidTestApp testApp = HelloWorldApp.noBuildFile()
    static public AndroidTestApp libraryInJar = new EmptyAndroidTestApp()

    static {
        TestSourceFile oldHelloWorld = testApp.getFile("HelloWorld.java")
        testApp.removeFile(oldHelloWorld)
        testApp.addFile(new TestSourceFile(oldHelloWorld.path, oldHelloWorld.name, """\
package com.example.helloworld;

import com.example.libinjar.LibInJar;

import android.app.Activity;
import android.os.Bundle;

public class HelloWorld extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        LibInJar.method();
    }
}
"""))

        testApp.addFile(new TestSourceFile("", "config.pro", "-keeppackagenames"));

        // Create simple library jar.
        libraryInJar.addFile(new TestSourceFile(
                "src/main/java/com/example/libinjar","LibInJar.java", """\
package com.example.libinjar;

public class LibInJar {
    public static void method() {
        throw new UnsupportedOperationException("Not implemented");
    }
}
"""))
    }

    @ClassRule
    static public GradleTestProject androidProject =
            GradleTestProject.builder().withName("mainProject").fromTestApp(testApp).create()
    @ClassRule
    static public GradleTestProject libraryInJarProject =
            GradleTestProject.builder().withName("libInJar").fromTestApp(libraryInJar).create()

    @BeforeClass
    static public void setUp() {
        // Create android test application
        androidProject.getBuildFile() << """\
apply plugin: 'com.android.library'

dependencies {
    compile fileTree(dir: 'libs', include: '*.jar')
}

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"

    buildTypes {
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'config.pro'
        }
    }
}
"""

        libraryInJarProject.buildFile << "apply plugin: 'java'"
        libraryInJarProject.execute("assemble")

        // Copy the generated jar into the android project.
        androidProject.file("libs").mkdirs()
        String libInJarName = Joiner.on(File.separatorChar)
                .join("build", "libs", libraryInJarProject.getName() + SdkConstants.DOT_JAR)
        FileUtils.copyFile(
                libraryInJarProject.file(libInJarName),
                androidProject.file("libs/libinjar.jar"))
    }

    @AfterClass
    static void cleanUp() {
        androidProject = null
        libraryInJarProject = null
    }

    @Test
    public void "check debug AAR packaging"() {
        androidProject.execute("assembleDebug")

        // check that the classes from the local jars are still in a local jar
        assertThatAar(androidProject.getAar("debug")).containsClass(
                "Lcom/example/libinjar/LibInJar;",
                AbstractAndroidSubject.ClassFileScope.SECONDARY)

        // check that it's not in the main class file.
        assertThatAar(androidProject.getAar("debug")).doesNotContainClass(
                "Lcom/example/libinjar/LibInJar;",
                AbstractAndroidSubject.ClassFileScope.MAIN)
    }

    @Test
    public void "check release AAR packaging"() {
        androidProject.execute("assembleRelease")

        // check that the classes from the local jars are in the main class file
        assertThatAar(androidProject.getAar("release")).containsClass(
                "Lcom/example/libinjar/a;",
                AbstractAndroidSubject.ClassFileScope.MAIN)

        // check that it's not in any local jar
        assertThatAar(androidProject.getAar("release")).doesNotContainClass(
                "Lcom/example/libinjar/LibInJar;",
                AbstractAndroidSubject.ClassFileScope.SECONDARY)
    }
}
