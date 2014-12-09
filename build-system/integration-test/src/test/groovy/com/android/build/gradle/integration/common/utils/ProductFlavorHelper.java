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

package com.android.build.gradle.integration.common.utils;

import static org.junit.Assert.assertEquals;

import com.android.annotations.NonNull;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.ProductFlavor;

public final class ProductFlavorHelper {
    @NonNull
    private final ProductFlavor productFlavor;
    @NonNull private final String name;

    private String applicationId = null;
    private Integer versionCode = null;
    private String versionName = null;
    private ApiVersion minSdkVersion = null;
    private ApiVersion targetSdkVersion = null;
    private Integer renderscriptTargetApi = null;
    private String testApplicationId = null;
    private String testInstrumentationRunner = null;
    private Boolean testHandleProfiling = null;
    private Boolean testFunctionalTest = null;

    public ProductFlavorHelper(@NonNull ProductFlavor productFlavor, @NonNull String name) {
        this.productFlavor = productFlavor;
        this.name = name;
    }

    @NonNull
    public ProductFlavorHelper setApplicationId(String applicationId) {
        this.applicationId = applicationId;
        return this;
    }

    @NonNull
    public ProductFlavorHelper setVersionCode(Integer versionCode) {
        this.versionCode = versionCode;
        return this;
    }

    @NonNull
    public ProductFlavorHelper setVersionName(String versionName) {
        this.versionName = versionName;
        return this;
    }

    @NonNull
    public ProductFlavorHelper setMinSdkVersion(int minSdkVersion) {
        this.minSdkVersion = new FakeApiVersion(minSdkVersion);
        return this;
    }

    @NonNull
    public ProductFlavorHelper setTargetSdkVersion(int targetSdkVersion) {
        this.targetSdkVersion = new FakeApiVersion(targetSdkVersion);
        return this;
    }

    @NonNull
    public ProductFlavorHelper setRenderscriptTargetApi(Integer renderscriptTargetApi) {
        this.renderscriptTargetApi = renderscriptTargetApi;
        return this;
    }

    @NonNull
    public ProductFlavorHelper setTestApplicationId(String testApplicationId) {
        this.testApplicationId = testApplicationId;
        return this;
    }

    @NonNull
    public ProductFlavorHelper setTestInstrumentationRunner(String testInstrumentationRunner) {
        this.testInstrumentationRunner = testInstrumentationRunner;
        return this;
    }

    @NonNull
    public ProductFlavorHelper setTestHandleProfiling(Boolean testHandleProfiling) {
        this.testHandleProfiling = testHandleProfiling;
        return this;
    }

    @NonNull
    public ProductFlavorHelper setTestFunctionalTest(Boolean testFunctionalTest) {
        this.testFunctionalTest = testFunctionalTest;
        return this;
    }

    public void test() {
        assertEquals(name + ":applicationId", applicationId, productFlavor.getApplicationId());
        assertEquals(name + ":VersionCode", versionCode, productFlavor.getVersionCode());
        assertEquals(name + ":VersionName", versionName, productFlavor.getVersionName());
        assertEquals(name + ":minSdkVersion", minSdkVersion, productFlavor.getMinSdkVersion());
        assertEquals(name + ":targetSdkVersion", targetSdkVersion, productFlavor.getTargetSdkVersion());
        assertEquals(name + ":renderscriptTargetApi",
                renderscriptTargetApi, productFlavor.getRenderscriptTargetApi());
        assertEquals(name + ":testApplicationId",
                testApplicationId, productFlavor.getTestApplicationId());
        assertEquals(name + ":testInstrumentationRunner",
                testInstrumentationRunner, productFlavor.getTestInstrumentationRunner());
        assertEquals(name + ":testHandleProfiling",
                testHandleProfiling, productFlavor.getTestHandleProfiling());
        assertEquals(name + ":testFunctionalTest",
                testFunctionalTest, productFlavor.getTestFunctionalTest());
    }
}