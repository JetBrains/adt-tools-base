/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.builder.internal.compiler;

import static com.android.SdkConstants.FN_AAPT;
import static com.android.SdkConstants.FN_AIDL;
import static com.android.SdkConstants.FN_BCC_COMPAT;
import static com.android.SdkConstants.FN_DX;
import static com.android.SdkConstants.FN_DX_JAR;
import static com.android.SdkConstants.FN_RENDERSCRIPT;
import static com.android.SdkConstants.FN_ZIPALIGN;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.DexOptions;
import com.android.builder.core.ErrorReporter;
import com.android.builder.core.LibraryRequest;
import com.android.builder.model.SyncIssue;
import com.android.builder.sdk.SdkInfo;
import com.android.builder.sdk.TargetInfo;
import com.android.ide.common.blame.Message;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.ide.common.process.JavaProcessInfo;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.process.ProcessInfo;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.process.ProcessResult;
import com.android.repository.Revision;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.FileUtils;
import com.android.utils.NullLogger;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import junit.framework.TestCase;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

public class PreDexCacheTest extends TestCase {

    private static final String DEX_DATA = "**";

    private AndroidBuilder mAndroidBuilder;

    /**
     * implement a fake java process executor to intercept the call to dex and replace it
     * with something else.
     */
    private static class FakeJavaProcessExecutor implements JavaProcessExecutor {

        @NonNull
        @Override
        public ProcessResult execute(
                @NonNull JavaProcessInfo javaProcessInfo,
                @NonNull ProcessOutputHandler processOutputHandler) {

            List<String> command = javaProcessInfo.getArgs();

            ProcessException processException = null;

            try {
                // small delay to test multi-threading.
                Thread.sleep(1000);

                // input file is the last file in the command
                File input = new File(command.get(command.size() - 1));
                if (!input.isFile()) {
                    throw new FileNotFoundException(input.getPath());
                }

                // loop on the command to find --output
                String output = null;
                for (int i = 0; i < command.size(); i++) {
                    if ("--output".equals(command.get(i))) {
                        output = command.get(i + 1);
                        break;
                    }
                }

                if (output == null) {
                    throw new IOException("Failed to find output in dex commands");
                }

                // read the source content
                JarFile jarFile = new JarFile(input);
                JarEntry jarEntry = jarFile.getJarEntry("content.class");
                assert jarEntry != null;
                InputStream contentStream = jarFile.getInputStream(jarEntry);
                byte[] content  = new byte[256];
                int read = contentStream.read(content);
                contentStream.close();
                jarFile.close();

                String line = new String(content, 0, read, Charsets.UTF_8);

                // write it
                Files.write(DEX_DATA + line + DEX_DATA, new File(output), Charsets.UTF_8);
            } catch (Exception e) {
                //noinspection ThrowableInstanceNeverThrown
                processException = new ProcessException(null, e);
            }

            final ProcessException rethrow = processException;
            return new ProcessResult() {
                @NonNull
                @Override
                public ProcessResult assertNormalExitValue() throws ProcessException {
                    return this;
                }

                @Override
                public int getExitValue() {
                    return 0;
                }

                @NonNull
                @Override
                public ProcessResult rethrowFailure() throws ProcessException {
                    if (rethrow != null) {
                        throw rethrow;
                    }
                    return this;
                }
            };
        }
    }

    /**
     * Fake executor that fails to execute
     */
    private static class FailingExecutor implements JavaProcessExecutor {

        @NonNull
        @Override
        public ProcessResult execute(@NonNull JavaProcessInfo javaProcessInfo,
                @NonNull ProcessOutputHandler processOutputHandler) {
            try {
                Thread.sleep(1000);
                throw new IOException("foo");
            } catch (final Exception e) {
                return new ProcessResult() {
                    @NonNull
                    @Override
                    public ProcessResult assertNormalExitValue() throws ProcessException {
                        return this;
                    }

                    @Override
                    public int getExitValue() {
                        return 0;
                    }

                    @NonNull
                    @Override
                    public ProcessResult rethrowFailure() throws ProcessException {
                        throw new ProcessException(null, e);
                    }
                };
            }
        }
    }

    private static class FakeProcessOutputHandler implements ProcessOutputHandler {

        @NonNull
        @Override
        public ProcessOutput createOutput() {
            return null;
        }

        @Override
        public void handleOutput(@NonNull ProcessOutput processOutput) throws ProcessException {

        }
    }

    private static class FakeDexOptions implements DexOptions {

        @Override
        public boolean getIncremental() {
            return false;
        }

        @Override
        public boolean getPreDexLibraries() {
            return false;
        }

        @Override
        public boolean getJumboMode() {
            return false;
        }

        @Override
        @Nullable
        public String getJavaMaxHeapSize() {
            return null;
        }

        @Override
        @Nullable
        public Integer getThreadCount() {
            return null;
        }

        @Nullable
        @Override
        public Integer getMaxProcessCount() {
            return null;
        }

        @NonNull
        @Override
        public List<String> getAdditionalParameters() {
            return Lists.newArrayList();
        }
    }

    private static class FakeProcessExecutor implements ProcessExecutor {
        @NonNull
        @Override
        public ProcessResult execute(@NonNull ProcessInfo processInfo,
                @NonNull ProcessOutputHandler processOutputHandler) {
            throw new RuntimeException("fake");
        }
    }

    private static class FakeErrorReporter extends ErrorReporter {
        protected FakeErrorReporter(@NonNull EvaluationMode mode) {
            super(mode);
        }

        @NonNull
        @Override
        public SyncIssue handleSyncIssue(
                @Nullable String data, int type, int severity, @NonNull String msg) {
            throw new RuntimeException("fake");
        }

        @Override
        public void receiveMessage(@NonNull Message message) {
            throw new RuntimeException("fake");
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mAndroidBuilder = new AndroidBuilder(
                "testProject",
                getClass().getName(),
                new FakeProcessExecutor(),
                new FakeJavaProcessExecutor(),
                new FakeErrorReporter(ErrorReporter.EvaluationMode.STANDARD),
                new NullLogger(),
                true);

        TargetInfo targetInfo = mock(TargetInfo.class);
        when(targetInfo.getBuildTools()).thenReturn(getBuildToolInfo());

        mAndroidBuilder.setTargetInfo(
                mock(SdkInfo.class),
                targetInfo,
                ImmutableList.<LibraryRequest>of());
    }

    @Override
    protected void tearDown() throws Exception {
        File toolFolder = mAndroidBuilder.getTargetInfo().getBuildTools().getLocation();
        FileUtils.deleteFolder(toolFolder);

        PreDexCache.getCache().clear(null, null);

        super.tearDown();
    }

    public void testSinglePreDexLibrary() throws IOException, ProcessException, InterruptedException {
        String content = "Some Content";
        File input = createInputFile(content);

        File output = File.createTempFile("predex", ".jar");
        output.deleteOnExit();

        PreDexCache.getCache().preDexLibrary(
                mAndroidBuilder,
                input,
                output,
                false /*multidex*/,
                new FakeDexOptions(),
                new FakeProcessOutputHandler());

        checkOutputFile(content, output);
    }

    public void testThreadedPreDexLibrary() throws IOException, InterruptedException {
        String content = "Some Content";
        final File input = createInputFile(content);
        input.deleteOnExit();

        Thread[] threads = new Thread[3];
        final File[] outputFiles = new File[threads.length];

        final JavaProcessExecutor javaProcessExecutor = new FakeJavaProcessExecutor();
        final DexOptions dexOptions = new FakeDexOptions();

        for (int i = 0 ; i < threads.length ; i++) {
            final int ii = i;
            threads[i] = new Thread() {
                @Override
                public void run() {
                    try {
                        File output = File.createTempFile("predex", ".jar");
                        output.deleteOnExit();
                        outputFiles[ii] = output;

                        PreDexCache.getCache().preDexLibrary(
                                mAndroidBuilder,
                                input,
                                output,
                                false /*multidex*/,
                                dexOptions,
                                new FakeProcessOutputHandler());
                    } catch (Exception ignored) {

                    }
                }
            };

            threads[i].start();
        }

        // wait on the threads.
        for (Thread thread : threads) {
            thread.join();
        }

        // check the output.
        for (File outputFile : outputFiles) {
            checkOutputFile(content, outputFile);
        }

        // now check the cache
        PreDexCache cache = PreDexCache.getCache();
        assertEquals(1, cache.getMisses());
        assertEquals(threads.length - 1, cache.getHits());
    }

    public void testThreadedPreDexLibraryWithError() throws IOException, InterruptedException {
        String content = "Some Content";
        final File input = createInputFile(content);
        input.deleteOnExit();

        Thread[] threads = new Thread[3];
        final File[] outputFiles = new File[threads.length];

        final JavaProcessExecutor javaProcessExecutor = new FakeJavaProcessExecutor();
        final JavaProcessExecutor javaProcessExecutorWithError = new FailingExecutor();
        final DexOptions dexOptions = new FakeDexOptions();

        final AtomicInteger threadDoneCount = new AtomicInteger();

        for (int i = 0 ; i < threads.length ; i++) {
            final int ii = i;
            threads[i] = new Thread() {
                @Override
                public void run() {
                    try {
                        File output = File.createTempFile("predex", ".jar");
                        output.deleteOnExit();
                        outputFiles[ii] = output;

                        AndroidBuilder builder = new AndroidBuilder(
                                "testProject",
                                getClass().getName(),
                                new FakeProcessExecutor(),
                                ii == 0 ? javaProcessExecutorWithError : javaProcessExecutor,
                                new FakeErrorReporter(ErrorReporter.EvaluationMode.STANDARD),
                                new NullLogger(),
                                true);

                        PreDexCache.getCache().preDexLibrary(
                                builder,
                                input,
                                output,
                                false /*multidex*/,
                                dexOptions,
                                new FakeProcessOutputHandler());
                    } catch (Exception ignored) {

                    }
                    threadDoneCount.incrementAndGet();
                }
            };

            threads[i].start();
        }

        // wait on the threads, long enough but stop after a while
        for (Thread thread : threads) {
            thread.join(5000);
        }

        // if the test fail, we'll have two threads still blocked on the countdownlatch.
        assertEquals(3, threadDoneCount.get());
    }


    public void testReload() throws IOException, ProcessException, InterruptedException {
        final JavaProcessExecutor javaProcessExecutor = new FakeJavaProcessExecutor();
        final DexOptions dexOptions = new FakeDexOptions();

        // convert one file.
        String content = "Some Content";
        File input = createInputFile(content);

        File output = File.createTempFile("predex", ".jar");
        output.deleteOnExit();

        PreDexCache.getCache().preDexLibrary(
                mAndroidBuilder,
                input,
                output,
                false /*multidex*/,
                dexOptions,
                new FakeProcessOutputHandler());

        checkOutputFile(content, output);

        // store the cache
        File cacheXml = File.createTempFile("predex", ".xml");
        cacheXml.deleteOnExit();
        PreDexCache.getCache().clear(cacheXml, null);

        // reload.
        PreDexCache.getCache().load(cacheXml);

        // re-pre-dex into another file.
        File output2 = File.createTempFile("predex", ".jar");
        output2.deleteOnExit();

        PreDexCache.getCache().preDexLibrary(
                mAndroidBuilder,
                input,
                output2,
                false /*multidex*/,
                dexOptions,
                new FakeProcessOutputHandler());

        // check the output
        checkOutputFile(content, output2);

        // check the hit/miss
        PreDexCache cache = PreDexCache.getCache();
        assertEquals(0, cache.getMisses());
        assertEquals(1, cache.getHits());
    }

    private static File createInputFile(String content) throws IOException {
        File input = File.createTempFile("predex", ".jar");
        input.deleteOnExit();

        JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(input));
        try {
            jarOutputStream.putNextEntry(new ZipEntry("content.class"));
            jarOutputStream.write(content.getBytes(Charsets.UTF_8));
            jarOutputStream.closeEntry();
        } finally {
            jarOutputStream.close();
        }

        return input;
    }

    private static void checkOutputFile(String content, File output) throws IOException {
        List<String> lines = Files.readLines(output, Charsets.UTF_8);

        assertEquals(1, lines.size());
        assertEquals(DEX_DATA + content + DEX_DATA, lines.get(0));
    }

    /**
     * Create a fake build tool info where the dx tool actually exists (even if it's not used).
     */
    private static BuildToolInfo getBuildToolInfo() throws IOException {
        File toolDir = Files.createTempDir();

        // create a dx.jar file.
        File dx = new File(toolDir, FN_DX_JAR);
        Files.write("dx!", dx, Charsets.UTF_8);

        return new BuildToolInfo(
                new Revision(21, 0, 1),
                toolDir,
                new File(toolDir, FN_AAPT),
                new File(toolDir, FN_AIDL),
                new File(toolDir, FN_DX),
                dx,
                new File(toolDir, FN_RENDERSCRIPT),
                new File(toolDir, "include"),
                new File(toolDir, "clang-include"),
                new File(toolDir, FN_BCC_COMPAT),
                new File(toolDir, "arm-linux-androideabi-ld"),
                new File(toolDir, "aarch64-linux-android-ld"),
                new File(toolDir, "i686-linux-android-ld"),
                new File(toolDir, "mipsel-linux-android-ld"),
                new File(toolDir, FN_ZIPALIGN));
    }
}
