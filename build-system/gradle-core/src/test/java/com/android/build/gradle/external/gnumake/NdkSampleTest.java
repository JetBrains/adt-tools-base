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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assert_;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.external.gson.NativeBuildConfigValue;
import com.android.build.gradle.external.gson.NativeLibraryValue;
import com.android.build.gradle.truth.NativeBuildConfigValueSubject;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.truth.Truth;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;


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

    private static final ImmutableList<CommandClassifier.BuildTool> extraTestClassifiers =
            ImmutableList.of(
                new NdkBuildWarningBuildTool(),
                new NoOpBuildTool("bcc_compat"), // Renderscript
                new NoOpBuildTool("llvm-rs-cc"), // Renderscript
                new NoOpBuildTool("rm"),
                new NoOpBuildTool("cd"),
                new NoOpBuildTool("cp"),
                new NoOpBuildTool("md"),
                new NoOpBuildTool("del"),
                new NoOpBuildTool("echo.exe"),
                new NoOpBuildTool("mkdir"),
                new NoOpBuildTool("echo"),
                new NoOpBuildTool("copy"),
                new NoOpBuildTool("install"),
                new NoOpBuildTool("androideabi-strip"),
                new NoOpBuildTool("android-strip"));

    /**
     * This build tool skips warning that can be emitted by ndk-build during -n -B processing.
     * Example,
     *   Android NDK: WARNING: APP_PLATFORM android-19 is larger than android:minSdkVersion
     *   14 in {ndkPath}/samples/HelloComputeNDK/AndroidManifest.xml
     */
    static class NdkBuildWarningBuildTool implements CommandClassifier.BuildTool {
        @NonNull
        @Override
        public BuildStepInfo createCommand(@NonNull CommandLine command) {
            return new BuildStepInfo(command, Lists.newArrayList(), Lists.newArrayList());
        }

        @Override
        public boolean isMatch(@NonNull CommandLine command) {
            return command.executable.equals("Android");
        }
    }

    /**
     * This build tool recognizes a particular command and treats it as a build step with no
     * inputs and no outputs.
     */
    static class NoOpBuildTool implements CommandClassifier.BuildTool {
        @NonNull private final String executable;
        NoOpBuildTool(@NonNull String executable) {
            this.executable = executable;
        }

        @NonNull
        @Override
        public BuildStepInfo createCommand(@NonNull CommandLine command) {
            return new BuildStepInfo(command, Lists.newArrayList(), Lists.newArrayList(), false);
        }

        @Override
        public boolean isMatch(@NonNull CommandLine command) {
            return command.executable.endsWith(executable);
        }
    }


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
            @SuppressWarnings("StringBufferField")
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
                    try (BufferedReader bufferedReader = new BufferedReader(streamReader)) {
                        String line;
                        while ((line = bufferedReader.readLine()) != null) {
                            output.append(line);
                            output.append("\n");
                        }
                    }
                    //noinspection ThrowFromFinallyBlock

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

    private static NativeBuildConfigValue checkJson(String path)
            throws IOException, InterruptedException {
        return checkJson(path, SdkConstants.PLATFORM_LINUX);
    }

    private static NativeBuildConfigValue checkJson(String path, int operatingSystem)
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

            builder.addCommands(
                    "echo build command",
                    variantName,
                    variantBuildOutputText,
                    true);


            // Add extra command classifiers that are supposed to match all commands in the test.
            // The checks below well see whether there are extra commands we don't know about.
            // If there are unknown commands we need to evaluate whether they should be understood
            // by the parser or just ignored (added to extraTestClassifiers)
            List<CommandClassifier.BuildTool> testClassifiers = Lists.newArrayList();
            testClassifiers.addAll(CommandClassifier.DEFAULT_CLASSIFIERS);
            testClassifiers.addAll(extraTestClassifiers);
            List<CommandLine> commandLines = CommandLineParser.parse(variantBuildOutputText,
                    operatingSystem == SdkConstants.PLATFORM_WINDOWS);
            List<BuildStepInfo> recognized = CommandClassifier.classify(variantBuildOutputText,
                    operatingSystem == SdkConstants.PLATFORM_WINDOWS, testClassifiers);
            checkAllCommandsRecognized(commandLines, recognized);
            checkExpectedCompilerParserBehavior(commandLines);
        }

        NativeBuildConfigValue actualConfig = builder.build();
        String actualResult = new GsonBuilder()
                .setPrettyPrinting()
                .create()
                .toJson(actualConfig);
        checkOutputsHaveWhitelistedExtensions(actualConfig);

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
        assertThat(actualConfig).isEqualTo(baselineConfig);
        assertThat(actualConfig).hasUniqueLibraryNames();
        return actualConfig;
    }

    private static void checkOutputsHaveWhitelistedExtensions(NativeBuildConfigValue config) {
        checkNotNull(config.libraries);
        for (NativeLibraryValue library : config.libraries.values()) {
            // These are the three extensions that should occur. These align with what CMake does.
            checkNotNull(library.output);
            if (library.output.toString().endsWith(".so")) {
                continue;
            }
            if (library.output.toString().endsWith(".a")) {
                continue;
            }
            if (!library.output.toString().contains(".")) {
                continue;
            }
            throw new RuntimeException(
                    String.format("Library output %s had an unexpected extension", library.output));
        }
    }

    private static void checkAllCommandsRecognized(List<CommandLine> commandLines,
            List<BuildStepInfo> recognizedBuildSteps) {

        // Check that outputs occur only once
        Map<String, BuildStepInfo> outputs = Maps.newHashMap();
        for (BuildStepInfo recognizedBuildStep : recognizedBuildSteps) {
            for (String output : recognizedBuildStep.getOutputs()) {
                // Check for duplicate names
                Truth.assertThat(outputs.keySet()).doesNotContain(output);
                outputs.put(output, recognizedBuildStep);
            }
        }

        if (commandLines.size() != recognizedBuildSteps.size()) {
            // Build a set of executable commands that were classified.
            Set<String> recognizedCommandLines = Sets.newHashSet();
            for (BuildStepInfo recognizedBuildStep : recognizedBuildSteps) {
                recognizedCommandLines.add(recognizedBuildStep.getCommand().executable);
            }

            Truth.assertThat(recognizedCommandLines).containsAllIn(commandLines);
        }
    }

    // Find the compiler commands and check their parse against expected parse.
    private static void checkExpectedCompilerParserBehavior(List<CommandLine> commands) {
        for (CommandLine command : commands) {
            if (new CommandClassifier.NativeCompilerBuildTool().isMatch(command)) {
                for (String arg : command.args) {
                    if (arg.startsWith("-")) {
                        String trimmed = arg;
                        while(trimmed.startsWith("-")) {
                            trimmed = trimmed.substring(1);
                        }
                        boolean matched = false;
                        for (String withRequiredArgFlag : CompilerParser.WITH_REQUIRED_ARG_FLAGS) {
                            if (trimmed.startsWith(withRequiredArgFlag)) {
                                matched = true;
                            }
                        }

                        for (String withNoArgsFlag : CompilerParser.WITH_NO_ARG_FLAGS) {
                            if (trimmed.equals(withNoArgsFlag)) {
                                matched = true;
                            }
                        }

                        // Recognize -W style flag
                        if (trimmed.startsWith("W")) {
                            matched = true;
                        }

                        if (!matched) {
                            // If you get here, there is a new gcc or clang flag in a baseline test.
                            // For completeness, you should add this flag in CompilerParser.
                            throw new RuntimeException(
                                    "The flag " + arg + " was not a recognized compiler flag");

                        }
                    }
                }
            }
        }
    }

    @NonNull
    public static NativeBuildConfigValueSubject assertThat(@Nullable NativeBuildConfigValue project) {
        return assert_().about(NativeBuildConfigValueSubject.FACTORY).that(project);
    }

    // Related to b.android.com/216676. Same source file name produces same target name.
    @Test
    public void duplicate_source_names() throws IOException, InterruptedException {
        NativeBuildConfigValue config = checkJson("samples/duplicate-source-names");
        assertThat(config).hasExactLibrariesNamed(
                "apple-release-mips64",
                "apple-debug-mips",
                "apple-release-armeabi",
                "banana-release-arm64-v8a",
                "hello-jni-debug-armeabi",
                "hello-jni-debug-x86_64",
                "banana-debug-armeabi",
                "hello-jni-release-arm64-v8a",
                "banana-release-x86",
                "banana-debug-mips64",
                "banana-release-armeabi-v7a",
                "hello-jni-debug-x86",
                "apple-release-armeabi-v7a",
                "hello-jni-release-mips",
                "banana-release-armeabi",
                "hello-jni-debug-mips",
                "apple-release-mips",
                "hello-jni-release-mips64",
                "hello-jni-debug-armeabi-v7a",
                "banana-debug-x86_64",
                "apple-debug-arm64-v8a",
                "apple-release-x86_64",
                "apple-debug-armeabi",
                "hello-jni-release-armeabi",
                "apple-release-x86",
                "hello-jni-release-x86",
                "banana-debug-arm64-v8a",
                "hello-jni-debug-mips64",
                "hello-jni-release-x86_64",
                "banana-debug-mips",
                "apple-debug-mips64",
                "apple-debug-x86",
                "apple-release-arm64-v8a",
                "banana-debug-x86",
                "banana-release-mips64",
                "hello-jni-debug-arm64-v8a",
                "banana-debug-armeabi-v7a",
                "hello-jni-release-armeabi-v7a",
                "banana-release-x86_64",
                "apple-debug-armeabi-v7a",
                "apple-debug-x86_64",
                "banana-release-mips");
    }

    // Related to b.android.com/218397. On Windows, the wrong target name was used because it
    // was passed through File class which caused slashes to be normalized to back slash.
    @Test
    public void windows_target_name() throws IOException, InterruptedException {
        NativeBuildConfigValue config = checkJson("samples/windows-target-name",
                SdkConstants.PLATFORM_WINDOWS);
        assertThat(config).hasExactLibrariesNamed(
                "hello-jni-debug-mips",
                "hello-jni-release-mips64",
                "hello-jni-debug-armeabi-v7a",
                "hello-jni-debug-armeabi",
                "hello-jni-debug-x86_64",
                "hello-jni-release-arm64-v8a",
                "hello-jni-release-armeabi",
                "hello-jni-release-x86",
                "hello-jni-debug-mips64",
                "hello-jni-release-x86_64",
                "hello-jni-debug-arm64-v8a",
                "hello-jni-debug-x86",
                "hello-jni-release-armeabi-v7a",
                "hello-jni-release-mips");
    }

    // Related to b.android.com/214626
    @Test
    public void LOCAL_MODULE_FILENAME() throws IOException, InterruptedException {
        checkJson("samples/LOCAL_MODULE_FILENAME");
    }

    @Test
    public void include_flag() throws IOException, InterruptedException {
        checkJson("samples/include-flag");
    }

    @Test
    public void clang_example() throws IOException, InterruptedException {
        checkJson("samples/clang");
    }

    @Test
    public void ccache_example() throws IOException, InterruptedException {
        // CCache is turned on in ndk build by setting NDK_CCACHE to a path to ccache
        // executable.
        checkJson("samples/ccache");
    }

    @Test
    public void google_test_example() throws IOException, InterruptedException {
       NativeBuildConfigValue config = checkJson("samples/google-test-example");
       assertThat(config).hasExactLibraryOutputs(
               "{NDK}/debug/obj/local/arm64-v8a/libgoogletest_static.a",
               "{NDK}/debug/obj/local/arm64-v8a/sample1_unittest",
               "{NDK}/debug/obj/local/arm64-v8a/libsample1.so",
               "{NDK}/debug/obj/local/arm64-v8a/libgoogletest_main.a");
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
        NativeBuildConfigValue config = checkJson("samples/HelloComputeNDK");
        assertThat(config).hasExactLibraryOutputs(
                "{ndkPath}/samples/HelloComputeNDK/obj/local/x86/libhellocomputendk.so",
                "{ndkPath}/samples/HelloComputeNDK/libs/armeabi-v7a/librs.mono.so",
                "{ndkPath}/samples/HelloComputeNDK/obj/local/mips/libhellocomputendk.so",
                "{ndkPath}/samples/HelloComputeNDK/libs/mips/librs.mono.so",
                "{ndkPath}/samples/HelloComputeNDK/libs/x86/librs.mono.so",
                "{ndkPath}/samples/HelloComputeNDK/obj/local/armeabi-v7a/libhellocomputendk.so");
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
