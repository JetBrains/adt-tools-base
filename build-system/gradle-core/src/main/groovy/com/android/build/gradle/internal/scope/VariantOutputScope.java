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

package com.android.build.gradle.internal.scope;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.TaskFactory;
import com.android.build.gradle.internal.variant.ApkVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.tasks.PackageApplication;
import com.android.build.gradle.tasks.ZipAlign;
import com.android.utils.StringHelper;
import com.google.common.collect.ImmutableMap;

import java.io.File;
import java.util.Map;

/**
 * A scope containing data for a specific variant.
 */
public class VariantOutputScope {

    @NonNull
    private VariantScope variantScope;
    @NonNull
    private BaseVariantOutputData variantOutputData;

    public VariantOutputScope(
            @NonNull VariantScope variantScope,
            @NonNull BaseVariantOutputData variantOutputData) {
        this.variantScope = variantScope;
        this.variantOutputData = variantOutputData;
    }

    @NonNull
    public GlobalScope getGlobalScope() {
        return variantScope.getGlobalScope();
    }

    @NonNull
    public VariantScope getVariantScope() {
        return variantScope;
    }

    @NonNull
    public BaseVariantOutputData getVariantOutputData() {
        return variantOutputData;
    }

    @NonNull
    public File getPackageApk() {
        ApkVariantData apkVariantData = (ApkVariantData) variantScope.getVariantData();

        boolean signedApk = apkVariantData.isSigned();
        String apkName = signedApk ?
                getGlobalScope().getProjectBaseName() + "-" + variantOutputData.getBaseName() + "-unaligned.apk" :
                getGlobalScope().getProjectBaseName() + "-" + variantOutputData.getBaseName() + "-unsigned.apk";

        // if this is the final task then the location is
        // the potentially overridden one.
        if (!signedApk || !apkVariantData.getZipAlignEnabled()) {
            return getGlobalScope().getProject().file(
                    getGlobalScope().getApkLocation() + "/" + apkName);
        } else {
            // otherwise default one.
            return getGlobalScope().getProject().file(getGlobalScope().getDefaultApkLocation() + "/" + apkName);
        }
    }
}
