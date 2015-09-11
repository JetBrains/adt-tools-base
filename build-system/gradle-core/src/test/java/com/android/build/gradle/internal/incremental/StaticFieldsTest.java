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

package com.android.build.gradle.internal.incremental;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.build.gradle.internal.incremental.fixture.ClassEnhancement;
import com.example.basic.StaticFields;

import org.junit.ClassRule;
import org.junit.Test;

import java.util.logging.Logger;

/**
 * Tests for static fields and final static fields accesses from byte code enhanced code.
 */
public class StaticFieldsTest {

    @ClassRule
    public static ClassEnhancement harness = new ClassEnhancement();

    @SuppressWarnings("AccessStaticViaInstance")
    @Test
    public void changeClassWithStaticFields() throws Exception {

        GenericInstantRuntime.setLogger(Logger.getLogger(ClassEnhancement.class.getName()));

        harness.reset();
        StaticFields staticFields = new StaticFields();

        assertWithMessage("base: staticFields.getAllFields()")
                .that(staticFields.accessAllFields()).isEqualTo("12243648");

        assertWithMessage("base: staticFields.getAllFields()")
                .that(StaticFields.staticAccessAllFields()).isEqualTo("12243648");

        assertWithMessage("base: staticFields.staticAccessAllFinalFields()")
                .that(StaticFields.staticAccessAllFinalFields()).isEqualTo("14284256");

        assertWithMessage("base: staticFields.getAllFields()")
                .that(staticFields.staticAccessAllFinalFields()).isEqualTo("14284256");

        assertWithMessage("base: StaticFields.publicInt")
                .that(StaticFields.publicInt == 12);

        assertWithMessage("base: StaticFields.")
                .that(StaticFields.finalPublicInt == 56);

        assertWithMessage("base: staticFields.publicInt")
                .that(staticFields.publicInt == 12);

        harness.applyPatch("changeSubClass");

        assertWithMessage("changeSubClass: staticFields.getAllFields()")
                .that(staticFields.accessAllFields()).isEqualTo("49372513");

        assertWithMessage("changeSubClass: staticFields.staticAccessAllFields()")
                .that(staticFields.staticAccessAllFields()).isEqualTo("48362412");

        // it will set static fields to these values times two.
        staticFields.setAllFields(11,22,33,44);

        // one should be added to above values*2
        assertWithMessage("changeSubClass: staticFields.accessAllFields()")
                .that(staticFields.accessAllFields()).isEqualTo("89674523");

        // should be above values multiplied by two only.
        assertWithMessage("changeSubClass: StaticFields.accessAllFields()")
                .that(StaticFields.staticAccessAllFields()).isEqualTo("88664422");

        assertWithMessage("changeSubClass: staticFields.staticAccessAllFinalFields()")
                .that(staticFields.staticAccessAllFinalFields()).isEqualTo("60453015");

        assertWithMessage("changeSubClass: StaticFields.publicInt")
                .that(StaticFields.publicInt == 22);

        assertWithMessage("changeSubClass: StaticFields.")
                .that(StaticFields.finalPublicInt == 60);

        assertWithMessage("changeSubClass: staticFields.publicInt")
                .that(staticFields.publicInt == 22);

        StaticFields.staticSetAllFields(10, 20, 30, 40);
        assertWithMessage("changeSubClass: StaticFields.accessAllFields()")
                .that(StaticFields.staticAccessAllFields()).isEqualTo("40302010");

        // values should also be changed for the original class but with the original method impl.
        harness.reset();
        assertWithMessage("base: staticFields.getAllFields()")
                .that(staticFields.accessAllFields()).isEqualTo("10203040");

    }
}
