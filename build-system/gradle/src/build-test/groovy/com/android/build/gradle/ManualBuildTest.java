/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle;

import static com.android.SdkConstants.DOT_ANDROID_PACKAGE;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FD_RES_RAW;
import static com.android.builder.core.BuilderConstants.ANDROID_WEAR_MICRO_APK;
import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;
import static com.android.builder.model.AndroidProject.FD_OUTPUTS;
import static com.android.builder.model.AndroidProject.PROPERTY_SIGNING_KEY_ALIAS;
import static com.android.builder.model.AndroidProject.PROPERTY_SIGNING_KEY_PASSWORD;
import static com.android.builder.model.AndroidProject.PROPERTY_SIGNING_STORE_FILE;
import static com.android.builder.model.AndroidProject.PROPERTY_SIGNING_STORE_PASSWORD;
import static java.io.File.separator;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.internal.CommandLineRunner;
import com.android.ide.common.internal.LoggedErrorException;
import com.android.utils.StdLogger;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;

/**
 * Some manual tests for building projects.
 *
 * This requires an SDK, found through the ANDROID_HOME environment variable or present in the
 * Android Source tree under out/host/<platform>/sdk/... (result of 'make sdk')
 */
public class ManualBuildTest extends BuildTest {

    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFF0000FF;

    protected File manualDir;
    protected File regularDir;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        manualDir = new File(testDir, FOLDER_TEST_MANUAL);
        regularDir = new File(testDir, FOLDER_TEST_REGULAR);
    }

    public void testOverlay1Content() throws Exception {
        File project = buildProject(FOLDER_TEST_REGULAR, "overlay1", BasePlugin.GRADLE_TEST_VERSION);
        File drawableOutput = new File(project, "build/" + FD_INTERMEDIATES + "/res/debug/drawable");

        checkImageColor(drawableOutput, "no_overlay.png", GREEN);
        checkImageColor(drawableOutput, "type_overlay.png", GREEN);
    }

    public void testOverlay2Content() throws Exception {
        File project = buildProject(FOLDER_TEST_REGULAR, "overlay2", BasePlugin.GRADLE_TEST_VERSION);
        File drawableOutput = new File(project, "build/" + FD_INTERMEDIATES + "/res/one/debug/drawable");

        checkImageColor(drawableOutput, "no_overlay.png", GREEN);
        checkImageColor(drawableOutput, "type_overlay.png", GREEN);
        checkImageColor(drawableOutput, "flavor_overlay.png", GREEN);
        checkImageColor(drawableOutput, "type_flavor_overlay.png", GREEN);
        checkImageColor(drawableOutput, "variant_type_flavor_overlay.png", GREEN);
    }

    public void testOverlay3Content() throws Exception {
        File project = buildProject(FOLDER_TEST_REGULAR, "overlay3", BasePlugin.GRADLE_TEST_VERSION);
        File drawableOutput = new File(project, "build/" + FD_INTERMEDIATES + "/res/freebeta/debug/drawable");

        checkImageColor(drawableOutput, "no_overlay.png", GREEN);
        checkImageColor(drawableOutput, "debug_overlay.png", GREEN);
        checkImageColor(drawableOutput, "beta_overlay.png", GREEN);
        checkImageColor(drawableOutput, "free_overlay.png", GREEN);
        checkImageColor(drawableOutput, "free_beta_overlay.png", GREEN);
        checkImageColor(drawableOutput, "free_beta_debug_overlay.png", GREEN);
        checkImageColor(drawableOutput, "free_normal_overlay.png", RED);

        drawableOutput = new File(project, "build/" + FD_INTERMEDIATES + "/res/freenormal/debug/drawable");

        checkImageColor(drawableOutput, "no_overlay.png", GREEN);
        checkImageColor(drawableOutput, "debug_overlay.png", GREEN);
        checkImageColor(drawableOutput, "beta_overlay.png", RED);
        checkImageColor(drawableOutput, "free_overlay.png", GREEN);
        checkImageColor(drawableOutput, "free_beta_overlay.png", RED);
        checkImageColor(drawableOutput, "free_beta_debug_overlay.png", RED);
        checkImageColor(drawableOutput, "free_normal_overlay.png", GREEN);

        drawableOutput = new File(project, "build/" + FD_INTERMEDIATES + "/res/paidbeta/debug/drawable");

        checkImageColor(drawableOutput, "no_overlay.png", GREEN);
        checkImageColor(drawableOutput, "debug_overlay.png", GREEN);
        checkImageColor(drawableOutput, "beta_overlay.png", GREEN);
        checkImageColor(drawableOutput, "free_overlay.png", RED);
        checkImageColor(drawableOutput, "free_beta_overlay.png", RED);
        checkImageColor(drawableOutput, "free_beta_debug_overlay.png", RED);
        checkImageColor(drawableOutput, "free_normal_overlay.png", RED);
    }

    public void testRepo() {
        File repo = new File(manualDir, "repo");

        try {
            runTasksOn(
                    new File(repo, "util"),
                    BasePlugin.GRADLE_TEST_VERSION,
                    "clean", "uploadArchives");
            runTasksOn(
                    new File(repo, "baseLibrary"),
                    BasePlugin.GRADLE_TEST_VERSION,
                    "clean", "uploadArchives");
            runTasksOn(
                    new File(repo, "library"),
                    BasePlugin.GRADLE_TEST_VERSION,
                    "clean", "uploadArchives");
            runTasksOn(
                    new File(repo, "app"),
                    BasePlugin.GRADLE_TEST_VERSION,
                    "clean", "assemble");
        } finally {
            // clean up the test repository.
            File testRepo = new File(repo, "testrepo");
            deleteFolder(testRepo);
        }
    }

    public void testLibsManifestMerging() throws Exception {
        File project = new File(regularDir, "libsTest");
        File fileOutput = new File(project, "libapp/build/" + FD_INTERMEDIATES + "/bundles/release/AndroidManifest.xml");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "build");
        assertTrue(fileOutput.exists());
    }

    // test whether a library project has its fields obfuscated
    public void testLibMinify() throws Exception {
        File project = new File(regularDir, "libMinify");
        File fileOutput = new File(project, "build/" + FD_OUTPUTS + "/mapping/release");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "build");
        checkFile(fileOutput, "mapping.txt", new String[]{"int obfuscatedInt -> a"});
    }

    // test whether proguard.txt has been correctly merged
    public void testLibProguardConsumerFile() throws Exception {
        File project = new File(regularDir, "libProguardConsumerFiles");
        File debugFileOutput = new File(project, "build/" + FD_INTERMEDIATES + "/bundles/debug");
        File releaseFileOutput = new File(project, "build/" + FD_INTERMEDIATES + "/bundles/release");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "build");
        checkFile(debugFileOutput, "proguard.txt", new String[]{"A"});
        checkFile(releaseFileOutput, "proguard.txt", new String[]{"A", "B", "C"});
    }

    public void testShrinkResources() throws Exception {
        File project = new File(manualDir, "shrink");
        File output = new File(project, "build/" + FD_OUTPUTS);
        File intermediates = new File(project, "build/" + FD_INTERMEDIATES);

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "assembleRelease", "assembleDebug", "assembleProguardNoShrink");

        // The release target has shrinking enabled.
        // The proguardNoShrink target has proguard but no shrinking enabled.
        // The debug target has neither proguard nor shrinking enabled.

        File apkRelease = new File(output, "apk" + separator + "shrink-release-unsigned.apk");
        File apkDebug = new File(output, "apk" + separator + "shrink-debug.apk");
        File apkProguardOnly = new File(output, "apk" + separator + "shrink-proguardNoShrink-unsigned.apk");

        assertTrue(apkDebug + " is not a file", apkDebug.isFile());
        assertTrue(apkRelease + " is not a file", apkRelease.isFile());
        assertTrue(apkProguardOnly + " is not a file", apkProguardOnly.isFile());

        File compressed = new File(intermediates, "res" + separator + "resources-release-stripped.ap_");
        File uncompressed = new File(intermediates, "res" + separator + "resources-release.ap_");
        assertTrue(compressed + " is not a file", compressed.isFile());
        assertTrue(uncompressed + " is not a file", uncompressed.isFile());

        // Check that there is no shrinking in the other two targets:
        assertTrue(new File(intermediates,
                "res" + separator + "resources-debug.ap_").exists());
        assertFalse(new File(intermediates,
                "res" + separator + "resources-debug-stripped.ap_").exists());
        assertTrue(new File(intermediates,
                "res" + separator + "resources-proguardNoShrink.ap_").exists());
        assertFalse(new File(intermediates,
                "res" + separator + "resources-proguardNoShrink-stripped.ap_").exists());

        String expectedUnstrippedApk = ""
                + "AndroidManifest.xml\n"
                + "classes.dex\n"
                + "resources.arsc\n"
                + "res/layout/unused1.xml\n"
                + "res/layout/unused2.xml\n"
                + "res/drawable/unused9.xml\n"
                + "res/drawable/unused10.xml\n"
                + "res/drawable/unused11.xml\n"
                + "res/menu/unused12.xml\n"
                + "res/layout/unused13.xml\n"
                + "res/layout/used1.xml\n"
                + "res/layout/used2.xml\n"
                + "res/layout/used3.xml\n"
                + "res/layout/used4.xml\n"
                + "res/layout/used5.xml\n"
                + "res/layout/used6.xml\n"
                + "res/layout/used7.xml\n"
                + "res/layout/used8.xml\n"
                + "res/drawable/used9.xml\n"
                + "res/drawable/used10.xml\n"
                + "res/drawable/used11.xml\n"
                + "res/drawable/used12.xml\n"
                + "res/menu/used13.xml\n"
                + "res/layout/used14.xml\n"
                + "res/drawable/used15.xml\n"
                + "res/layout/used16.xml\n"
                + "res/layout/used17.xml\n"
                + "res/layout/used18.xml\n"
                + "res/layout/used19.xml\n"
                + "res/layout/used20.xml";

        String expectedStrippedApkContents = ""
                + "AndroidManifest.xml\n"
                + "classes.dex\n"
                + "resources.arsc\n"
                + "res/layout/used1.xml\n"
                + "res/layout/used2.xml\n"
                + "res/layout/used3.xml\n"
                + "res/layout/used4.xml\n"
                + "res/layout/used5.xml\n"
                + "res/layout/used6.xml\n"
                + "res/layout/used7.xml\n"
                + "res/layout/used8.xml\n"
                + "res/drawable/used9.xml\n"
                + "res/drawable/used10.xml\n"
                + "res/drawable/used11.xml\n"
                + "res/drawable/used12.xml\n"
                + "res/menu/used13.xml\n"
                + "res/layout/used14.xml\n"
                + "res/drawable/used15.xml\n"
                + "res/layout/used16.xml\n"
                + "res/layout/used17.xml\n"
                + "res/layout/used18.xml\n"
                + "res/layout/used19.xml\n"
                + "res/layout/used20.xml";

        // Should not have any unused resources in the compressed list
        assertFalse(expectedStrippedApkContents, expectedStrippedApkContents.contains("unused"));
        // Should have *all* the used resources, currently 1-20
        for (int i = 1; i <= 20; i++) {
            assertTrue("Missing used"+i + " in " + expectedStrippedApkContents,
                    expectedStrippedApkContents.contains("/used" + i + "."));
        }

        // Check that the uncompressed resources (.ap_) for the release target have everything
        // we expect
        String expectedUncompressed = expectedUnstrippedApk.replace("classes.dex\n", "");
        assertEquals(expectedUncompressed, dumpZipContents(uncompressed).trim());

        // The debug target should have everything there in the APK
        assertEquals(expectedUnstrippedApk, dumpZipContents(apkDebug));
        assertEquals(expectedUnstrippedApk, dumpZipContents(apkProguardOnly));

        // Check the compressed .ap_:
        String actualCompressed = dumpZipContents(compressed);
        String expectedCompressed = expectedStrippedApkContents.replace("classes.dex\n", "");
        assertEquals(expectedCompressed, actualCompressed);
        assertFalse(expectedCompressed, expectedCompressed.contains("unused"));
        assertEquals(expectedStrippedApkContents, dumpZipContents(apkRelease));
    }

    private static List<String> getZipPaths(File zipFile) throws IOException {
        List<String> lines = Lists.newArrayList();
        FileInputStream fis = new FileInputStream(zipFile);
        try {
            ZipInputStream zis = new ZipInputStream(fis);
            try {
                ZipEntry entry = zis.getNextEntry();
                while (entry != null) {
                    lines.add(entry.getName());
                    entry = zis.getNextEntry();
                }
            } finally {
                zis.close();
            }
        } finally {
            fis.close();
        }

        return lines;
    }

    private static String dumpZipContents(File zipFile) throws IOException {
        List<String> lines = getZipPaths(zipFile);

        // Remove META-INF statements
        ListIterator<String> iterator = lines.listIterator();
        while (iterator.hasNext()) {
            if (iterator.next().startsWith("META-INF/")) {
                iterator.remove();
            }
        }

        // Sort by base name (and numeric sort such that unused10 comes after unused9)
        final Pattern pattern = Pattern.compile("(.*[^\\d])(\\d+)\\..+");
        Collections.sort(lines, new Comparator<String>() {
            @Override
            public int compare(String line1, String line2) {
                String name1 = line1.substring(line1.lastIndexOf('/') + 1);
                String name2 = line2.substring(line2.lastIndexOf('/') + 1);
                int delta = name1.compareTo(name2);
                if (delta != 0) {
                    // Try to do numeric sort
                    Matcher match1 = pattern.matcher(name1);
                    if (match1.matches()) {
                        Matcher match2 = pattern.matcher(name2);
                        //noinspection ConstantConditions
                        if (match2.matches() && match1.group(1).equals(match2.group(1))) {
                            //noinspection ConstantConditions
                            int num1 = Integer.parseInt(match1.group(2));
                            //noinspection ConstantConditions
                            int num2 = Integer.parseInt(match2.group(2));
                            if (num1 != num2) {
                                return num1 - num2;
                            }
                        }
                    }
                    return delta;
                }
                return line1.compareTo(line2);
            }
        });

        return Joiner.on('\n').join(lines);
    }

    public void testAnnotations() throws Exception {
        File project = new File(regularDir, "extractAnnotations");
        File debugFileOutput = new File(project, "build/" + FD_INTERMEDIATES + "/annotations/debug");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "assembleDebug");
        File file = new File(debugFileOutput, "annotations.zip");

        Map<String, String> map = Maps.newHashMap();
        //noinspection SpellCheckingInspection
        map.put("com/android/tests/extractannotations/annotations.xml", ""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<root>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest ExtractTest(int, java.lang.String) 0\">\n"
                + "    <annotation name=\"android.support.annotation.IdRes\" />\n"
                + "  </item>\n"
                // This item should be removed when I start supporting @hide
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest int getHiddenMethod()\">\n"
                + "    <annotation name=\"android.support.annotation.IdRes\" />\n"
                + "  </item>\n"
                // This item should be removed when I start supporting @hide
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest int getPrivate()\">\n"
                + "    <annotation name=\"android.support.annotation.IdRes\" />\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest int getVisibility()\">\n"
                + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                + "      <val name=\"value\" val=\"{com.android.tests.extractannotations.ExtractTest.VISIBLE, com.android.tests.extractannotations.ExtractTest.INVISIBLE, com.android.tests.extractannotations.ExtractTest.GONE, 5, 17, com.android.tests.extractannotations.Constants.CONSTANT_1}\" />\n"
                + "    </annotation>\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest int resourceTypeMethod(int, int)\">\n"
                + "    <annotation name=\"android.support.annotation.StringRes\" />\n"
                + "    <annotation name=\"android.support.annotation.IdRes\" />\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest int resourceTypeMethod(int, int) 0\">\n"
                + "    <annotation name=\"android.support.annotation.DrawableRes\" />\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest int resourceTypeMethod(int, int) 1\">\n"
                + "    <annotation name=\"android.support.annotation.IdRes\" />\n"
                + "    <annotation name=\"android.support.annotation.ColorRes\" />\n"
                + "  </item>\n"
                // This item should be removed when I start supporting @hide
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest java.lang.Object getPackagePrivate()\">\n"
                + "    <annotation name=\"android.support.annotation.IdRes\" />\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest java.lang.String getStringMode(int)\">\n"
                + "    <annotation name=\"android.support.annotation.StringDef\">\n"
                + "      <val name=\"value\" val=\"{com.android.tests.extractannotations.ExtractTest.STRING_1, com.android.tests.extractannotations.ExtractTest.STRING_2, &quot;literalValue&quot;, &quot;concatenated&quot;}\" />\n"
                + "    </annotation>\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest java.lang.String getStringMode(int) 0\">\n"
                + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                + "      <val name=\"value\" val=\"{com.android.tests.extractannotations.ExtractTest.VISIBLE, com.android.tests.extractannotations.ExtractTest.INVISIBLE, com.android.tests.extractannotations.ExtractTest.GONE, 5, 17, com.android.tests.extractannotations.Constants.CONSTANT_1}\" />\n"
                + "    </annotation>\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest void checkForeignTypeDef(int) 0\">\n"
                + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                + "      <val name=\"value\" val=\"{com.android.tests.extractannotations.Constants.CONSTANT_1, com.android.tests.extractannotations.Constants.CONSTANT_2}\" />\n"
                + "      <val name=\"flag\" val=\"true\" />\n"
                + "    </annotation>\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest void resourceTypeMethodWithTypeArgs(java.util.Map&lt;java.lang.String,? extends java.lang.Number&gt;, T, int) 0\">\n"
                + "    <annotation name=\"android.support.annotation.StringRes\" />\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest void resourceTypeMethodWithTypeArgs(java.util.Map&lt;java.lang.String,? extends java.lang.Number&gt;, T, int) 1\">\n"
                + "    <annotation name=\"android.support.annotation.DrawableRes\" />\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest void resourceTypeMethodWithTypeArgs(java.util.Map&lt;java.lang.String,? extends java.lang.Number&gt;, T, int) 2\">\n"
                + "    <annotation name=\"android.support.annotation.IdRes\" />\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest void testMask(int) 0\">\n"
                + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                + "      <val name=\"value\" val=\"{0, com.android.tests.extractannotations.Constants.FLAG_VALUE_1, com.android.tests.extractannotations.Constants.FLAG_VALUE_2}\" />\n"
                + "      <val name=\"flag\" val=\"true\" />\n"
                + "    </annotation>\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest void testNonMask(int) 0\">\n"
                + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                + "      <val name=\"value\" val=\"{0, com.android.tests.extractannotations.Constants.CONSTANT_1, com.android.tests.extractannotations.Constants.CONSTANT_3}\" />\n"
                + "    </annotation>\n"
                + "  </item>\n"
                // This should be hidden when we start filtering out hidden classes on @hide!
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest.HiddenClass int getHiddenMember()\">\n"
                + "    <annotation name=\"android.support.annotation.IdRes\" />\n"
                + "  </item>\n"
                + "</root>");

        checkJar(file, map);

        // check the resulting .aar file to ensure annotations.zip inclusion.
        File archiveFile = new File(project, "build/outputs/aar/extractAnnotations-debug.aar");
        assertTrue(archiveFile.isFile());
        ZipFile archive = null;
        try {
            archive = new ZipFile(archiveFile);
            ZipEntry entry = archive.getEntry("annotations.zip");
            assertNotNull(entry);
        } finally {
            if (archive != null) {
                archive.close();
            }
        }
    }

    public void testRsEnabledAnnotations() throws IOException {
        File project = new File(regularDir, "extractRsEnabledAnnotations");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "assembleDebug");

        // check the resulting .aar file to ensure annotations.zip inclusion.
        File archiveFile = new File(project, "build/outputs/aar/extractRsEnabledAnnotations-debug.aar");
        assertTrue(archiveFile.isFile());
        ZipFile archive = null;
        try {
            archive = new ZipFile(archiveFile);
            ZipEntry entry = archive.getEntry("annotations.zip");
            assertNotNull(entry);
        } finally {
            if (archive != null) {
                archive.close();
            }
        }
    }

    public void testSimpleManifestMerger() throws IOException {
        File project = new File(manualDir, "simpleManifestMergingTask");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "manifestMerger");
    }

    public void test3rdPartyTests() throws Exception {
        // custom because we want to run deviceCheck even without devices, since we use
        // a fake DeviceProvider that doesn't use a device, but only record the calls made
        // to the DeviceProvider and the DeviceConnector.
        runTasksOn(
                new File(manualDir, "3rdPartyTests"),
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "deviceCheck");
    }

    public void testEmbedded() throws Exception {
        File project = new File(regularDir, "embedded");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", ":main:assembleRelease");

        File mainApk = new File(project, "main/build/" + FD_OUTPUTS + "/apk/main-release-unsigned.apk");

        checkJar(mainApk, Collections.<String, String>singletonMap(
                FD_RES + '/' + FD_RES_RAW + '/' + ANDROID_WEAR_MICRO_APK + DOT_ANDROID_PACKAGE,
                null));
    }

    public void testUserProvidedTestAndroidManifest() throws Exception {
        File project = new File(regularDir, "androidManifestInTest");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "assembleDebugTest");

        File testApk = new File(project, "build/" + FD_OUTPUTS + "/apk/androidManifestInTest-debug-test-unaligned.apk");

        File aapt = new File(sdkDir, "build-tools/19.1.0/aapt");

        assertTrue("Test requires build-tools 19.1.0", aapt.isFile());

        String[] command = new String[4];
        command[0] = aapt.getPath();
        command[1] = "l";
        command[2] = "-a";
        command[3] = testApk.getPath();

        CommandLineRunner commandLineRunner = new CommandLineRunner(new StdLogger(StdLogger.Level.ERROR));

        final List<String> aaptOutput = Lists.newArrayList();

        commandLineRunner.runCmdLine(command, new CommandLineRunner.CommandLineOutput() {
            @Override
            public void out(@Nullable String line) {
                if (line != null) {
                    aaptOutput.add(line);
                }
            }
            @Override
            public void err(@Nullable String line) {
                super.err(line);

            }
        }, null /*env vars*/);

        System.out.println("Beginning dump");
        boolean foundPermission = false;
        boolean foundMetadata = false;
        for (String line : aaptOutput) {
            if (line.contains("foo.permission-group.COST_MONEY")) {
                foundPermission = true;
            }
            if (line.contains("meta-data")) {
                foundMetadata = true;
            }
        }
        if (!foundPermission) {
            fail("Could not find user-specified permission group.");
        }
        if (!foundMetadata) {
            fail("Could not find meta-data under instrumentation ");
        }
    }

    public void testDensitySplits() throws Exception {
        File project = new File(regularDir, "densitySplit");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "assembleDebug");

        Map<String, VersionData> expected = Maps.newHashMapWithExpectedSize(5);
        expected.put("universal", VersionData.of(112, "version 112"));
        expected.put("mdpi",      VersionData.of(212, "version 212"));
        expected.put("hdpi",      VersionData.of(312, "version 312"));
        expected.put("xhdpi",     VersionData.of(412, "version 412"));
        expected.put("xxhdpi",    VersionData.of(512, "version 512"));

        checkVersion(project, null, expected, "densitySplit", "-debug");
    }

    public void testAbiSplits() throws Exception {
        File project = new File(regularDir, "ndkJniLib");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "app:assembleDebug");

        Map<String, VersionData> expected = Maps.newHashMapWithExpectedSize(8);
        expected.put("gingerbread-universal",        VersionData.of(1000123));
        expected.put("gingerbread-armeabi-v7a",      VersionData.of(1100123));
        expected.put("gingerbread-mips",             VersionData.of(1200123));
        expected.put("gingerbread-x86",              VersionData.of(1300123));
        expected.put("icecreamSandwich-universal",   VersionData.of(2000123));
        expected.put("icecreamSandwich-armeabi-v7a", VersionData.of(2100123));
        expected.put("icecreamSandwich-mips",        VersionData.of(2200123));
        expected.put("icecreamSandwich-x86",         VersionData.of(2300123));

        checkVersion(project, "app/", expected, "app", "-debug");
    }

    public void testPureAbiSplits() throws Exception {
        File project = new File(regularDir, "ndkJniPureSplitLib");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "app:assembleDebug");

        Map<String, VersionData> expected = Maps.newHashMapWithExpectedSize(8);
        expected.put("freeDebug-armeabi-v7a",      VersionData.of(123));
        expected.put("freeDebug-mips",             VersionData.of(123));
        expected.put("freeDebug-x86",              VersionData.of(123));
        expected.put("paidDebug-armeabi-v7a",      VersionData.of(123));
        expected.put("paidDebug-mips",             VersionData.of(123));
        expected.put("paidDebug-x86",              VersionData.of(123));

        checkVersion(project, "app/", expected, "app", null /* suffix */);
    }

    private static final class VersionData {
        static VersionData of(int code, String name) {
            VersionData versionData = new VersionData();
            versionData.code = code;
            versionData.name = name;
            return versionData;
        }

        static VersionData of(int code) {
            return of(code, null);
        }

        @Nullable
        Integer code;
        @Nullable
        String name;
    }

    private void checkVersion(
            @NonNull File project,
            @Nullable String outRoot,
            @NonNull Map<String, VersionData> expected,
            @NonNull String baseName,
            @Nullable String suffix)
            throws IOException, InterruptedException, LoggedErrorException {
        File aapt = new File(sdkDir, "build-tools/20.0.0/aapt");

        assertTrue("Test requires build-tools 20.0.0", aapt.isFile());

        String[] command = new String[4];
        command[0] = aapt.getPath();
        command[1] = "dump";
        command[2] = "badging";

        CommandLineRunner commandLineRunner = new CommandLineRunner(new StdLogger(StdLogger.Level.ERROR));

        for (Map.Entry<String, VersionData> entry : expected.entrySet()) {
            if (suffix == null) {
                suffix = "";
            }
            String path = "build/" + FD_OUTPUTS + "/apk/" + baseName + "-" + entry.getKey() + suffix + ".apk";
            if (outRoot != null) {
                path = outRoot + path;
            }

            File apk = new File(project, path);

            command[3] = apk.getPath();

            final List<String> aaptOutput = Lists.newArrayList();

            commandLineRunner.runCmdLine(command, new CommandLineRunner.CommandLineOutput() {
                @Override
                public void out(@Nullable String line) {
                    if (line != null) {
                        aaptOutput.add(line);
                    }
                }
                @Override
                public void err(@Nullable String line) {
                    super.err(line);

                }
            }, null /*env vars*/);

            Pattern p = Pattern.compile("^package: name='(.+)' versionCode='([0-9]*)' versionName='(.*)'$");

            String versionCode = null;
            String versionName = null;

            for (String line : aaptOutput) {
                Matcher m = p.matcher(line);
                if (m.matches()) {
                    versionCode = m.group(2);
                    versionName = m.group(3);
                    break;
                }
            }

            assertNotNull("Unable to determine version code", versionCode);
            assertNotNull("Unable to determine version name", versionName);

            VersionData versionData = entry.getValue();

            if (versionData.code != null) {
                assertEquals("Unexpected version code for split: " + entry.getKey(),
                        versionData.code.intValue(), Integer.parseInt(versionCode));
            }

            if (versionData.name != null) {
                assertEquals("Unexpected version code for split: " + entry.getKey(),
                        versionData.name, versionName);

            }
        }
    }

    public void testPseudolocalization() throws Exception {
        File project = new File(regularDir, "pseudolocalized");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "assembleDebug");

        File aapt = new File(sdkDir, "build-tools/21.0.0/aapt");

        assertTrue("Test requires build-tools 21.0.0", aapt.isFile());

        File apk = new File(project, "build/" + FD_OUTPUTS + "/apk/pseudolocalized-debug.apk");

        String[] command = new String[4];
        command[0] = aapt.getPath();
        command[1] = "dump";
        command[2] = "badging";
        command[3] = apk.getPath();

        CommandLineRunner commandLineRunner = new CommandLineRunner(new StdLogger(StdLogger.Level.ERROR));

        class TestOutput extends CommandLineRunner.CommandLineOutput {
            public boolean pseudolocalized = false;
            private Pattern p = Pattern.compile("^locales:.*'en[_-]XA'.*'ar[_-]XB'.*");

            @Override
            public void out(@Nullable String line) {
                if (line != null) {
                    Matcher m = p.matcher(line);
                    if (m.matches()) {
                      pseudolocalized = true;
                    }
                }
            }
            @Override
            public void err(@Nullable String line) {
                super.err(line);

            }

            public boolean getPseudolocalized() {
                return pseudolocalized;
            }
        };

        TestOutput handler = new TestOutput();
        commandLineRunner.runCmdLine(command, handler, null /*env vars*/);

        assertTrue("Pseudo locales were not added", handler.getPseudolocalized());
    }

    public void testBasicWithSigningOverride() throws Exception {
        File project = new File(regularDir, "basic");

        // add prop args for signing override.
        List<String> args = Lists.newArrayListWithExpectedSize(4);
        args.add("-P" + PROPERTY_SIGNING_STORE_FILE + "=" + new File(project, "debug.keystore").getPath());
        args.add("-P" + PROPERTY_SIGNING_STORE_PASSWORD + "=android");
        args.add("-P" + PROPERTY_SIGNING_KEY_ALIAS + "=AndroidDebugKey");
        args.add("-P" + PROPERTY_SIGNING_KEY_PASSWORD + "=android");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                args,
                Collections.<String, String>emptyMap(),
                "clean", ":assembleRelease");

        // check that the output exist. Since the filename is tried to signing/zipaligning
        // this gives us a fairly good idea about signing already.
        File releaseApk = new File(project, "build/" + FD_OUTPUTS + "/apk/basic-release.apk");
        assertTrue(releaseApk.isFile());

        // now check for signing file inside the archive.
        checkJar(releaseApk, Collections.<String,
                String>singletonMap("META-INF/CERT.RSA", null));
    }

    public void testMaxSdkVersion() throws Exception {
        File project = new File(regularDir, "maxSdkVersion");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "assembleDebug");
        checkMaxSdkVersion(
                new File(project, "build/" + FD_OUTPUTS + "/apk/maxSdkVersion-f1-debug.apk"), "21");
        checkMaxSdkVersion(
                new File(project, "build/" + FD_OUTPUTS + "/apk/maxSdkVersion-f2-debug.apk"), "19");
    }

    public void testVariantConfigurationDependencies() throws Exception {
        File project = new File(manualDir, "dependenciesWithVariants");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "assembleDebug", "assembleTest");
    }

    private void checkMaxSdkVersion(File testApk, String version)
            throws InterruptedException, LoggedErrorException, IOException {

        File aapt = new File(sdkDir, "build-tools/19.1.0/aapt");

        assertTrue("Test requires build-tools 19.1.0", aapt.isFile());

        String[] command = new String[4];
        command[0] = aapt.getPath();
        command[1] = "dump";
        command[2] = "badging";
        command[3] = testApk.getPath();

        CommandLineRunner commandLineRunner = new CommandLineRunner(new StdLogger(StdLogger.Level.ERROR));

        final List<String> aaptOutput = Lists.newArrayList();

        commandLineRunner.runCmdLine(command, new CommandLineRunner.CommandLineOutput() {
            @Override
            public void out(@Nullable String line) {
                if (line != null) {
                    aaptOutput.add(line);
                }
            }
            @Override
            public void err(@Nullable String line) {
                super.err(line);

            }
        }, null /*env vars*/);

        System.out.println("Beginning dump");
        for (String line : aaptOutput) {
            if (line.equals("maxSdkVersion:'" + version + "'")) {
                return;
            }
        }
        fail("Could not find uses-sdk:maxSdkVersion set to " + version + " in apk dump");
    }



    private static void checkImageColor(File folder, String fileName, int expectedColor)
            throws IOException {
        File f = new File(folder, fileName);
        assertTrue("File '" + f.getAbsolutePath() + "' does not exist.", f.isFile());

        BufferedImage image = ImageIO.read(f);
        int rgb = image.getRGB(0, 0);
        assertEquals(String.format("Expected: 0x%08X, actual: 0x%08X for file %s",
                expectedColor, rgb, f),
                expectedColor, rgb);
    }

    private static void checkFile(File folder, String fileName, String[] expectedContents)
            throws IOException {
        File f = new File(folder, fileName);
        assertTrue("File '" + f.getAbsolutePath() + "' does not exist.", f.isFile());

        String contents = Files.toString(f, Charsets.UTF_8);
        for (String expectedContent : expectedContents) {
            assertTrue("File '" + f.getAbsolutePath() + "' does not contain: " + expectedContent,
                contents.contains(expectedContent));
        }
    }

    private static void checkJar(File jar, Map<String, String> pathToContents)
            throws IOException {
        assertTrue("File '" + jar.getPath() + "' does not exist.", jar.isFile());
        JarInputStream zis = null;
        FileInputStream fis;
        Set<String> notFound = Sets.newHashSet();
        notFound.addAll(pathToContents.keySet());
        fis = new FileInputStream(jar);
        try {
            zis = new JarInputStream(fis);

            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                String name = entry.getName();

                String expected = pathToContents.get(name);
                if (expected != null) {
                    notFound.remove(name);
                    if (!entry.isDirectory()) {
                        byte[] bytes = ByteStreams.toByteArray(zis);
                        if (bytes != null) {
                            String contents = new String(bytes, Charsets.UTF_8).trim();
                            assertEquals("Contents in " + name + " did not match",
                                    expected, contents);
                        }
                    }
                } else if (pathToContents.keySet().contains(name)) {
                    notFound.remove(name);
                }
                entry = zis.getNextEntry();
            }
        } finally {
            fis.close();
            if (zis != null) {
                zis.close();
            }
        }

        assertTrue("Did not find the following paths in the " + jar.getPath() + " file: " +
            notFound, notFound.isEmpty());
    }
}
