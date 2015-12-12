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

package com.android.build.gradle.model.internal;

import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.managed.BuildType;
import com.android.build.gradle.managed.NdkConfig;
import com.android.build.gradle.managed.ProductFlavor;
import com.android.build.gradle.model.AndroidBinary;

import org.gradle.nativeplatform.NativeLibraryBinarySpec;

import java.util.List;

/**
 * Internal interface for {@link AndroidBinary}
 */
public interface AndroidBinaryInternal extends AndroidBinary {

    void setBuildType(BuildType buildType);

    void setProductFlavors(List<ProductFlavor> productFlavors);

    NdkConfig getMergedNdkConfig();

    BaseVariantData getVariantData();

    void setVariantData(BaseVariantData variantData);

    List<NativeLibraryBinarySpec> getNativeBinaries();

    List<String> getTargetAbi();

    void computeMergedNdk(
            NdkConfig ndkConfig,
            List<com.android.build.gradle.managed.ProductFlavor> flavors,
            com.android.build.gradle.managed.BuildType buildType);

}
