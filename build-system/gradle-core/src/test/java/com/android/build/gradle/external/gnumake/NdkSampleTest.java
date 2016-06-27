/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.build.gradle.external.gnumake;

import static org.junit.Assert.fail;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.external.gson.NativeBuildConfigValue;
import com.android.build.gradle.truth.NativeBuildConfigValueSubject;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.common.truth.Truth;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

public class NdkSampleTest {
    // Turn this flag to true to regenerate test baselines in the case that output has intentionally
    // changed. Should never be checked in as 'true'.
    @SuppressWarnings("FieldCanBeLocal")
    private static final boolean REGENERATE_TEST_BASELINES = false;
    // Turn this flag to true to regenerate test JSON from preexisting baselines in the case that
    // output has intentionally changed.
    @SuppressWarnings("FieldCanBeLocal")
    private static final boolean REGENERATE_TEST_JSON_FROM_TEXT = false;
    @NonNull
    private static final String THIS_TEST_FOLDER =
            "src/test/java/com/android/build/gradle/external/gnumake/";
    private static final boolean isWindows =
            SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS;

    private static class Spawner {
        private static final int THREAD_JOIN_TIMEOUT_MILLIS = 2000;

        private static Process platformExec(String command) throws IOException {
            if (System.getProperty("os.name").contains("Windows")) {
                return Runtime.getRuntime().exec(new String[]{"cmd", "/C", command});
            } else {
                return Runtime.getRuntime().exec(new String[]{"bash", "-c", command});
            }
        }

        @NonNull
        private static String spawn(String command) throws IOException, InterruptedException {
            Process proc = platformExec(command);

            // any error message?
            StreamReaderThread errorThread = new
                    StreamReaderThread(proc.getErrorStream());

            // any output?
            StreamReaderThread outputThread = new
                    StreamReaderThread(proc.getInputStream());

            // kick them off
            errorThread.start();
            outputThread.start();

            // Wait for process to finish
            proc.waitFor();

            // Wait for output capture threads to finish
            errorThread.join(THREAD_JOIN_TIMEOUT_MILLIS);
            outputThread.join(THREAD_JOIN_TIMEOUT_MILLIS);

            if (proc.exitValue() != 0) {
                System.err.println(errorThread.result());
                throw new RuntimeException(
                        String.format("Spawned process failed with code %s", proc.exitValue()));
            }

            if (errorThread.ioe != null) {
                throw new RuntimeException(
                        String.format("Problem reading stderr: %s", errorThread.ioe));
            }

            if (outputThread.ioe != null) {
                throw new RuntimeException(
                        String.format("Problem reading stdout: %s", outputThread.ioe));
            }

            return outputThread.result();
        }

        /**
         * Read an input stream off of the main thread
         */
        private static class StreamReaderThread extends Thread {
            private final InputStream is;
            private final StringBuilder output = new StringBuilder();
            @Nullable
            IOException ioe = null;

            public StreamReaderThread(InputStream is) {
                this.is = is;
            }

            @NonNull
            public String result() {
                return output.toString();
            }

            @Override
            public void run() {
                try {
                    InputStreamReader streamReader = new InputStreamReader(is);
                    BufferedReader bufferedReader = new BufferedReader(streamReader);
                    try {
                        String line;
                        while ((line = bufferedReader.readLine()) != null) {
                            output.append(line);
                            output.append("\n");
                        }
                    } finally {
                        //noinspection ThrowFromFinallyBlock
                        bufferedReader.close();
                    }
                } catch (IOException ioe) {
                    this.ioe = ioe;
                }
            }
        }
    }

    private static File getNdkPath() {
        String path = System.getenv().get("ANDROID_NDK_HOME");
        if (isWindows) {
            path = path.replace("/", "\\\\");
        }
        return new File(path).getAbsoluteFile();
    }

    private static Map<String, String> getVariantConfigs() {
        return ImmutableMap.<String, String>builder()
                .put("debug", "NDK_DEBUG=1")
                .put("release","NDK_DEBUG=0")
                .build();
    }

    @NonNull
    private static File getVariantBuildOutputFile(
            @NonNull File testPath,
            @NonNull String variant,
            int operatingSystem) {
        return new File(
                THIS_TEST_FOLDER
                        + "support-files/ndk-sample-baselines/"
                        + testPath.getName()
                        + "." + variant + "." + getOsName(operatingSystem) + ".txt");
    }

    @NonNull
    private static String getOsName(int os) {
        switch(os) {
            case SdkConstants.PLATFORM_LINUX:
                return "linux";
            case SdkConstants.PLATFORM_DARWIN:
                return "darwin";
            case SdkConstants.PLATFORM_WINDOWS:
                return "windows";
            default:
                return "unknown";
        }
    }
    @NonNull
    private static File getJsonFile(@NonNull File testPath, int operatingSystem) {
        return new File(
                THIS_TEST_FOLDER + "support-files/ndk-sample-baselines/"
                        + testPath.getName() + "." + getOsName(operatingSystem) + ".json");
    }

    @NonNull
    private static String getNdkResult(
            @NonNull File projectPath, String flags) throws IOException, InterruptedException {

        String command = String.format(getNdkPath() + "/ndk-build -B -n NDK_PROJECT_PATH=%s %s",
                projectPath.getAbsolutePath(),
                flags);
        return Spawner.spawn(command);
    }


    private static void checkJson(String path)
            throws IOException, InterruptedException {
        checkJson(path, SdkConstants.PLATFORM_LINUX);
    }

    private static void checkJson(String path, int operatingSystem)
            throws IOException, InterruptedException {

        if (isWindows) {
            path = path.replace("/", "\\");
        }
        File ndkPath = getNdkPath();
        File testPath = new File(ndkPath, path);
        Map<String, String> variantConfigs = getVariantConfigs();

        // Get the baseline config
        File baselineJsonFile = getJsonFile(testPath, operatingSystem);

        if (REGENERATE_TEST_BASELINES) {
            File directory = new File(THIS_TEST_FOLDER + "support-files/ndk-sample-baselines");
            if (!directory.exists()) {
                //noinspection ResultOfMethodCallIgnored
                directory.mkdir();
            }

            // Create the output .txt for each variant by running ndk-build
            for (String variantName : variantConfigs.keySet()) {
                String variantBuildOutputText =
                        getNdkResult(testPath, variantConfigs.get(variantName));
                variantBuildOutputText = variantBuildOutputText
                        .replace("\\", "/")
                        .replace(FileUtils.toSystemIndependentPath(ndkPath.getPath()), "{ndkPath}")
                        .replace(FileUtils.toSystemIndependentPath(testPath.getPath()), "{testPath}")
                        .replace("windows", "{platform}")
                        .replace("linux", "{platform}")
                        .replace("darwin", "{platform}")
                        .replace(THIS_TEST_FOLDER, "{test}");
                File variantBuildOutputFile = getVariantBuildOutputFile(testPath, variantName, operatingSystem);
                Files.write(variantBuildOutputText, variantBuildOutputFile, Charsets.UTF_8);
            }
        }

        // Build the expected result
        NativeBuildConfigValueBuilder builder = new NativeBuildConfigValueBuilder(testPath);
        for (String variantName : variantConfigs.keySet()) {
            File variantBuildOutputFile = getVariantBuildOutputFile(testPath, variantName, operatingSystem);
            String variantBuildOutputText = Joiner.on('\n')
                    .join(Files.readLines(variantBuildOutputFile, Charsets.UTF_8));
            builder.addCommands("echo build command", variantName, variantBuildOutputText, true);
        }
        NativeBuildConfigValue actualConfig = builder.build();
        String actualResult = new GsonBuilder()
                .setPrettyPrinting()
                .create()
                .toJson(actualConfig);

        String testPathString = testPath.toString();

        if (isWindows) {
            actualResult = actualResult.replace("/", "\\\\");
            // JSon also uses \ as escape character. For this reason, we need to double escape
            // the file path separators on Windows.
            testPathString = testPathString.replace("\\", "\\\\");
        }
        actualResult = actualResult.replace(testPathString, "{testPath}");
        actualConfig = new Gson().fromJson(actualResult, NativeBuildConfigValue.class);

        if (REGENERATE_TEST_BASELINES || !baselineJsonFile.exists() || REGENERATE_TEST_JSON_FROM_TEXT) {
            Files.write(actualResult, baselineJsonFile, Charsets.UTF_8);
        }

        // Build the baseline result.
        String baselineResult = Joiner.on('\n')
                .join(Files.readLines(baselineJsonFile, Charsets.UTF_8));

        if (isWindows) {
            baselineResult = baselineResult.replace("/", "\\\\");
        }

        NativeBuildConfigValue baselineConfig = new Gson()
                .fromJson(baselineResult, NativeBuildConfigValue.class);
        Truth.assert_().about(NativeBuildConfigValueSubject.FACTORY)
                .that(actualConfig).isEqualTo(baselineConfig);
    }

    @Test
    public void ccache_example() throws IOException, InterruptedException {
        // CCache is turned on in ndk build by setting NDK_CCACHE to a path to ccache
        // executable.
        checkJson("samples/ccache");
    }

    @Test
    public void google_test_example() throws IOException, InterruptedException {
        checkJson("samples/google-test-example");
    }

    @Test
    public void missing_include_example() throws IOException, InterruptedException {
        checkJson("samples/missing-include");
    }

    // input: support-files/ndk-sample-baselines/san-angeles.json
    @Test
    public void san_angeles() throws IOException, InterruptedException {
        checkJson("samples/san-angeles");
    }

    @Test
    public void san_angeles_windows() throws IOException, InterruptedException {
        checkJson("samples/san-angeles", SdkConstants.PLATFORM_WINDOWS);
    }

    // input: support-files/ndk-sample-baselines/Teapot.json
    @Test
    public void Teapot() throws IOException, InterruptedException {
        checkJson("samples/Teapot");
    }
    // input: support-files/ndk-sample-baselines/native-audio.json
    @Test
    public void native_audio() throws IOException, InterruptedException {
        checkJson("samples/native-audio");
    }
    // input: support-files/ndk-sample-baselines/native-codec.json
    @Test
    public void native_codec() throws IOException, InterruptedException {
        checkJson("samples/native-codec");
    }
    // input: support-files/ndk-sample-baselines/native-media.json
    @Test
    public void native_media() throws IOException, InterruptedException {
        checkJson("samples/native-media");
    }
    // input: support-files/ndk-sample-baselines/native-plasma.json
    @Test
    public void native_plasma() throws IOException, InterruptedException {
        checkJson("samples/native-plasma");
    }
    // input: support-files/ndk-sample-baselines/bitmap-plasma.json
    @Test
    public void bitmap_plasma() throws IOException, InterruptedException {
        checkJson("samples/bitmap-plasma");
    }
    // input: support-files/ndk-sample-baselines/native-activity.json
    @Test
    public void native_activity() throws IOException, InterruptedException {
        checkJson("samples/native-activity");
    }
    // input: support-files/ndk-sample-baselines/HelloComputeNDK.json
    @Test
    public void HelloComputeNDK() throws IOException, InterruptedException {
        checkJson("samples/HelloComputeNDK");
    }
    // input: support-files/ndk-sample-baselines/test-libstdc++.json
    @Test
    public void test_libstdcpp() throws IOException, InterruptedException {
        checkJson("samples/test-libstdc++");
    }
    // input: support-files/ndk-sample-baselines/hello-gl2.json
    @Test
    public void hello_gl2() throws IOException, InterruptedException {
        checkJson("samples/hello-gl2");
    }
    // input: support-files/ndk-sample-baselines/two-libs.json
    @Test
    public void two_libs() throws IOException, InterruptedException {
        checkJson("samples/two-libs");
    }
    // input: support-files/ndk-sample-baselines/module-exports.json
    @Test
    public void module_exports() throws IOException, InterruptedException {
        checkJson("samples/module-exports");
    }
}
