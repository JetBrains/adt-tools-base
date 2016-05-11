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

package com.android.builder.internal.packaging.zfile;

import com.android.annotations.NonNull;
import com.android.builder.internal.packaging.zip.ZFileOptions;
import com.android.builder.packaging.ApkCreator;
import com.android.builder.packaging.ApkCreatorFactory;
import com.android.builder.packaging.PackagerException;

/**
 * Creates instances of {@link ApkZFileCreator}.
 */
public class ApkZFileCreatorFactory implements ApkCreatorFactory {

    /**
     * Options for the {@link ZFileOptions} to use in all APKs.
     */
    @NonNull
    private final ZFileOptions mOptions;

    /**
     * Creates a new factory.
     *
     * @param options the options to use for all instances created
     */
    public ApkZFileCreatorFactory(@NonNull ZFileOptions options) {
        mOptions = options;
    }


    @Override
    @NonNull
    public ApkCreator make(@NonNull CreationData creationData) throws PackagerException {
        try {
            return new ApkZFileCreator(creationData, mOptions);
        } catch (Exception e) {
            throw new PackagerException(e);
        }
    }
}
