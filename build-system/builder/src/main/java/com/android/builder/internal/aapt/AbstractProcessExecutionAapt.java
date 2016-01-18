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

package com.android.builder.internal.aapt;

import com.android.annotations.NonNull;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.process.ProcessInfo;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.process.ProcessResult;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

/**
 * {@link Aapt} implementation that relies on external execution of an {@code aapt} command.
 */
public abstract class AbstractProcessExecutionAapt extends AbstractAapt {

    /**
     * Executor used to run external processes,
     */
    @NonNull
    private final ProcessExecutor mProcessExecutor;

    /**
     * Handler to process the output of the executed process.
     */
    @NonNull
    private final ProcessOutputHandler mProcessOutputHandler;

    /**
     * Creates a new entry point to the original {@code aapt}.
     *
     * @param processExecutor the executor for external processes
     * @param processOutputHandler handler to process the output of the executed process
     */
    public AbstractProcessExecutionAapt(@NonNull ProcessExecutor processExecutor,
            @NonNull ProcessOutputHandler processOutputHandler) {
        mProcessExecutor = processExecutor;
        mProcessOutputHandler = processOutputHandler;
    }

    @Override
    @NonNull
    protected ListenableFuture<Void> makeValidatedPackage(@NonNull AaptPackageConfig config)
            throws AaptException {
        ProcessInfoBuilder builder = makePackageProcessBuilder(config);

        final ProcessInfo processInfo = builder.createProcess();
        ListenableFuture<ProcessResult> execResult = mProcessExecutor.submit(processInfo,
                mProcessOutputHandler);

        final SettableFuture<Void> result = SettableFuture.create();
        Futures.addCallback(execResult, new FutureCallback<ProcessResult>() {
            @Override
            public void onSuccess(ProcessResult processResult) {
                try {
                    processResult.rethrowFailure().assertNormalExitValue();
                    result.set(null);
                } catch (Exception e) {
                    result.setException(e);
                }
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                result.setException(t);
            }
        });

        return result;
    }

    /**
     * Creates a process builder to invoke {@code aapt} to perform a package with the requested
     * configuration. The package configuration has already been validated.
     *
     * @param config the package configuration
     * @return a builder to invoke {@code aapt}
     * @throws AaptException failed to create the command, for example, package configuration is
     * invalid in spite of having already been validated
     */
    @NonNull
    protected abstract ProcessInfoBuilder makePackageProcessBuilder(
            @NonNull AaptPackageConfig config) throws AaptException;

}