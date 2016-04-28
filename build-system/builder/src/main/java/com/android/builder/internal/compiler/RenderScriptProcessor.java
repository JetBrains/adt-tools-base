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


package com.android.builder.internal.compiler;

import static com.android.SdkConstants.EXT_BC;
import static com.android.SdkConstants.FN_RENDERSCRIPT_V8_JAR;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.internal.LoggedErrorException;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.process.ProcessResult;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.ILogger;
import com.google.common.collect.Maps;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Compiles Renderscript files.
 */
public class RenderScriptProcessor {

    private static final String LIBCLCORE_BC = "libclcore.bc";

    // ABI list, as pairs of (android-ABI, toolchain-ABI)
    private static final class Abi {

        @NonNull
        private final String mDevice;
        @NonNull
        private final String mToolchain;
        @NonNull
        private final BuildToolInfo.PathId mLinker;
        @NonNull
        private final String[] mLinkerArgs;

        Abi(@NonNull String device,
            @NonNull String toolchain,
            @NonNull BuildToolInfo.PathId linker,
            @NonNull String... linkerArgs) {

            mDevice = device;
            mToolchain = toolchain;
            mLinker = linker;
            mLinkerArgs = linkerArgs;
        }
    }

    private static final Abi[] ABIS_32 = {
            new Abi("armeabi-v7a", "armv7-none-linux-gnueabi", BuildToolInfo.PathId.LD_ARM,
                    "-dynamic-linker", "/system/bin/linker", "-X", "-m", "armelf_linux_eabi"),
            new Abi("mips", "mipsel-unknown-linux", BuildToolInfo.PathId.LD_MIPS, "-EL"),
            new Abi("x86", "i686-unknown-linux", BuildToolInfo.PathId.LD_X86, "-m", "elf_i386") };
    private static final Abi[] ABIS_64 = {
            new Abi("arm64-v8a", "aarch64-linux-android", BuildToolInfo.PathId.LD_ARM64, "-X"),
            new Abi("x86_64", "x86_64-unknown-linux", BuildToolInfo.PathId.LD_X86_64, "-m", "elf_x86_64") };


    public static final String RS_DEPS = "rsDeps";

    @NonNull
    private final List<File> mSourceFolders;

    @NonNull
    private final List<File> mImportFolders;

    @NonNull
    private final File mSourceOutputDir;

    @NonNull
    private final File mResOutputDir;

    @NonNull
    private final File mObjOutputDir;

    @NonNull
    private final File mLibOutputDir;

    @NonNull
    private final BuildToolInfo mBuildToolInfo;

    @NonNull
    private final ILogger mLogger;

    private final int mTargetApi;

    private final int mOptimizationLevel;

    private final boolean mNdkMode;

    private final boolean mSupportMode;

    private final Set<String> mAbiFilters;


    private final File mRsLib;
    private final Map<String, File> mLibClCore = Maps.newHashMap();

    public RenderScriptProcessor(
            @NonNull List<File> sourceFolders,
            @NonNull List<File> importFolders,
            @NonNull File sourceOutputDir,
            @NonNull File resOutputDir,
            @NonNull File objOutputDir,
            @NonNull File libOutputDir,
            @NonNull BuildToolInfo buildToolInfo,
            int targetApi,
            boolean debugBuild,
            int optimizationLevel,
            boolean ndkMode,
            boolean supportMode,
            @Nullable Set<String> abiFilters,
            @NonNull ILogger logger) {
        mSourceFolders = sourceFolders;
        mImportFolders = importFolders;
        mSourceOutputDir = sourceOutputDir;
        mResOutputDir = resOutputDir;
        mObjOutputDir = objOutputDir;
        mLibOutputDir = libOutputDir;
        mBuildToolInfo = buildToolInfo;
        mTargetApi = targetApi;
        mOptimizationLevel = optimizationLevel;
        mNdkMode = ndkMode;
        mSupportMode = supportMode;
        mAbiFilters = abiFilters;
        mLogger = logger;

        if (supportMode) {
            File rs = new File(mBuildToolInfo.getLocation(), "renderscript");
            mRsLib = new File(rs, "lib");
            File bcFolder = new File(mRsLib, "bc");
            for (Abi abi : ABIS_32) {
                File rsClCoreFile = new File(bcFolder, abi.mDevice + File.separatorChar + LIBCLCORE_BC);
                if (rsClCoreFile.exists()) {
                    mLibClCore.put(abi.mDevice, rsClCoreFile);
                }
            }
            for (Abi abi : ABIS_64) {
                File rsClCoreFile = new File(bcFolder, abi.mDevice + File.separatorChar + LIBCLCORE_BC);
                if (rsClCoreFile.exists()) {
                    mLibClCore.put(abi.mDevice, rsClCoreFile);
                }
            }
        } else {
            mRsLib = null;
        }
    }

    public static File getSupportJar(String buildToolsFolder) {
        return new File(buildToolsFolder, "renderscript/lib/" + FN_RENDERSCRIPT_V8_JAR);
    }

    public static File getSupportNativeLibFolder(String buildToolsFolder) {
        File rs = new File(buildToolsFolder, "renderscript");
        File lib = new File(rs, "lib");
        return new File(lib, "packaged");
    }

    public void build(
            @NonNull ProcessExecutor processExecutor,
            @NonNull ProcessOutputHandler processOutputHandler)
            throws InterruptedException, ProcessException, LoggedErrorException, IOException {

        // gather the files to compile
        FileGatherer fileGatherer = new FileGatherer();
        SourceSearcher searcher = new SourceSearcher(mSourceFolders, "rs", "fs");
        searcher.setUseExecutor(false);
        searcher.search(fileGatherer);

        List<File> renderscriptFiles = fileGatherer.getFiles();

        if (renderscriptFiles.isEmpty()) {
            return;
        }

        // get the env var
        Map<String, String> env = Maps.newHashMap();
        if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_DARWIN) {
            env.put("DYLD_LIBRARY_PATH", mBuildToolInfo.getLocation().getAbsolutePath());
        } else if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_LINUX) {
            env.put("LD_LIBRARY_PATH", mBuildToolInfo.getLocation().getAbsolutePath());
        }

        doMainCompilation(renderscriptFiles, processExecutor, processOutputHandler, env);

        if (mSupportMode) {
            createSupportFiles(processExecutor, processOutputHandler, env);
        }
    }

    private void doMainCompilation(
            @NonNull List<File> inputFiles,
            @NonNull ProcessExecutor processExecutor,
            @NonNull ProcessOutputHandler processOutputHandler,
            @NonNull Map<String, String> env)
            throws ProcessException {
        ProcessInfoBuilder builder = new ProcessInfoBuilder();

        String renderscript = mBuildToolInfo.getPath(BuildToolInfo.PathId.LLVM_RS_CC);
        if (renderscript == null || !new File(renderscript).isFile()) {
            throw new IllegalStateException(BuildToolInfo.PathId.LLVM_RS_CC + " is missing");
        }

        String rsPath = mBuildToolInfo.getPath(BuildToolInfo.PathId.ANDROID_RS);
        String rsClangPath = mBuildToolInfo.getPath(BuildToolInfo.PathId.ANDROID_RS_CLANG);

        // the renderscript compiler doesn't expect the top res folder,
        // but the raw folder directly.
        File rawFolder = new File(mResOutputDir, SdkConstants.FD_RES_RAW);

        // compile all the files in a single pass
        builder.setExecutable(renderscript);
        builder.addEnvironments(env);

        // Due to a device side bug, let's not enable this at this time.
//        if (mDebugBuild) {
//            command.add("-g");
//        }

        builder.addArgs("-O");
        builder.addArgs(Integer.toString(mOptimizationLevel));

        // add all import paths
        builder.addArgs("-I");
        builder.addArgs(rsPath);
        builder.addArgs("-I");
        builder.addArgs(rsClangPath);

        for (File importPath : mImportFolders) {
            if (importPath.isDirectory()) {
                builder.addArgs("-I");
                builder.addArgs(importPath.getAbsolutePath());
            }
        }

        if (mSupportMode) {
            builder.addArgs("-rs-package-name=android.support.v8.renderscript");
        }

        // source output
        builder.addArgs("-p");
        builder.addArgs(mSourceOutputDir.getAbsolutePath());

        if (mNdkMode) {
            builder.addArgs("-reflect-c++");
        }

        // res output
        builder.addArgs("-o");
        builder.addArgs(rawFolder.getAbsolutePath());

        builder.addArgs("-target-api");
        int targetApi = mTargetApi < 11 ? 11 : mTargetApi;
        targetApi = (mSupportMode && targetApi < 18) ? 18 : targetApi;
        builder.addArgs(Integer.toString(targetApi));

        // input files
        for (File sourceFile : inputFiles) {
            builder.addArgs(sourceFile.getAbsolutePath());
        }

        ProcessResult result = processExecutor.execute(
                builder.createProcess(), processOutputHandler);
        result.rethrowFailure().assertNormalExitValue();
    }

    private void createSupportFiles(
            @NonNull final ProcessExecutor processExecutor,
            @NonNull final ProcessOutputHandler processOutputHandler,
            @NonNull final Map<String, String> env)
            throws IOException, InterruptedException, LoggedErrorException, ProcessException {
        // get the generated BC files.
        int targetApi = mTargetApi < 11 ? 11 : mTargetApi;
        targetApi = (mSupportMode && targetApi < 18) ? 18 : targetApi;
        if (targetApi < 21) {
            File rawFolder = new File(mResOutputDir, SdkConstants.FD_RES_RAW);
            createSupportFilesHelper(rawFolder, ABIS_32, processExecutor, processOutputHandler, env);
        } else {
            File rawFolder32 = new File(mResOutputDir, SdkConstants.FD_RES_RAW + "/bc32");
            createSupportFilesHelper(rawFolder32, ABIS_32, processExecutor,
                    processOutputHandler, env);
            File rawFolder64 = new File(mResOutputDir, SdkConstants.FD_RES_RAW + "/bc64");
            createSupportFilesHelper(rawFolder64, ABIS_64, processExecutor,
                    processOutputHandler, env);

        }
    }

    private void createSupportFilesHelper(
            @NonNull final File rawFolder,
            @NonNull final Abi[] abis,
            @NonNull final ProcessExecutor processExecutor,
            @NonNull final ProcessOutputHandler processOutputHandler,
            @NonNull final Map<String, String> env)
            throws IOException, InterruptedException, LoggedErrorException, ProcessException {
        SourceSearcher searcher = new SourceSearcher(
                Collections.singletonList(rawFolder), EXT_BC);
        FileGatherer fileGatherer = new FileGatherer();
        searcher.search(fileGatherer);

        WaitableExecutor<Void> mExecutor  = WaitableExecutor.useGlobalSharedThreadPool();

        for (final File bcFile : fileGatherer.getFiles()) {
            String name = bcFile.getName();
            final String objName = name.replaceAll("\\.bc", ".o");
            final String soName = "librs." + name.replaceAll("\\.bc", ".so");

            for (final Abi abi : abis) {
                if (mAbiFilters != null && !mAbiFilters.contains(abi.mDevice)) {
                    continue;
                }
                // only build for the ABIs bundled in Build-Tools.
                if (mLibClCore.get(abi.mDevice) == null) {
                    // warn the user to update Build-Tools if the desired ABI is not found.
                    mLogger.warning("Skipped RenderScript support mode compilation for "
                                    + abi.mDevice
                                    + " : required components not found in Build-Tools "
                                    + mBuildToolInfo.getRevision().toString()
                                    + '\n'
                                    + "Please check and update your BuildTools.");
                    continue;
                }

                // make sure the dest folders exist
                final File objAbiFolder = new File(mObjOutputDir, abi.mDevice);
                if (!objAbiFolder.isDirectory() && !objAbiFolder.mkdirs()) {
                    throw new IOException("Unable to create dir " + objAbiFolder.getAbsolutePath());
                }

                final File libAbiFolder = new File(mLibOutputDir, abi.mDevice);
                if (!libAbiFolder.isDirectory() && !libAbiFolder.mkdirs()) {
                    throw new IOException("Unable to create dir " + libAbiFolder.getAbsolutePath());
                }

                mExecutor.execute(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        File objFile = createSupportObjFile(
                                bcFile,
                                abi,
                                objName,
                                objAbiFolder,
                                processExecutor,
                                processOutputHandler,
                                env);
                        createSupportLibFile(
                                objFile,
                                abi,
                                soName,
                                libAbiFolder,
                                processExecutor,
                                processOutputHandler,
                                env);
                        return null;
                    }
                });
            }
        }

        mExecutor.waitForTasksWithQuickFail(true /*cancelRemaining*/);
    }

    private File createSupportObjFile(
            @NonNull File bcFile,
            @NonNull Abi abi,
            @NonNull String objName,
            @NonNull File objAbiFolder,
            @NonNull ProcessExecutor processExecutor,
            @NonNull ProcessOutputHandler processOutputHandler,
            @NonNull Map<String, String> env) throws ProcessException {

        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        builder.setExecutable(mBuildToolInfo.getPath(BuildToolInfo.PathId.BCC_COMPAT));
        builder.addEnvironments(env);

        builder.addArgs("-O" + Integer.toString(mOptimizationLevel));

        File outFile = new File(objAbiFolder, objName);
        builder.addArgs("-o", outFile.getAbsolutePath());

        builder.addArgs("-fPIC");
        builder.addArgs("-shared");

        builder.addArgs("-rt-path", mLibClCore.get(abi.mDevice).getAbsolutePath());

        builder.addArgs("-mtriple", abi.mToolchain);

        builder.addArgs(bcFile.getAbsolutePath());

        processExecutor.execute(
                builder.createProcess(), processOutputHandler)
                .rethrowFailure().assertNormalExitValue();

        return outFile;
    }

    private void createSupportLibFile(
            @NonNull File objFile,
            @NonNull Abi abi,
            @NonNull String soName,
            @NonNull File libAbiFolder,
            @NonNull ProcessExecutor processExecutor,
            @NonNull ProcessOutputHandler processOutputHandler,
            @NonNull Map<String, String> env) throws ProcessException {

        File intermediatesFolder = new File(mRsLib, "intermediates");
        File intermediatesAbiFolder = new File(intermediatesFolder, abi.mDevice);
        File packagedFolder = new File(mRsLib, "packaged");
        File packagedAbiFolder = new File(packagedFolder, abi.mDevice);

        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        builder.setExecutable(mBuildToolInfo.getPath(abi.mLinker));
        builder.addEnvironments(env);

        builder.addArgs("--eh-frame-hdr")
                .addArgs(abi.mLinkerArgs)
                .addArgs("-shared", "-Bsymbolic", "-z", "noexecstack", "-z", "relro", "-z", "now");

        File outFile = new File(libAbiFolder, soName);
        builder.addArgs("-o", outFile.getAbsolutePath());

        builder.addArgs(
                "-L" + intermediatesAbiFolder.getAbsolutePath(),
                "-L" + packagedAbiFolder.getAbsolutePath(),
                "-soname",
                soName,
                objFile.getAbsolutePath(),
                new File(intermediatesAbiFolder, "libcompiler_rt.a").getAbsolutePath(),
                "-lRSSupport",
                "-lm",
                "-lc");

        processExecutor.execute(
                builder.createProcess(), processOutputHandler)
                .rethrowFailure().assertNormalExitValue();
    }
}
