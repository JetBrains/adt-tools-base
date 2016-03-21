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
import com.example.basic.ControlClass;
import com.ir.disable.InstantRunDisabledClass;
import com.ir.disable.InstantRunDisabledMethod;
import com.ir.disable.all.DisabledClassOne;
import com.ir.disable.all.DisabledClassTwo;

import org.junit.ClassRule;
import org.junit.Test;

/**
 * Test class for InstantRun disabled package, classes and methods
 */
public class InstantRunDisabledTest {

    @ClassRule
    public static ClassEnhancement harness = new ClassEnhancement();

    @Test
    public void testDisabledPackage()
            throws ClassNotFoundException, NoSuchFieldException, InstantiationException,
            IllegalAccessException {
        harness.reset();

        DisabledClassOne classOne = new DisabledClassOne();
        DisabledClassTwo classTwo = new DisabledClassTwo();
        assertThat(classOne.getValue()).isEqualTo("original");
        assertThat(classTwo.getValue()).isEqualTo("original");

        ControlClass controlClass = new ControlClass();
        assertThat(controlClass.getValue()).isEqualTo("hello");

        harness.applyPatch("changeBaseClass");
        assertThat(classOne.getValue()).isEqualTo("original");
        assertThat(classTwo.getValue()).isEqualTo("original");

        classOne = new DisabledClassOne();
        classTwo = new DisabledClassTwo();
        assertThat(classOne.getValue()).isEqualTo("original");
        assertThat(classTwo.getValue()).isEqualTo("original");

        // control that runtime was properly setup.
        assertThat(controlClass.getValue()).isEqualTo("modified");
    }

    @Test
    public void testDisabledClass()
            throws ClassNotFoundException, NoSuchFieldException, InstantiationException,
            IllegalAccessException {
        harness.reset();

        InstantRunDisabledClass disabledClass = new InstantRunDisabledClass();
        assertThat(InstantRunDisabledClass.getStaticString()).isEqualTo("Original");
        assertThat(disabledClass.stringField).isEqualTo("set in original ctor");
        assertThat(disabledClass.getString()).isEqualTo("original method");

        ControlClass controlClass = new ControlClass();
        assertThat(controlClass.getValue()).isEqualTo("hello");

        harness.applyPatch("changeBaseClass");
        assertThat(InstantRunDisabledClass.getStaticString()).isEqualTo("Original");
        assertThat(disabledClass.stringField).isEqualTo("set in original ctor");
        assertThat(disabledClass.getString()).isEqualTo("original method");

        disabledClass = new InstantRunDisabledClass();
        assertThat(InstantRunDisabledClass.getStaticString()).isEqualTo("Original");
        assertThat(disabledClass.stringField).isEqualTo("set in original ctor");
        assertThat(disabledClass.getString()).isEqualTo("original method");
        assertThat(disabledClass.getString()).isEqualTo("original method");

        // control that runtime was properly setup.
        assertThat(controlClass.getValue()).isEqualTo("modified");
    }

    @Test
    public void testDisabledMethods()
            throws ClassNotFoundException, NoSuchFieldException, InstantiationException,
            IllegalAccessException {
         harness.reset();

        InstantRunDisabledMethod methods = new InstantRunDisabledMethod();
        assertThat(methods.getStringField()).isEqualTo("non alterable ctor");
        assertThat(methods.alterableMethod()).isEqualTo("alterable original");
        assertThat(methods.nonAlterableMethod()).isEqualTo("non alterable original");
        assertThat(methods.finalAlterableMethod()).isEqualTo("final alterable original");
        assertThat(methods.finalNonAlterableMethod()).isEqualTo("final non alterable original");
        assertThat(InstantRunDisabledMethod.alterableStaticMethod()).isEqualTo(
                "alterable static original");
        assertThat(InstantRunDisabledMethod.nonAlterableStaticMethod()).isEqualTo(
                "non alterable static original");

        assertThat(new InstantRunDisabledMethod("hello").getStringField()).isEqualTo("hello");

        ControlClass controlClass = new ControlClass();
        assertThat(controlClass.getValue()).isEqualTo("hello");

        harness.applyPatch("changeBaseClass");
        assertThat(methods.alterableMethod()).isEqualTo("alterable updated");
        assertThat(methods.nonAlterableMethod()).isEqualTo("non alterable original");
        assertThat(methods.finalAlterableMethod()).isEqualTo("final alterable updated");
        assertThat(methods.finalNonAlterableMethod()).isEqualTo("final non alterable original");
        assertThat(InstantRunDisabledMethod.alterableStaticMethod()).isEqualTo(
                "alterable static updated");
        assertThat(InstantRunDisabledMethod.nonAlterableStaticMethod()).isEqualTo(
                "non alterable static original");

        methods = new InstantRunDisabledMethod();
        assertThat(methods.getStringField()).isEqualTo("non alterable ctor");
        assertThat(methods.alterableMethod()).isEqualTo("alterable updated");
        assertThat(methods.nonAlterableMethod()).isEqualTo("non alterable original");
        assertThat(methods.finalAlterableMethod()).isEqualTo("final alterable updated");
        assertThat(methods.finalNonAlterableMethod()).isEqualTo("final non alterable original");
        assertThat(InstantRunDisabledMethod.alterableStaticMethod()).isEqualTo(
                "alterable static updated");
        assertThat(InstantRunDisabledMethod.nonAlterableStaticMethod()).isEqualTo(
                "non alterable static original");

        // disabled due to constructor code.

        //assertThat(new InstantRunDisabledMethod("hello").getStringField()).isEqualTo("modified hello");
        //
        //// control that runtime was properly setup.
        //assertThat(controlClass.getValue()).isEqualTo("modified");
    }
}

