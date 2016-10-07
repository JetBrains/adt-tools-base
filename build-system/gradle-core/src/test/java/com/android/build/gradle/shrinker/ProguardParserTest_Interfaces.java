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

package com.android.build.gradle.shrinker;

import com.android.build.gradle.shrinker.TestClasses.Interfaces;
import com.google.common.io.Files;

import org.junit.Before;
import org.junit.Test;

import java.io.File;

/**
 * Tests for {@link com.android.build.gradle.shrinker.parser.ProguardParser}, related to interfaces
 * and annotations.
 */
public class ProguardParserTest_Interfaces extends AbstractShrinkerTest {

    @Before
    public void createClasses() throws Exception {
        Files.write(Interfaces.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Interfaces.myCharSequence(), new File(mTestPackageDir, "MyCharSequence.class"));
        Files.write(Interfaces.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(Interfaces.myImpl(), new File(mTestPackageDir, "MyImpl.class"));
        Files.write(Interfaces.namedRunnable(), new File(mTestPackageDir, "NamedRunnable.class"));
        Files.write(
                Interfaces.namedRunnableImpl(), new File(mTestPackageDir, "NamedRunnableImpl.class"));
        Files.write(Interfaces.doesSomething(), new File(mTestPackageDir, "DoesSomething.class"));
        Files.write(
                Interfaces.implementationFromSuperclass(),
                new File(mTestPackageDir, "ImplementationFromSuperclass.class"));
    }

    @Test
    public void interfaces_keepRules_interfaceOnInterface() throws Exception {
        run(parseKeepRules("-keep interface test/MyInterface"));
        assertMembersLeft("MyInterface");
    }

    @Test
    public void interfaces_keepRules_interfaceOnClass() throws Exception {
        run(parseKeepRules("-keep interface test/Main"));
        assertClassSkipped("Main");
    }

    @Test
    public void interfaces_keepRules_atInterfaceOnInterface() throws Exception {
        run(parseKeepRules("-keep @interface test/MyInterface"));
        assertClassSkipped("MyInterface");
    }
}
