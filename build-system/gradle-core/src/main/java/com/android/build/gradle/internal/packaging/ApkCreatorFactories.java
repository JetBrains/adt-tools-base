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

package com.android.build.gradle.internal.packaging;

import com.android.annotations.NonNull;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.internal.packaging.zfile.ApkZFileCreatorFactory;
import com.android.builder.internal.packaging.zip.ZFileOptions;
import com.android.builder.packaging.ApkCreatorFactory;
import com.android.builder.signing.SignedJarApkCreatorFactory;

import org.gradle.api.Project;

/**
 * Constructs a {@link ApkCreatorFactory} based on gradle options.
 */
public final class ApkCreatorFactories {

    /**
     * Utility class: no constructor.
     */
    private ApkCreatorFactories() {
        /*
         * Nothing to do.
         */
    }

    /**
     * Creates an {@link ApkCreatorFactory} based on the definitions in the project.
     *
     * @param variantScope the variant scope used to obtain the project properties
     * @return the factory
     */
    @NonNull
    public static ApkCreatorFactory fromProjectProperties(@NonNull VariantScope variantScope) {
        Project project = variantScope.getGlobalScope().getProject();
        boolean useOldPackaging = AndroidGradleOptions.useOldPackaging(project);
        if (useOldPackaging) {
            return new SignedJarApkCreatorFactory();
        } else {
            boolean keepTimestamps = AndroidGradleOptions.keepTimestampsInApk(
                    variantScope.getGlobalScope().getProject());

            ZFileOptions options = new ZFileOptions();
            options.setNoTimestamps(!keepTimestamps);
            options.setUseExtraFieldForAlignment(true);
            return new ApkZFileCreatorFactory(options);
        }
    }
}
