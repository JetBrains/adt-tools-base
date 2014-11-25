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
import static java.io.File.separatorChar;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.core.ApkInfoParser;
import com.android.ide.common.internal.CommandLineRunner;
import com.android.ide.common.internal.LoggedErrorException;
import com.android.utils.StdLogger;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
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

    protected File sampleDir;
    protected File testProjectDir;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        sampleDir = new File(testDir, FOLDER_TEST_PROJECTS);
        testProjectDir = new File(testDir, FOLDER_TEST_SAMPLES);
    }

    public void testOverlay1Content() throws Exception {
        File project = buildProject(FOLDER_TEST_SAMPLES, "overlay1", BasePlugin.GRADLE_TEST_VERSION);
        File drawableOutput = new File(project, "build/" + FD_INTERMEDIATES + "/res/debug/drawable");

        checkImageColor(drawableOutput, "no_overlay.png", GREEN);
        checkImageColor(drawableOutput, "type_overlay.png", GREEN);
    }

    public void testOverlay2Content() throws Exception {
        File project = buildProject(FOLDER_TEST_SAMPLES, "overlay2", BasePlugin.GRADLE_TEST_VERSION);
        File drawableOutput = new File(project, "build/" + FD_INTERMEDIATES + "/res/one/debug/drawable");

        checkImageColor(drawableOutput, "no_overlay.png", GREEN);
        checkImageColor(drawableOutput, "type_overlay.png", GREEN);
        checkImageColor(drawableOutput, "flavor_overlay.png", GREEN);
        checkImageColor(drawableOutput, "type_flavor_overlay.png", GREEN);
        checkImageColor(drawableOutput, "variant_type_flavor_overlay.png", GREEN);
    }

    public void testOverlay3Content() throws Exception {
        File project = buildProject(FOLDER_TEST_SAMPLES, "overlay3", BasePlugin.GRADLE_TEST_VERSION);
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

    public void testLibsManifestMerging() throws Exception {
        File project = new File(testProjectDir, "libsTest");
        File fileOutput = new File(project, "libapp/build/" + FD_INTERMEDIATES + "/bundles/release/AndroidManifest.xml");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "build");
        assertTrue(fileOutput.exists());
    }

    // test whether a library project has its fields obfuscated
    public void testLibMinify() throws Exception {
        File project = new File(testProjectDir, "libMinify");
        File fileOutput = new File(project, "build/" + FD_OUTPUTS + "/mapping/release");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "build");
        checkFile(fileOutput, "mapping.txt", new String[]{"int obfuscatedInt -> a"});
    }

    // test whether proguard.txt has been correctly merged
    public void testLibProguardConsumerFile() throws Exception {
        File project = new File(testProjectDir, "libProguardConsumerFiles");
        File debugFileOutput = new File(project, "build/" + FD_INTERMEDIATES + "/bundles/debug");
        File releaseFileOutput = new File(project, "build/" + FD_INTERMEDIATES + "/bundles/release");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "build");
        checkFile(debugFileOutput, "proguard.txt", new String[]{"A"});
        checkFile(releaseFileOutput, "proguard.txt", new String[]{"A", "B", "C"});
    }

    public void testSimpleManifestMerger() throws IOException {
        File project = new File(sampleDir, "simpleManifestMergingTask");

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
                new File(sampleDir, "3rdPartyTests"),
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "deviceCheck");
    }

    public void testEmbedded() throws Exception {
        File project = new File(testProjectDir, "embedded");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", ":main:assemble");

        File mainOut = new File(project, "main/build/" + FD_OUTPUTS + "/apk");
        String embeddedApkPath = FD_RES + '/' + FD_RES_RAW + '/' + ANDROID_WEAR_MICRO_APK + DOT_ANDROID_PACKAGE;

        File aapt = new File(sdkDir, "build-tools/20.0.0/aapt");
        assertTrue("Test requires build-tools 20.0.0", aapt.isFile());
        CommandLineRunner commandLineRunner = new CommandLineRunner(new StdLogger(StdLogger.Level.ERROR));
        ApkInfoParser parser = new ApkInfoParser(aapt, commandLineRunner);

        // each micro app has a different version name to distinguish them from one another.
        // here we record what we expect from which.
        String[][] variantData = new String[][] {
                new String[] { "main-flavor1-release-unsigned.apk", "flavor1" },
                new String[] { "main-flavor2-release-unsigned.apk", "default" },
                new String[] { "main-flavor1-custom-unsigned.apk",  "custom" },
                new String[] { "main-flavor2-custom-unsigned.apk", "custom" },
                new String[] { "main-flavor1-debug.apk", null },
                new String[] { "main-flavor2-debug.apk", null },
        };

        for (String[] data : variantData) {
            File fullApk = new File(mainOut, data[0]);
            File embeddedApk = findAndExtractFromZip(fullApk, embeddedApkPath);

            if (data[1] == null) {
                assertNull("Expected no embedded app for " + data[0], embeddedApk);
                break;
            } else {
                assertNotNull("Failed to find embedded micro app for " + data[0], embeddedApk);
            }

            // check for the versionName
            ApkInfoParser.ApkInfo apkInfo = parser.parseApk(embeddedApk);
            assertEquals("Wrong version name for app embedded in " + data[0], data[1], apkInfo.getVersionName());
        }
    }

    public void testUserProvidedTestAndroidManifest() throws Exception {
        File project = new File(testProjectDir, "androidManifestInTest");

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
        File project = new File(testProjectDir, "densitySplit");

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
        File project = new File(testProjectDir, "ndkJniLib");

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
        File project = new File(testProjectDir, "ndkJniPureSplitLib");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "app:assembleDebug");

        Map<String, VersionData> expected = Maps.newHashMapWithExpectedSize(8);
        expected.put("free-debug_armeabi-v7a",      VersionData.of(123));
        expected.put("free-debug_mips",             VersionData.of(123));
        expected.put("free-debug_x86",              VersionData.of(123));
        expected.put("paid-debug_armeabi-v7a",      VersionData.of(123));
        expected.put("paid-debug_mips",             VersionData.of(123));
        expected.put("paid-debug_x86",              VersionData.of(123));

        checkVersion(project, "app/", expected, "app", null /* suffix */);
    }

    public void testManifestMergerReports() throws Exception {
        File project = new File(testProjectDir, "flavors");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "assemble");

        File file = new File(project, "build/" + FD_OUTPUTS + "/apk/");
        File[] reports = file.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.getName().startsWith("manifest-merger");
            }
        });
        assertEquals(8, reports.length);
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

        CommandLineRunner commandLineRunner = new CommandLineRunner(new StdLogger(StdLogger.Level.ERROR));

        ApkInfoParser parser = new ApkInfoParser(aapt, commandLineRunner);

        for (Map.Entry<String, VersionData> entry : expected.entrySet()) {
            if (suffix == null) {
                suffix = "";
            }
            String path = "build/" + FD_OUTPUTS + "/apk/" + baseName + "-" + entry.getKey() + suffix + ".apk";
            if (outRoot != null) {
                path = outRoot + path;
            }

            File apk = new File(project, path);

            ApkInfoParser.ApkInfo apkInfo = parser.parseApk(apk);

            VersionData versionData = entry.getValue();

            if (versionData.code != null) {
                assertEquals("Unexpected version code for split: " + entry.getKey(),
                        versionData.code, apkInfo.getVersionCode());
            }

            if (versionData.name != null) {
                assertEquals("Unexpected version code for split: " + entry.getKey(),
                        versionData.name, apkInfo.getVersionName());
            }
        }
    }

    public void testPseudolocalization() throws Exception {
        File project = new File(testProjectDir, "pseudolocalized");

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
        File project = new File(testProjectDir, "basic");

        // add prop args for signing override.
        List<String> args = Lists.newArrayListWithExpectedSize(4);
        args.add("-P" + PROPERTY_SIGNING_STORE_FILE + "=" + new File(project, "debug.keystore")
                .getPath());
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
        File project = new File(testProjectDir, "maxSdkVersion");

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
        File project = new File(sampleDir, "dependenciesWithVariants");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "assembleDebug", "assembleTest");
    }

    public void testLegacyMultiDex() throws Exception {
        File project = new File(testProjectDir, "multiDex");

        runTasksOn(project, BasePlugin.GRADLE_TEST_VERSION, "assembleIcsDebug");

        // manually inspcet the apk to ensure that the classes.dex that was created is the same
        // one in the apk. This tests that the packaging didn't rename the multiple dex files
        // around when we packaged them.
        File classesDex = new File(project, "build/" + FD_INTERMEDIATES + "/dex/ics/debug/classes.dex");
        File apk = new File(project, "build/" + FD_OUTPUTS + "/apk/multiDex-ics-debug.apk");

        compareApkEntry(apk, "classes.dex", classesDex);
    }

    public void testMultiDexDontObfuscate() throws Exception {
        File project = new File(testProjectDir, "multiDex");
        runTasksOn(project, BasePlugin.GRADLE_TEST_VERSION, "assembleIcsProguard");
    }

    public void testRenamedApk() throws Exception {
        File project = new File(testProjectDir, "renamedApk");
        runTasksOn(project, BasePlugin.GRADLE_TEST_VERSION, "assembleDebug");

        File output = new File(project, "build");
        File debugApk = new File(output, "debug.apk");

        assertTrue("Check output file: " + debugApk.getName(), debugApk.isFile());
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

    @Nullable
    private static File findAndExtractFromZip(@NonNull File zipFile, @NonNull String entryPath) throws IOException {
        ZipFile zip = null;
        try {
            zip = new ZipFile(zipFile);
            ZipEntry entry = zip.getEntry(entryPath);
            if (entry == null) {
                return null;
            }

            // extract the file
            File apk = File.createTempFile("findAndExtractFromZip", "apk");
            apk.deleteOnExit();
            Files.asByteSink(apk).writeFrom(zip.getInputStream(entry));
            return apk;
        } catch (IOException e) {
            throw new IOException("Failed to open " + zipFile, e);
        } finally {
            if (zip != null) {
                zip.close();
            }
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

    private static void compareApkEntry(
            @NonNull File jar,
            @NonNull String pathInZip,
            @NonNull File compareTo) throws IOException {
        assertTrue("File '" + jar.getPath() + "' does not exist.", jar.isFile());
        JarInputStream zis = null;
        FileInputStream fis;

        fis = new FileInputStream(jar);
        try {
            zis = new JarInputStream(fis);

            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                String name = entry.getName();

                if (pathInZip.equals(name)) {
                    if (!entry.isDirectory()) {
                        byte[] bytes = ByteStreams.toByteArray(zis);
                        if (bytes != null) {
                            ByteSource actual = ByteSource.wrap(bytes);
                            ByteSource expected = Files.asByteSource(compareTo);

                            assertTrue(expected.contentEquals(actual));

                            return;
                        }
                    }
                }
                entry = zis.getNextEntry();
            }
        } finally {
            fis.close();
            if (zis != null) {
                zis.close();
            }
        }

        fail("Did not find the following paths in the " + jar.getPath() + " file: " + pathInZip);
    }
}
