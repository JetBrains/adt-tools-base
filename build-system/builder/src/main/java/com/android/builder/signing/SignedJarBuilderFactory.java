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

package com.android.builder.signing;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.packaging.ApkCreator;
import com.android.builder.packaging.ApkBuilderFactory;
import com.android.builder.packaging.PackagerException;

import java.io.File;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * APK builder factory that creates {@link SignedJarBuilder}s.
 */
public class SignedJarBuilderFactory implements ApkBuilderFactory {

    @Override
    public ApkCreator make(@NonNull File out, @Nullable PrivateKey key,
            @Nullable X509Certificate certificate, @Nullable String builtBy,
            @Nullable String createdBy, int minSdkVersion) throws PackagerException {
        try {
            return new SignedJarBuilder(out, key, certificate, builtBy, createdBy, minSdkVersion);
        } catch (Exception e) {
            throw new PackagerException(e);
        }
    }
}
