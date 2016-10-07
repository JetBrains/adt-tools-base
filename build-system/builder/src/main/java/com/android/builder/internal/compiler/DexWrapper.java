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

package com.android.builder.internal.compiler;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.builder.core.DexOptions;
import com.android.builder.core.DexProcessBuilder;
import com.android.dx.command.dexer.DxContext;
import com.android.dx.command.dexer.Main;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.process.ProcessResult;
import com.google.common.base.Objects;
import com.google.common.collect.Iterables;

import java.io.File;
import java.io.IOException;

/**
 * Wrapper around the real dx classes.
 */
public class DexWrapper {

    /**
     * Runs the dex command.
     *
     * @return the integer return code of com.android.dx.command.dexer.Main.run()
     */
    public static ProcessResult run(
            @NonNull DexProcessBuilder processBuilder,
            @NonNull DexOptions dexOptions,
            @NonNull ProcessOutputHandler outputHandler) throws IOException, ProcessException {
        ProcessOutput output = outputHandler.createOutput();
        int res;
        try {
            DxContext dxContext = new DxContext(output.getStandardOutput(), output.getErrorOutput());
            Main.Arguments args = buildArguments(processBuilder, dexOptions, dxContext);
            res = new Main(dxContext).run(args);
        } finally {
            output.close();
        }

        outputHandler.handleOutput(output);
        return new DexProcessResult(res);
    }

    @NonNull
    private static Main.Arguments buildArguments(
            @NonNull DexProcessBuilder processBuilder,
            @NonNull DexOptions dexOptions,
            @NonNull DxContext dxContext)
            throws ProcessException {
        Main.Arguments args = new Main.Arguments();

        // Inputs:
        args.fileNames = Iterables.toArray(processBuilder.getFilesToAdd(null), String.class);

        // Outputs:
        if (processBuilder.getOutputFile().isDirectory() && !processBuilder.isMultiDex()) {
            args.outName = new File(processBuilder.getOutputFile(), "classes.dex").getPath();
            args.jarOutput = false;
        } else {
            String outputFileAbsolutePath = processBuilder.getOutputFile().getAbsolutePath();
            args.outName = outputFileAbsolutePath;
            args.jarOutput = outputFileAbsolutePath.endsWith(SdkConstants.DOT_JAR);
        }

        // Multi-dex:
        args.multiDex = processBuilder.isMultiDex();
        if (processBuilder.getMainDexList() != null) {
            args.mainDexListFile = processBuilder.getMainDexList().getPath();
        }

        // Other:
        args.verbose = processBuilder.isVerbose();
        args.optimize = !processBuilder.isNoOptimize();
        args.numThreads = Objects.firstNonNull(dexOptions.getThreadCount(), 4);
        args.forceJumbo = dexOptions.getJumboMode();

        args.parseFlags(Iterables.toArray(dexOptions.getAdditionalParameters(), String.class));
        args.makeOptionsObjects(dxContext);

        return args;
    }

    private static class DexProcessResult implements ProcessResult {

        private int mExitValue;

        DexProcessResult(int exitValue) {
            mExitValue = exitValue;
        }

        @NonNull
        @Override
        public ProcessResult assertNormalExitValue()
                throws ProcessException {
            if (mExitValue != 0) {
                throw new ProcessException(
                        String.format("Return code %d for dex process", mExitValue));
            }

            return this;
        }

        @Override
        public int getExitValue() {
            return mExitValue;
        }

        @NonNull
        @Override
        public ProcessResult rethrowFailure()
                throws ProcessException {
            return assertNormalExitValue();
        }
    }

}
