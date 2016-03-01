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

package com.android.builder.internal.compiler;

import static com.android.SdkConstants.PLATFORM_DARWIN;
import static com.android.SdkConstants.PLATFORM_LINUX;
import static com.android.SdkConstants.PLATFORM_WINDOWS;
import static com.android.SdkConstants.currentPlatform;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.process.ProcessResult;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * A Source File processor for AIDL files. This compiles each aidl file found by the SourceSearcher.
 */
public class ShaderProcessor implements SourceSearcher.SourceFileProcessor {

    public static final String EXT_VERT = "vert";
    public static final String EXT_TESC = "tesc";
    public static final String EXT_TESE = "tese";
    public static final String EXT_GEOM = "geom";
    public static final String EXT_FRAG = "frag";
    public static final String EXT_COMP = "comp";

    @Nullable
    private File mNdkLocation;
    @NonNull
    private File mSourceFolder;
    @NonNull
    private final File mOutputDir;
    @NonNull
    private final List<String> mOptions;
    @NonNull
    private final ProcessExecutor mProcessExecutor;
    @NonNull
    private  final ProcessOutputHandler mProcessOutputHandler;

    private File mGlslcLocation;


    public ShaderProcessor(
            @Nullable File ndkLocation,
            @NonNull File sourceFolder,
            @NonNull File outputDir,
            @NonNull List<String> options,
            @NonNull ProcessExecutor processExecutor,
            @NonNull ProcessOutputHandler processOutputHandler) {
        mNdkLocation = ndkLocation;
        mSourceFolder = sourceFolder;
        mOutputDir = outputDir;
        mOptions = ImmutableList.copyOf(options);
        mProcessExecutor = processExecutor;
        mProcessOutputHandler = processOutputHandler;
    }

    @Override
    public void initOnFirstFile() {
        if (mNdkLocation == null) {
            throw new IllegalStateException("NDK location is missing. It is required to compile shaders.");
        }

        if (!mNdkLocation.isDirectory()) {
            throw new IllegalStateException("NDK location does not exist. It is required to compile shaders: " + mNdkLocation);
        }

        // find the location of the compiler.
        File glslcRootFolder = new File(mNdkLocation, SdkConstants.FD_GLSLC);

        switch (currentPlatform()) {
            case PLATFORM_DARWIN:
                glslcRootFolder = new File(glslcRootFolder, "darwin-x86_64");
                break;
            case PLATFORM_WINDOWS:
                // try 64 bit first
                glslcRootFolder = new File(glslcRootFolder, "windows-x86_64");
                if (!glslcRootFolder.isDirectory()) {
                    // try 32 bit next
                    glslcRootFolder = new File(glslcRootFolder, "windows");
                }
                break;
            case PLATFORM_LINUX:
                glslcRootFolder = new File(glslcRootFolder, "linux-x86_64");
                break;
        }

        if (!glslcRootFolder.isDirectory()) {
            throw new IllegalStateException("Missing NDK subfolder: " + glslcRootFolder);
        }

        mGlslcLocation = new File(glslcRootFolder, SdkConstants.FN_GLSLC);

        if (!mGlslcLocation.isFile()) {
            throw new IllegalStateException("glslc is missing: " + mGlslcLocation);
        }
    }

    @Override
    public void processFile(@NonNull File sourceFolder, @NonNull File sourceFile)
            throws ProcessException, IOException {
        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        builder.setExecutable(mGlslcLocation);

        // working dir for the includes
        builder.addArgs("-I", mSourceFolder.getPath());

        // add the options
        builder.addArgs(mOptions);

        // the source file
        builder.addArgs(sourceFile.getPath());

        // output file path
        File destFile = computeOutputFile(sourceFolder, sourceFile, mOutputDir);
        builder.addArgs("-o", destFile.getPath());

        // make sure the output file's parent folder is created.
        FileUtils.mkdirs(destFile.getParentFile());

        ProcessResult result = mProcessExecutor.execute(
                builder.createProcess(), mProcessOutputHandler);
        result.rethrowFailure().assertNormalExitValue();
    }

    public static File computeOutputFile(
            @NonNull File sourceFolder,
            @NonNull File sourceFile,
            @NonNull File outputDir) {
        String relativePath = FileUtils.relativePath(sourceFile, sourceFolder);
        return new File(outputDir, relativePath + ".spv");
    }
}
