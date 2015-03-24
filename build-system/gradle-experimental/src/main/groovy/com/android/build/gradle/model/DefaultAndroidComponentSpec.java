/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.model;

import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.VariantManager;
import com.android.builder.model.SigningConfig;

import org.gradle.nativeplatform.NativeLibrary;
import org.gradle.nativeplatform.NativeLibrarySpec;
import org.gradle.platform.base.component.BaseComponentSpec;

/**
 * Implementation for Android component spec.
 */
public class DefaultAndroidComponentSpec extends BaseComponentSpec implements AndroidComponentSpec{
    BaseExtension extension;

    VariantManager variantManager;

    SigningConfig signingOverride;

    NativeLibrarySpec nativeLibrary;

    @Override
    public BaseExtension getExtension() {
        return extension;
    }

    public void setExtension(BaseExtension extension) {
        this.extension = extension;
    }

    public VariantManager getVariantManager() {

        return variantManager;
    }

    public void setVariantManager(VariantManager variantManager) {
        this.variantManager = variantManager;
    }

    public SigningConfig getSigningOverride() {
        return signingOverride;
    }

    public void setSigningOverride(SigningConfig signingOverride) {
        this.signingOverride = signingOverride;
    }

    public NativeLibrarySpec getNativeLibrary() {
        return nativeLibrary;
    }

    public void setNativeLibrary(NativeLibrarySpec nativeLibrary) {
        this.nativeLibrary = nativeLibrary;
    }
}
