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

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.internal.incremental.fixture.ClassEnhancement;
import com.example.basic.FieldOverridingChild;
import com.example.basic.FieldOverridingGrandChild;
import com.example.basic.FieldOverridingParent;

import org.junit.ClassRule;
import org.junit.Test;

/**
 * Test private fields overriding classes.
 */
@SuppressWarnings("AccessStaticViaInstance")
public class FieldOverridingTest {

    @ClassRule
    public static ClassEnhancement harness = new ClassEnhancement();

    @Test
    public void getField() throws Exception {
        harness.reset();

        FieldOverridingParent parent = new FieldOverridingParent();
        assertThat(parent.getField()).isEqualTo("parent");
        assertThat(FieldOverridingParent.getStaticField()).isEqualTo("static parent");
        assertThat(parent.getStaticField()).isEqualTo("static parent");
        assertThat(parent.getStaticCollection()).contains("static parent");

        FieldOverridingChild child = new FieldOverridingChild();
        assertThat(child.getField()).isEqualTo("parent");
        assertThat(child.field()).isWithin(0.1).of(11d);
        assertThat(FieldOverridingChild.staticField()).isWithin(0.1).of(10d);
        assertThat(child.staticField()).isWithin(0.1).of(10d);
        assertThat(child.getStaticCollection()).contains("static child");
        assertThat(child.getCollection()).contains("child");

        FieldOverridingGrandChild fieldOverridingGrandChild = new FieldOverridingGrandChild();
        assertThat(fieldOverridingGrandChild.getParentCollection()).contains("child");


        harness.applyPatch("changeSubClass");

        // use original instances.
        assertThat(parent.getField()).isEqualTo("modified parent");
        assertThat(child.getField()).isEqualTo("modified parent");
        assertThat(FieldOverridingParent.getStaticField()).isEqualTo("modified static parent");
        assertThat(parent.getStaticField()).isEqualTo("modified static parent");
        assertThat(parent.getStaticCollection()).contains("static parent");

        assertThat(child.field()).isWithin(0.1).of(11d);
        assertThat(FieldOverridingChild.staticField()).isWithin(0.1).of(10d);
        assertThat(child.staticField()).isWithin(0.1).of(10d);
        assertThat(child.getStaticCollection()).contains("static child");
        assertThat(child.getCollection()).contains("child");

        assertThat(fieldOverridingGrandChild.getParentCollection()).contains("child");

        // use new instances.

        // disabled due to constructor use.

        //parent = new FieldOverridingParent();
        //child = new FieldOverridingChild();
        //assertThat(parent.getField()).isEqualTo("modified modified parent");
        //assertThat(child.getField()).isEqualTo("modified modified parent");
        //assertThat(FieldOverridingParent.getStaticField()).isEqualTo("modified static parent");
        //assertThat(parent.getStaticField()).isEqualTo("modified static parent");
        //assertThat(parent.getStaticCollection()).contains("modified static parent");
        //
        //assertThat(child.field()).isWithin(0.1).of(12d);
        //assertThat(FieldOverridingChild.staticField()).isWithin(0.1).of(13d);
        //assertThat(child.staticField()).isWithin(0.1).of(13d);
        //assertThat(child.getStaticCollection()).contains("modified static child");
        //assertThat(child.getCollection()).contains("modified child");
        //
        //
        //fieldOverridingGrandChild = new FieldOverridingGrandChild();
        //assertThat(fieldOverridingGrandChild.getParentCollection()).contains("modified child");
    }
}
