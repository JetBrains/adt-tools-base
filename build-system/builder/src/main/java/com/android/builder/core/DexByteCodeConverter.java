/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.builder.core;

import static com.android.SdkConstants.DOT_CLASS;
import static com.android.SdkConstants.DOT_DEX;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.concurrency.GuardedBy;
import com.android.builder.internal.compiler.DexWrapper;
import com.android.builder.sdk.TargetInfo;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.ide.common.process.JavaProcessInfo;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.process.ProcessResult;
import com.android.repository.Revision;
import com.android.utils.ILogger;
import com.android.utils.SdkUtils;
import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Java To Dex bytecode converter.
 */
public class DexByteCodeConverter {

    /**
     * Amount of heap size that an "average" project needs for dexing in-process.
     */
    private static final long DEFAULT_DEX_HEAP_SIZE = 1024 * 1024 * 1024; // 1 GiB

    /**
     * Approximate amount of heap space necessary for non-dexing steps of the build process.
     */
    @VisibleForTesting
    static final long NON_DEX_HEAP_SIZE = 512 * 1024 * 1024; // 0.5 GiB

    private static final Object LOCK_FOR_DEX = new Object();

    /**
     * Default number of dx "instances" running at once.
     *
     * <p>Remember to update the DSL javadoc when changing the default value.
     */
    private static final AtomicInteger DEX_PROCESS_COUNT = new AtomicInteger(4);

    /**
     * {@link ExecutorService} used to run all dexing code (either in-process or out-of-process).
     * Size of the underlying thread pool limits the number of parallel dex "invocations", even
     * though every invocation can spawn many threads, depending on dexing options.
     */
    @GuardedBy("LOCK_FOR_DEX")
    private static ExecutorService sDexExecutorService = null;

    private final boolean mVerboseExec;
    private final JavaProcessExecutor mJavaProcessExecutor;
    private final TargetInfo mTargetInfo;
    private final ILogger mLogger;
    private Boolean mIsDexInProcess = null;


    public DexByteCodeConverter(ILogger logger, TargetInfo targetInfo,
            JavaProcessExecutor javaProcessExecutor,
            boolean verboseExec) {
        mLogger = logger;
        mTargetInfo = targetInfo;
        mJavaProcessExecutor = javaProcessExecutor;
        mVerboseExec = verboseExec;
    }

    /**
     * Converts the bytecode to Dalvik format
     * @param inputs the input files
     * @param outDexFolder the location of the output folder
     * @param dexOptions dex options
     * @throws IOException
     * @throws InterruptedException
     * @throws ProcessException
     */
    public void convertByteCode(
            @NonNull Collection<File> inputs,
            @NonNull File outDexFolder,
            boolean multidex,
            @Nullable File mainDexList,
            @NonNull DexOptions dexOptions,
            boolean optimize,
            @NonNull ProcessOutputHandler processOutputHandler)
            throws IOException, InterruptedException, ProcessException {checkNotNull(inputs, "inputs cannot be null.");
        checkNotNull(outDexFolder, "outDexFolder cannot be null.");
        checkNotNull(dexOptions, "dexOptions cannot be null.");
        checkArgument(outDexFolder.isDirectory(), "outDexFolder must be a folder");
        checkState(mTargetInfo != null,
                "Cannot call convertByteCode() before setTargetInfo() is called.");

        ImmutableList.Builder<File> verifiedInputs = ImmutableList.builder();
        for (File input : inputs) {
            if (checkLibraryClassesJar(input)) {
                verifiedInputs.add(input);
            }
        }

        DexProcessBuilder builder = new DexProcessBuilder(outDexFolder);

        builder.setVerbose(mVerboseExec)
                .setNoOptimize(!optimize)
                .setMultiDex(multidex)
                .setMainDexList(mainDexList)
                .addInputs(verifiedInputs.build());

        runDexer(builder, dexOptions, processOutputHandler);
    }

    public void runDexer(
            @NonNull final DexProcessBuilder builder,
            @NonNull final DexOptions dexOptions,
            @NonNull final ProcessOutputHandler processOutputHandler)
            throws ProcessException, IOException, InterruptedException {
        initDexExecutorService(dexOptions);

        if (shouldDexInProcess(dexOptions, mTargetInfo.getBuildTools().getRevision())) {
            dexInProcess(builder, dexOptions, processOutputHandler);
        } else {
            dexOutOfProcess(builder, dexOptions, processOutputHandler);
        }
    }

    private void dexInProcess(
            @NonNull final DexProcessBuilder builder,
            @NonNull final DexOptions dexOptions,
            @NonNull final ProcessOutputHandler outputHandler)
            throws IOException, ProcessException {
        final String submission = Joiner.on(',').join(builder.getInputs());
        mLogger.info("Dexing in-process : %s", submission);
        try {
            sDexExecutorService.submit(() -> {
                Stopwatch stopwatch = Stopwatch.createStarted();
                ProcessResult result = DexWrapper.run(builder, dexOptions, outputHandler);
                result.assertNormalExitValue();
                mLogger.info("Dexing %s took %s.", submission, stopwatch.toString());
                return null;
            }).get();
        } catch (Exception e) {
            throw new ProcessException(e);
        }
    }

    private void dexOutOfProcess(
            @NonNull final DexProcessBuilder builder,
            @NonNull final DexOptions dexOptions,
            @NonNull final ProcessOutputHandler processOutputHandler)
            throws ProcessException, InterruptedException {
        final String submission = Joiner.on(',').join(builder.getInputs());
        mLogger.info("Dexing out-of-process : %s", submission);
        try {
            Callable<Void> task = () -> {
                JavaProcessInfo javaProcessInfo =
                        builder.build(mTargetInfo.getBuildTools(), dexOptions);
                ProcessResult result =
                        mJavaProcessExecutor.execute(javaProcessInfo, processOutputHandler);
                result.rethrowFailure().assertNormalExitValue();
                return null;
            };

            Stopwatch stopwatch = Stopwatch.createStarted();
            // this is a hack, we always spawn a new process for dependencies.jar so it does
            // get built in parallel with the slices, this is only valid for InstantRun mode.
            if (submission.contains("dependencies.jar")) {
                task.call();
            } else {
                sDexExecutorService.submit(task).get();
            }
            mLogger.info("Dexing %s took %s.", submission, stopwatch.toString());
        } catch (Exception e) {
            throw new ProcessException(e);
        }
    }


    private void initDexExecutorService(@NonNull DexOptions dexOptions) {
        synchronized (LOCK_FOR_DEX) {
            if (sDexExecutorService == null) {
                if (dexOptions.getMaxProcessCount() != null) {
                    DEX_PROCESS_COUNT.set(dexOptions.getMaxProcessCount());
                }
                mLogger.info(
                        "Allocated dexExecutorService of size %d.",
                        DEX_PROCESS_COUNT.get());
                sDexExecutorService = Executors.newFixedThreadPool(DEX_PROCESS_COUNT.get());
            } else {
                // check whether our executor service has the same number of max processes as
                // this module requests, and print a warning if necessary.
                if (dexOptions.getMaxProcessCount() != null
                        && dexOptions.getMaxProcessCount() != DEX_PROCESS_COUNT.get()) {
                    mLogger.warning(
                            "dexOptions is specifying a maximum number of %1$d concurrent dx processes,"
                                    + " but the Gradle daemon was initialized with %2$d.\n"
                                    + "To initialize with a different maximum value,"
                                    + " first stop the Gradle daemon by calling ‘gradlew —-stop’.",
                            dexOptions.getMaxProcessCount(),
                            DEX_PROCESS_COUNT.get());
                }
            }
        }
    }

    /**
     * Determine whether to dex in process.
     */
    @VisibleForTesting
    synchronized boolean shouldDexInProcess(
            @NonNull DexOptions dexOptions,
            @NonNull Revision buildToolsVersion) {
        if (mIsDexInProcess != null) {
            return mIsDexInProcess;
        }
        if (!dexOptions.getDexInProcess()) {
            mIsDexInProcess = false;
            return false;
        }
        if (buildToolsVersion.compareTo(DexProcessBuilder.FIXED_DX_MERGER) < 0) {
            // We substitute Dex > 23.0.2 with the local implementation.
            mLogger.warning("Running dex in-process requires build tools %1$s.\n"
                            + "For faster builds update this project to use the latest build tools.",
                    DexProcessBuilder.FIXED_DX_MERGER.toShortString());
            mIsDexInProcess = false;
            return false;
        }

        // Requested memory for dex.
        long requestedHeapSize;
        if (dexOptions.getJavaMaxHeapSize() != null) {
            Optional<Long> heapSize = parseSizeToBytes(dexOptions.getJavaMaxHeapSize());
            if (heapSize.isPresent()) {
                requestedHeapSize = heapSize.get();
            } else {
                mLogger.warning(
                        "Unable to parse dex options size parameter '%1$s', assuming %2$s bytes.",
                        dexOptions.getJavaMaxHeapSize(),
                        DEFAULT_DEX_HEAP_SIZE);
                requestedHeapSize = DEFAULT_DEX_HEAP_SIZE;
            }
        } else {
            requestedHeapSize = DEFAULT_DEX_HEAP_SIZE;
        }
        // Approximate heap size requested.
        long requiredHeapSizeHeuristic = requestedHeapSize + NON_DEX_HEAP_SIZE;
        // Get the heap size defined by the user. This value will be compared with
        // requiredHeapSizeHeuristic, which we suggest the user set in their gradle.properties file.
        long maxMemory = getUserDefinedHeapSize();

        if (requiredHeapSizeHeuristic > maxMemory) {
            String dexOptionsComment = "";
            if (dexOptions.getJavaMaxHeapSize() != null) {
                dexOptionsComment = String.format(
                        " (based on the dexOptions.javaMaxHeapSize = %s)",
                        dexOptions.getJavaMaxHeapSize());
            }

            mLogger.warning("\nRunning dex as a separate process.\n\n"
                            + "To run dex in process, the Gradle daemon needs a larger heap.\n"
                            + "It currently has %1$d MB.\n"
                            + "For faster builds, increase the maximum heap size for the "
                            + "Gradle daemon to at least %2$s MB%3$s.\n"
                            + "To do this set org.gradle.jvmargs=-Xmx%2$sM in the "
                            + "project gradle.properties.\n"
                            + "For more information see "
                            + "https://docs.gradle.org/current/userguide/build_environment.html\n",
                    maxMemory / (1024 * 1024),
                    // Add -1 and + 1 to round up the division
                    ((requiredHeapSizeHeuristic - 1) / (1024 * 1024)) + 1,
                    dexOptionsComment);
            mIsDexInProcess = false;
            return false;
        }
        mIsDexInProcess = true;
        return true;

    }

    /**
     * Returns the heap size that was specified by the -Xmx value from the user, or an approximated
     * value if the -Xmx value was not set or was set improperly.
     */
    static long getUserDefinedHeapSize() {
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.startsWith("-Xmx")) {
                Optional<Long> heapSize = parseSizeToBytes(arg.substring("-Xmx".length()));
                if (heapSize.isPresent()) {
                    return heapSize.get();
                }
                break;
            }
        }

        // If the -Xmx value was not set or was set improperly, get an approximation of the
        // heap size
        long heapSize = 0;
        for (MemoryPoolMXBean mpBean : ManagementFactory.getMemoryPoolMXBeans()) {
            if (mpBean.getType() == MemoryType.HEAP) {
                heapSize += mpBean.getUsage().getMax();
            }
        }
        return heapSize;
    }

    /**
     * Returns an Optional<Long> that is present when the size can be parsed successfully, and
     * empty otherwise.
     */
    @VisibleForTesting
    @NonNull
    static Optional<Long> parseSizeToBytes(@NonNull String sizeParameter) {
        long multiplier = 1;
        if (SdkUtils.endsWithIgnoreCase(sizeParameter, "k")) {
            multiplier = 1024;
        } else if (SdkUtils.endsWithIgnoreCase(sizeParameter, "m")) {
            multiplier = 1024 * 1024;
        } else if (SdkUtils.endsWithIgnoreCase(sizeParameter, "g")) {
            multiplier = 1024 * 1024 * 1024;
        }

        if (multiplier != 1) {
            sizeParameter = sizeParameter.substring(0, sizeParameter.length() - 1);
        }

        try {
            return Optional.of(multiplier * Long.parseLong(sizeParameter));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Returns true if the library (jar or folder) contains class files, false otherwise.
     */
    private static boolean checkLibraryClassesJar(@NonNull File input) throws IOException {

        if (!input.exists()) {
            return false;
        }

        if (input.isDirectory()) {
            return checkFolder(input);
        }

        try (ZipFile zipFile = new ZipFile(input)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while(entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.endsWith(DOT_CLASS) || name.endsWith(DOT_DEX)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Returns true if this folder or one of its subfolder contains a class file, false otherwise.
     */
    private static boolean checkFolder(@NonNull File folder) {
        File[] subFolders = folder.listFiles();
        if (subFolders != null) {
            for (File childFolder : subFolders) {
                if (childFolder.isFile()) {
                    String name = childFolder.getName();
                    if (name.endsWith(DOT_CLASS) || name.endsWith(DOT_DEX)) {
                        return true;
                    }
                }
                if (childFolder.isDirectory()) {
                    // if childFolder returns false, continue search otherwise return success.
                    if (checkFolder(childFolder)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
