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
}
