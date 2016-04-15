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
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;

/**
 * Interface to the {@code aapt} tool. To obtain an instance, a concrete class, tied to a specific
 * {@code aapt} implementation, should be used. For example,
 * {@link com.android.builder.internal.aapt.v1.AaptV1} can be used to create implementations that
 * use version 1 of the {@code aapt} tool.
 */
public interface Aapt {

    /**
     * Invokes {@code aapt} to package resources.
     *
     * @param config a package configuration
     * @return a {@code Future} to monitor execution; this {@code Future} always returns
     * {@code null}
     * @throws AaptException if there is a configuration problem, or if {@code aapt} invocation
     * fails; note that if execution of the {@code aapt} fails, but the command starts successfully,
     * the error will be reported in the returned {@code Future}, not as an exception here
     */
    @NonNull
    ListenableFuture<Void> makePackage(@NonNull AaptPackageConfig config) throws AaptException;

    /**
     * Invokes {@code aapt} to compile a file.
     *
     * <p>The compile method has the following contract:
     * <ul>
     *  <li>Compiling is requested on a file-by-file basis, {@code file} is the file to compile.
     *  <li>Each compilation request has a specific output directory, {@code output}, that is
     *  guaranteed to exist.
     *  <li>Invoking compile is issuing a request for compilation: the file provided may not be
     *  compilable by {@code aapt}.
     *  <li>The returned promise -- {@code Future} -- will be set to the file with the result of
     *  compilation or {@code null} if the file is not compilable.
     *  <li>If a file is returned, it is located inside the output directory.
     *  <li>Compilation is time-insensitive and isolated with respect to the inputs. This means
     *  that a file is always compiled to the same output (path and content) regardless of when it
     *  is compiled and regardless of which files were compiled before or after.
     *  <li>No two compilable input files generate the same output file.
     *  <li>No other files are written to the output directory other than the returned file.
     *  Subdirectories to contain the returned file may be created by the compile method.
     *  <li>If the input file is not compilable and, therefore, {@code null} is returned, then no
     *  files are changed in the filesystem.
     *  <li>The return promise is only fulfilled when the file is fully written and closed.
     *  <li>The compile method will fulfill the promise as fast as possible.
     *  <li>There is no logical limit on the number of parallel invocations of the compilation
     *  method.
     *  <li>The compile method makes no assumptions on the contents of the output directory.
     *  <li>The compile method can overwrite an existing file in output when compiling.
     * </ul>
     *
     * <p>The method receives the file to compile and returns a promise to return a {@code File}.
     * The compile method issues a "request for compilation": the file passed may not be compilable
     * in which case the future response is it set to {@code null}.
     *
     * @param file the file to compile, must exist and be a file
     * @param output the output directory, must exist and be a directory
     * @return the promise of the compilation result; the future will be set to {@code null} if
     * the file is not compilable
     * @throws AaptException failed to process the compilation request; actual compilation errors,
     * if any, are reported in the returned {@code Future} as they are asynchronous
     */
    @NonNull
    ListenableFuture<File> compile(@NonNull File file, @NonNull File output)
            throws AaptException;
}
