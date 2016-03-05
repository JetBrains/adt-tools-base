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

package com.android.builder.packaging;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import java.io.File;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * Factory that creates {@link ApkCreator}s.
 */
public interface ApkBuilderFactory {
    /**
     * Creates an {@link ApkCreator} with a given output location, and signing information.
     * <p/>If either {@code key} or {@code certificate} is {@code null} then
     * the archive will not be signed.
     * @param out the location where to write the jar archive.
     * @param key the {@link PrivateKey} used to sign the archive, or {@code null}.
     * @param certificate the {@link X509Certificate} used to sign the archive, or
     * {@code null}.
     * @param minSdkVersion minSdkVersion of the package contained in this JAR.
     * @throws PackagerException failed to create the builder
     */
    ApkCreator make(@NonNull File out, @Nullable PrivateKey key,
            @Nullable X509Certificate certificate, @Nullable String builtBy,
            @Nullable String createdBy, int minSdkVersion) throws PackagerException;
}
