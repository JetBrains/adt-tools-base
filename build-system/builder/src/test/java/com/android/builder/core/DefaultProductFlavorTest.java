/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.builder.core;

import com.android.builder.internal.ClassFieldImpl;
import com.android.builder.model.ClassField;
import com.android.builder.model.ProductFlavor;
import com.google.common.collect.ImmutableMap;

import junit.framework.TestCase;

import java.util.Collection;
import java.util.Map;

public class DefaultProductFlavorTest extends TestCase {

    private DefaultProductFlavor mDefault;
    private DefaultProductFlavor mDefault2;
    private DefaultProductFlavor mCustom;
    private DefaultProductFlavor mCustom2;

    @Override
    protected void setUp() throws Exception {
        mDefault = new DefaultProductFlavor("default");
        mDefault2 = new DefaultProductFlavor("default2");

        mCustom = new DefaultProductFlavor("custom");
        mCustom.setMinSdkVersion(new DefaultApiVersion(42));
        mCustom.setTargetSdkVersion(new DefaultApiVersion(43));
        mCustom.setRenderscriptTargetApi(17);
        mCustom.setVersionCode(44);
        mCustom.setVersionName("42.0");
        mCustom.setApplicationId("com.forty.two");
        mCustom.setTestApplicationId("com.forty.two.test");
        mCustom.setTestInstrumentationRunner("com.forty.two.test.Runner");
        mCustom.setTestHandleProfiling(true);
        mCustom.setTestFunctionalTest(true);
        mCustom.addResourceConfiguration("hdpi");
        mCustom.addManifestPlaceholders(
                ImmutableMap.<String, Object>of("one", "oneValue", "two", "twoValue"));

        mCustom.addResValue(new ClassFieldImpl("foo", "one", "oneValue"));
        mCustom.addResValue(new ClassFieldImpl("foo", "two", "twoValue"));
        mCustom.addBuildConfigField(new ClassFieldImpl("foo", "one", "oneValue"));
        mCustom.addBuildConfigField(new ClassFieldImpl("foo", "two", "twoValue"));


        mCustom2 = new DefaultProductFlavor("custom2");
        mCustom2.addResourceConfigurations("ldpi", "hdpi");
        mCustom2.addManifestPlaceholders(
                ImmutableMap.<String, Object>of("two", "twoValueBis", "three", "threeValue"));
        mCustom2.addResValue(new ClassFieldImpl("foo", "two", "twoValueBis"));
        mCustom2.addResValue(new ClassFieldImpl("foo", "three", "threeValue"));
        mCustom2.addBuildConfigField(new ClassFieldImpl("foo", "two", "twoValueBis"));
        mCustom2.addBuildConfigField(new ClassFieldImpl("foo", "three", "threeValue"));
    }

    public void testClone() {
        ProductFlavor flavor = DefaultProductFlavor.clone(mCustom);
        assertEquals(mCustom, flavor);
    }

    public void testMergeOnDefault() {
        ProductFlavor flavor = DefaultProductFlavor.mergeFlavors(mDefault, mCustom);

        assertNotNull(flavor.getMinSdkVersion());
        assertEquals(42, flavor.getMinSdkVersion().getApiLevel());
        assertNotNull(flavor.getTargetSdkVersion());
        assertEquals(43, flavor.getTargetSdkVersion().getApiLevel());
        assertNotNull(flavor.getRenderscriptTargetApi());
        assertEquals(17, flavor.getRenderscriptTargetApi().intValue());
        assertNotNull(flavor.getVersionCode());
        assertEquals(44, flavor.getVersionCode().intValue());
        assertEquals("42.0", flavor.getVersionName());
        assertEquals("com.forty.two", flavor.getApplicationId());
        assertEquals("com.forty.two.test", flavor.getTestApplicationId());
        assertEquals("com.forty.two.test.Runner", flavor.getTestInstrumentationRunner());
        assertEquals(Boolean.TRUE, flavor.getTestHandleProfiling());
        assertEquals(Boolean.TRUE, flavor.getTestFunctionalTest());
    }

    public void testMergeOnCustom() {
        ProductFlavor flavor = DefaultProductFlavor.mergeFlavors(mDefault, mCustom);

        assertNotNull(flavor.getMinSdkVersion());
        assertEquals(42, flavor.getMinSdkVersion().getApiLevel());
        assertNotNull(flavor.getTargetSdkVersion());
        assertEquals(43, flavor.getTargetSdkVersion().getApiLevel());
        assertNotNull(flavor.getRenderscriptTargetApi());
        assertEquals(17, flavor.getRenderscriptTargetApi().intValue());
        assertNotNull(flavor.getVersionCode());
        assertEquals(44, flavor.getVersionCode().intValue());
        assertEquals("42.0", flavor.getVersionName());
        assertEquals("com.forty.two", flavor.getApplicationId());
        assertEquals("com.forty.two.test", flavor.getTestApplicationId());
        assertEquals("com.forty.two.test.Runner", flavor.getTestInstrumentationRunner());
        assertEquals(Boolean.TRUE, flavor.getTestHandleProfiling());
        assertEquals(Boolean.TRUE, flavor.getTestFunctionalTest());
    }

    public void testMergeDefaultOnDefault() {
        ProductFlavor flavor = DefaultProductFlavor.mergeFlavors(mDefault2, mDefault);

        assertNull(flavor.getMinSdkVersion());
        assertNull(flavor.getTargetSdkVersion());
        assertNull(flavor.getRenderscriptTargetApi());
        assertNull(flavor.getVersionCode());
        assertNull(flavor.getVersionName());
        assertNull(flavor.getApplicationId());
        assertNull(flavor.getTestApplicationId());
        assertNull(flavor.getTestInstrumentationRunner());
        assertNull(flavor.getTestHandleProfiling());
        assertNull(flavor.getTestFunctionalTest());
    }

    public void testResourceConfigMerge() {
        ProductFlavor flavor = DefaultProductFlavor.mergeFlavors(mCustom2, mCustom);

        Collection<String> configs = flavor.getResourceConfigurations();
        assertEquals(2, configs.size());
        assertTrue(configs.contains("hdpi"));
        assertTrue(configs.contains("ldpi"));
    }

    public void testManifestPlaceholdersMerge() {
        ProductFlavor flavor = DefaultProductFlavor.mergeFlavors(mCustom2, mCustom);

        Map<String, Object> manifestPlaceholders = flavor.getManifestPlaceholders();
        assertEquals(3, manifestPlaceholders.size());
        assertEquals("oneValue", manifestPlaceholders.get("one"));
        assertEquals("twoValue", manifestPlaceholders.get("two"));
        assertEquals("threeValue", manifestPlaceholders.get("three"));

    }

    public void testResValuesMerge() {
        ProductFlavor flavor = DefaultProductFlavor.mergeFlavors(mCustom2, mCustom);

        Map<String, ClassField> resValues = flavor.getResValues();
        assertEquals(3, resValues.size());
        assertEquals("oneValue", resValues.get("one").getValue());
        assertEquals("twoValue", resValues.get("two").getValue());
        assertEquals("threeValue", resValues.get("three").getValue());
    }

    public void testBuildConfigFieldMerge() {
        ProductFlavor flavor = DefaultProductFlavor.mergeFlavors(mCustom2, mCustom);

        Map<String, ClassField> buildConfigFields = flavor.getBuildConfigFields();
        assertEquals(3, buildConfigFields.size());
        assertEquals("oneValue", buildConfigFields.get("one").getValue());
        assertEquals("twoValue", buildConfigFields.get("two").getValue());
        assertEquals("threeValue", buildConfigFields.get("three").getValue());
    }
}
