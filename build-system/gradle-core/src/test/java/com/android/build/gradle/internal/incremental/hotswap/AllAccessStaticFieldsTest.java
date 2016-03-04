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

package com.android.build.gradle.internal.incremental.hotswap;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.build.gradle.internal.incremental.fixture.ClassEnhancement;
import com.example.basic.AllAccessStaticFields;

import org.junit.ClassRule;
import org.junit.Test;

/**
 * Tests for static fields and final static fields accesses from byte code enhanced code.
 */
public class AllAccessStaticFieldsTest {

    @ClassRule
    public static ClassEnhancement harness = new ClassEnhancement();

    @SuppressWarnings("AccessStaticViaInstance")
    @Test
    public void changeClassWithStaticFields() throws Exception {

        harness.reset();
        AllAccessStaticFields allAccessStaticFields = new AllAccessStaticFields();

        assertWithMessage("base: allAccessStaticFields.getAllFields()")
                .that(allAccessStaticFields.accessAllFields()).isEqualTo("12243648");

        assertWithMessage("base: allAccessStaticFields.getAllFields()")
                .that(AllAccessStaticFields.staticAccessAllFields()).isEqualTo("12243648");

        assertWithMessage("base: allAccessStaticFields.staticAccessAllFinalFields()")
                .that(AllAccessStaticFields.staticAccessAllFinalFields()).isEqualTo("14284256");

        assertWithMessage("base: allAccessStaticFields.getAllFields()")
                .that(allAccessStaticFields.staticAccessAllFinalFields()).isEqualTo("14284256");

        assertWithMessage("base: AllAccessStaticFields.publicInt")
                .that(AllAccessStaticFields.publicInt == 12);

        assertWithMessage("base: AllAccessStaticFields.")
                .that(AllAccessStaticFields.finalPublicInt == 56);

        assertWithMessage("base: allAccessStaticFields.publicInt")
                .that(allAccessStaticFields.publicInt == 12);

        harness.applyPatch("changeSubClass");

        assertWithMessage("changeSubClass: allAccessStaticFields.getAllFields()")
                .that(allAccessStaticFields.accessAllFields()).isEqualTo("49372513");

        assertWithMessage("changeSubClass: allAccessStaticFields.staticAccessAllFields()")
                .that(allAccessStaticFields.staticAccessAllFields()).isEqualTo("48362412");

        // it will set static fields to these values times two.
        allAccessStaticFields.setAllFields(11,22,33,44);

        // one should be added to above values*2
        assertWithMessage("changeSubClass: allAccessStaticFields.accessAllFields()")
                .that(allAccessStaticFields.accessAllFields()).isEqualTo("89674523");

        // should be above values multiplied by two only.
        assertWithMessage("changeSubClass: AllAccessStaticFields.accessAllFields()")
                .that(AllAccessStaticFields.staticAccessAllFields()).isEqualTo("88664422");

        assertWithMessage("changeSubClass: allAccessStaticFields.staticAccessAllFinalFields()")
                .that(allAccessStaticFields.staticAccessAllFinalFields()).isEqualTo("60453015");

        assertWithMessage("changeSubClass: AllAccessStaticFields.publicInt")
                .that(AllAccessStaticFields.publicInt == 22);

        assertWithMessage("changeSubClass: AllAccessStaticFields.")
                .that(AllAccessStaticFields.finalPublicInt == 60);

        assertWithMessage("changeSubClass: allAccessStaticFields.publicInt")
                .that(allAccessStaticFields.publicInt == 22);

        AllAccessStaticFields.staticSetAllFields(10, 20, 30, 40);
        assertWithMessage("changeSubClass: AllAccessStaticFields.accessAllFields()")
                .that(AllAccessStaticFields.staticAccessAllFields()).isEqualTo("40302010");

        // values should also be changed for the original class but with the original method impl.
        harness.reset();
        assertWithMessage("base: allAccessStaticFields.getAllFields()")
                .that(allAccessStaticFields.accessAllFields()).isEqualTo("10203040");

    }
}
