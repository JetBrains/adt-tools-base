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

package com.android.build.gradle.shrinker;

import com.android.annotations.NonNull;
import com.android.build.api.transform.TransformInput;
import com.android.build.gradle.shrinker.TestClasses.InnerClasses;
import com.android.build.gradle.shrinker.TestClasses.Interfaces;
import com.android.build.gradle.shrinker.TestClasses.Reflection;
import com.android.ide.common.internal.WaitableExecutor;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

/**
 * Tests for {@link FullRunShrinker}.
 */
public class FullRunShrinkerTest extends AbstractShrinkerTest {

    private FullRunShrinker<String> mShrinker;

    @Before
    public void createShrinker() throws Exception {
        mShrinker = new FullRunShrinker<String>(
                WaitableExecutor.useGlobalSharedThreadPool(),
                buildGraph(),
                getPlatformJars(),
                mShrinkerLogger);
    }

    @NonNull
    private ShrinkerGraph<String> buildGraph() throws IOException {
        return JavaSerializationShrinkerGraph.empty(mIncrementalDir);
    }

    @Test
    public void simple_oneClass() throws Exception {
        // Given:
        Files.write(TestClasses.SimpleScenario.aaa(), new File(mTestPackageDir, "Aaa.class"));

        // When:
        run("Aaa", "aaa:()V");

        // Then:
        assertMembersLeft("Aaa", "aaa:()V", "bbb:()V");
    }

    @Test
    public void simple_threeClasses() throws Exception {
        // Given:
        Files.write(TestClasses.SimpleScenario.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(TestClasses.SimpleScenario.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(TestClasses.SimpleScenario.ccc(), new File(mTestPackageDir, "Ccc.class"));

        // When:
        run("Bbb", "bbb:(Ltest/Aaa;)V");

        // Then:
        assertMembersLeft("Aaa", "aaa:()V", "bbb:()V");
        assertMembersLeft("Bbb", "bbb:(Ltest/Aaa;)V");
        assertClassSkipped("Ccc");
    }

    @Test
    public void virtualCalls_keepEntryPointsSuperclass() throws Exception {
        // Given:
        Files.write(TestClasses.VirtualCalls.abstractClass(), new File(mTestPackageDir, "AbstractClass.class"));
        Files.write(TestClasses.VirtualCalls.impl(1), new File(mTestPackageDir, "Impl1.class"));

        // When:
        run("Impl1", "abstractMethod:()V");

        // Then:
        assertMembersLeft("Impl1", "abstractMethod:()V");
        assertMembersLeft("AbstractClass");
    }

    @Test
    public void virtualCalls_abstractType() throws Exception {
        // Given:
        Files.write(
                TestClasses.VirtualCalls.main_abstractType(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.VirtualCalls.abstractClass(), new File(mTestPackageDir, "AbstractClass.class"));
        Files.write(TestClasses.VirtualCalls.impl(1), new File(mTestPackageDir, "Impl1.class"));
        Files.write(TestClasses.VirtualCalls.impl(2), new File(mTestPackageDir, "Impl2.class"));
        Files.write(TestClasses.VirtualCalls.impl(3), new File(mTestPackageDir, "Impl3.class"));

        // When:
        run("Main", "main:([Ljava/lang/String;)V");

        // Then:
        assertMembersLeft("Main", "main:([Ljava/lang/String;)V");
        assertClassSkipped("Impl3");
        assertMembersLeft("AbstractClass", "abstractMethod:()V", "<init>:()V");
        assertMembersLeft("Impl1", "abstractMethod:()V", "<init>:()V");
        assertMembersLeft("Impl2", "abstractMethod:()V", "<init>:()V");
    }

    @Test
    public void virtualCalls_concreteType() throws Exception {
        // Given:
        Files.write(
                TestClasses.VirtualCalls.main_concreteType(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.VirtualCalls.abstractClass(), new File(mTestPackageDir, "AbstractClass.class"));
        Files.write(TestClasses.VirtualCalls.impl(1), new File(mTestPackageDir, "Impl1.class"));
        Files.write(TestClasses.VirtualCalls.impl(2), new File(mTestPackageDir, "Impl2.class"));
        Files.write(TestClasses.VirtualCalls.impl(3), new File(mTestPackageDir, "Impl3.class"));

        // When:
        run("Main", "main:([Ljava/lang/String;)V");

        // Then:
        assertMembersLeft("Main", "main:([Ljava/lang/String;)V");
        assertClassSkipped("Impl3");
        assertMembersLeft("AbstractClass", "<init>:()V");
        assertMembersLeft("Impl1", "abstractMethod:()V", "<init>:()V");
        assertMembersLeft("Impl2", "<init>:()V");
    }

    @Test
    public void virtualCalls_methodFromParent() throws Exception {
        // Given:
        Files.write(
                TestClasses.VirtualCalls.main_parentChild(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.VirtualCalls.parent(), new File(mTestPackageDir, "Parent.class"));
        Files.write(TestClasses.VirtualCalls.child(), new File(mTestPackageDir, "Child.class"));

        // When:
        run("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "main:()V");
        assertMembersLeft("Parent", "<init>:()V", "onlyInParent:()V");
        assertMembersLeft("Child", "<init>:()V");
    }

    @Test
    public void sdkTypes_methodsFromJavaClasses() throws Exception {
        // Given:
        Files.write(TestClasses.SdkTypes.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(
                TestClasses.SdkTypes.myException(), new File(mTestPackageDir, "MyException.class"));

        // When:
        run("Main", "main:([Ljava/lang/String;)V");

        // Then:
        assertMembersLeft("Main", "main:([Ljava/lang/String;)V");
        assertMembersLeft(
                "MyException",
                "<init>:()V",
                "hashCode:()I",
                "getMessage:()Ljava/lang/String;");
    }

    @Test
    public void interfaces_sdkInterface_classUsed_abstractType() throws Exception {
        // Given:
        Files.write(Interfaces.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Interfaces.myCharSequence(), new File(mTestPackageDir, "MyCharSequence.class"));
        Files.write(Interfaces.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(Interfaces.myImpl(), new File(mTestPackageDir, "MyImpl.class"));
        Files.write(Interfaces.doesSomething(), new File(mTestPackageDir, "DoesSomething.class"));
        Files.write(Interfaces.namedRunnable(), new File(mTestPackageDir, "NamedRunnable.class"));
        Files.write(Interfaces.namedRunnableImpl(), new File(mTestPackageDir, "NamedRunnableImpl.class"));
        Files.write(
                Interfaces.implementationFromSuperclass(),
                new File(mTestPackageDir, "ImplementationFromSuperclass.class"));

        // When:
        run(
                "Main",
                "buildMyCharSequence:()Ltest/MyCharSequence;",
                "callCharSequence:(Ljava/lang/CharSequence;)V");

        // Then:
        assertMembersLeft(
                "Main",
                "buildMyCharSequence:()Ltest/MyCharSequence;",
                "callCharSequence:(Ljava/lang/CharSequence;)V");
        assertMembersLeft(
                "MyCharSequence",
                "subSequence:(II)Ljava/lang/CharSequence;",
                "charAt:(I)C",
                "length:()I",
                "<init>:()V");
        assertClassSkipped("MyInterface");
        assertClassSkipped("MyImpl");

        assertImplements("MyCharSequence", "java/lang/CharSequence");
    }

    @Test
    public void interfaces_sdkInterface_implementedIndirectly() throws Exception {
        // Given:
        Files.write(Interfaces.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Interfaces.namedRunnable(), new File(mTestPackageDir, "NamedRunnable.class"));
        Files.write(Interfaces.namedRunnableImpl(), new File(mTestPackageDir, "NamedRunnableImpl.class"));
        Files.write(Interfaces.myCharSequence(), new File(mTestPackageDir, "MyCharSequence.class"));
        Files.write(Interfaces.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(Interfaces.myImpl(), new File(mTestPackageDir, "MyImpl.class"));
        Files.write(Interfaces.doesSomething(), new File(mTestPackageDir, "DoesSomething.class"));
        Files.write(
                Interfaces.implementationFromSuperclass(),
                new File(mTestPackageDir, "ImplementationFromSuperclass.class"));

        // When:
        run(
                "Main",
                "buildNamedRunnableImpl:()Ltest/NamedRunnableImpl;",
                "callRunnable:(Ljava/lang/Runnable;)V");

        // Then:
        assertMembersLeft(
                "Main",
                "buildNamedRunnableImpl:()Ltest/NamedRunnableImpl;",
                "callRunnable:(Ljava/lang/Runnable;)V");
        assertMembersLeft(
                "NamedRunnableImpl",
                "run:()V",
                "<init>:()V");

        assertMembersLeft("NamedRunnable");
        assertImplements("NamedRunnableImpl", "test/NamedRunnable");
    }

    @Test
    public void interfaces_sdkInterface_classUsed_concreteType() throws Exception {
        // Given:
        Files.write(Interfaces.namedRunnable(), new File(mTestPackageDir, "NamedRunnable.class"));
        Files.write(Interfaces.namedRunnableImpl(), new File(mTestPackageDir, "NamedRunnableImpl.class"));
        Files.write(Interfaces.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Interfaces.myCharSequence(), new File(mTestPackageDir, "MyCharSequence.class"));
        Files.write(Interfaces.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(Interfaces.myImpl(), new File(mTestPackageDir, "MyImpl.class"));
        Files.write(Interfaces.doesSomething(), new File(mTestPackageDir, "DoesSomething.class"));
        Files.write(
                Interfaces.implementationFromSuperclass(),
                new File(mTestPackageDir, "ImplementationFromSuperclass.class"));

        // When:
        run(
                "Main",
                "buildMyCharSequence:()Ltest/MyCharSequence;",
                "callMyCharSequence:(Ltest/MyCharSequence;)V");

        // Then:
        assertMembersLeft(
                "Main",
                "buildMyCharSequence:()Ltest/MyCharSequence;",
                "callMyCharSequence:(Ltest/MyCharSequence;)V");
        assertMembersLeft(
                "MyCharSequence",
                "subSequence:(II)Ljava/lang/CharSequence;",
                "charAt:(I)C",
                "length:()I",
                "<init>:()V");
        assertClassSkipped("MyInterface");
        assertClassSkipped("MyImpl");

        assertImplements("MyCharSequence", "java/lang/CharSequence");
    }

    @Test
    public void interfaces_implementationFromSuperclass() throws Exception {
        // Given:
        Files.write(Interfaces.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Interfaces.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(Interfaces.myCharSequence(), new File(mTestPackageDir, "MyCharSequence.class"));
        Files.write(Interfaces.myImpl(), new File(mTestPackageDir, "MyImpl.class"));
        Files.write(Interfaces.namedRunnable(), new File(mTestPackageDir, "NamedRunnable.class"));
        Files.write(Interfaces.namedRunnableImpl(), new File(mTestPackageDir, "NamedRunnableImpl.class"));
        Files.write(Interfaces.doesSomething(), new File(mTestPackageDir, "DoesSomething.class"));
        Files.write(
                Interfaces.implementationFromSuperclass(),
                new File(mTestPackageDir, "ImplementationFromSuperclass.class"));

        // When:
        run(
                "Main",
                "useImplementationFromSuperclass:(Ltest/ImplementationFromSuperclass;)V",
                "useMyInterface:(Ltest/MyInterface;)V");

        // Then:
        assertMembersLeft(
                "Main",
                "useImplementationFromSuperclass:(Ltest/ImplementationFromSuperclass;)V",
                "useMyInterface:(Ltest/MyInterface;)V");
        assertMembersLeft("ImplementationFromSuperclass");
        assertMembersLeft(
                "MyInterface",
                "doSomething:(Ljava/lang/Object;)V");
        assertClassSkipped("MyImpl");
        assertClassSkipped("MyCharSequence");

        // This is the tricky part: this method should be kept, because a subclass is using it to
        // implement an interface.
        assertMembersLeft(
                "DoesSomething",
                "doSomething:(Ljava/lang/Object;)V");

        assertImplements("ImplementationFromSuperclass", "test/MyInterface");
    }

    @Test
    public void interfaces_implementationFromSuperclass_interfaceInheritance() throws Exception {
        // Given:
        Files.write(Interfaces.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Interfaces.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(Interfaces.mySubInterface(), new File(mTestPackageDir, "MySubInterface.class"));
        Files.write(Interfaces.myCharSequence(), new File(mTestPackageDir, "MyCharSequence.class"));
        Files.write(Interfaces.myImpl(), new File(mTestPackageDir, "MyImpl.class"));
        Files.write(Interfaces.namedRunnable(), new File(mTestPackageDir, "NamedRunnable.class"));
        Files.write(Interfaces.namedRunnableImpl(), new File(mTestPackageDir, "NamedRunnableImpl.class"));
        Files.write(Interfaces.doesSomething(), new File(mTestPackageDir, "DoesSomething.class"));
        Files.write(
                Interfaces.implementationFromSuperclass_subInterface(),
                new File(mTestPackageDir, "ImplementationFromSuperclass.class"));

        // When:
        run(
                "Main",
                "useImplementationFromSuperclass:(Ltest/ImplementationFromSuperclass;)V",
                "useMyInterface:(Ltest/MyInterface;)V");

        // Then:
        assertMembersLeft(
                "Main",
                "useImplementationFromSuperclass:(Ltest/ImplementationFromSuperclass;)V",
                "useMyInterface:(Ltest/MyInterface;)V");
        assertMembersLeft("ImplementationFromSuperclass");
        assertMembersLeft(
                "MyInterface",
                "doSomething:(Ljava/lang/Object;)V");

        // This is the tricky part: this method should be kept, because a subclass is using it to
        // implement an interface.
        assertMembersLeft(
                "DoesSomething",
                "doSomething:(Ljava/lang/Object;)V");

        assertMembersLeft("MySubInterface");
        assertClassSkipped("MyImpl");
        assertClassSkipped("MyCharSequence");

        assertImplements("ImplementationFromSuperclass", "test/MySubInterface");
    }

    @Test
    public void interfaces_sdkInterface_classNotUsed() throws Exception {
        // Given:
        Files.write(Interfaces.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Interfaces.myCharSequence(), new File(mTestPackageDir, "MyCharSequence.class"));
        Files.write(Interfaces.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(Interfaces.myImpl(), new File(mTestPackageDir, "MyImpl.class"));
        Files.write(Interfaces.namedRunnable(), new File(mTestPackageDir, "NamedRunnable.class"));
        Files.write(Interfaces.namedRunnableImpl(), new File(mTestPackageDir, "NamedRunnableImpl.class"));
        Files.write(Interfaces.doesSomething(), new File(mTestPackageDir, "DoesSomething.class"));
        Files.write(
                Interfaces.implementationFromSuperclass(),
                new File(mTestPackageDir, "ImplementationFromSuperclass.class"));

        // When:
        run(
                "Main",
                "callCharSequence:(Ljava/lang/CharSequence;)V");

        // Then:
        assertMembersLeft(
                "Main",
                "callCharSequence:(Ljava/lang/CharSequence;)V");
        assertClassSkipped("MyCharSequence");
        assertClassSkipped("MyInterface");
        assertClassSkipped("MyImpl");
    }

    @Test
    public void interfaces_keepRules_interfaceOnInterface() throws Exception {
        // Given:
        Files.write(Interfaces.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Interfaces.myCharSequence(), new File(mTestPackageDir, "MyCharSequence.class"));
        Files.write(Interfaces.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(Interfaces.myImpl(), new File(mTestPackageDir, "MyImpl.class"));
        Files.write(Interfaces.namedRunnable(), new File(mTestPackageDir, "NamedRunnable.class"));
        Files.write(Interfaces.namedRunnableImpl(), new File(mTestPackageDir, "NamedRunnableImpl.class"));
        Files.write(Interfaces.doesSomething(), new File(mTestPackageDir, "DoesSomething.class"));
        Files.write(
                Interfaces.implementationFromSuperclass(),
                new File(mTestPackageDir, "ImplementationFromSuperclass.class"));

        // When:
        run(parseKeepRules("-keep interface test/MyInterface"));

        // Then:
        assertMembersLeft("MyInterface");
    }

    @Test
    public void interfaces_keepRules_interfaceOnClass() throws Exception {
        // Given:
        Files.write(Interfaces.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Interfaces.myCharSequence(), new File(mTestPackageDir, "MyCharSequence.class"));
        Files.write(Interfaces.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(Interfaces.myImpl(), new File(mTestPackageDir, "MyImpl.class"));
        Files.write(Interfaces.namedRunnable(), new File(mTestPackageDir, "NamedRunnable.class"));
        Files.write(Interfaces.namedRunnableImpl(), new File(mTestPackageDir, "NamedRunnableImpl.class"));
        Files.write(Interfaces.doesSomething(), new File(mTestPackageDir, "DoesSomething.class"));
        Files.write(
                Interfaces.implementationFromSuperclass(),
                new File(mTestPackageDir, "ImplementationFromSuperclass.class"));

        // When:
        run(parseKeepRules("-keep interface test/Main"));

        // Then:
        assertClassSkipped("Main");
    }

    @Test
    public void interfaces_keepRules_atInterfaceOnInterface() throws Exception {
        // Given:
        Files.write(Interfaces.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Interfaces.myCharSequence(), new File(mTestPackageDir, "MyCharSequence.class"));
        Files.write(Interfaces.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(Interfaces.myImpl(), new File(mTestPackageDir, "MyImpl.class"));
        Files.write(Interfaces.namedRunnable(), new File(mTestPackageDir, "NamedRunnable.class"));
        Files.write(Interfaces.namedRunnableImpl(), new File(mTestPackageDir, "NamedRunnableImpl.class"));
        Files.write(Interfaces.doesSomething(), new File(mTestPackageDir, "DoesSomething.class"));
        Files.write(
                Interfaces.implementationFromSuperclass(),
                new File(mTestPackageDir, "ImplementationFromSuperclass.class"));

        // When:
        run(parseKeepRules("-keep @interface test/MyInterface"));

        // Then:
        assertClassSkipped("MyInterface");
    }

    @Test
    public void interfaces_appInterface_abstractType() throws Exception {
        // Given:
        Files.write(Interfaces.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Interfaces.myCharSequence(), new File(mTestPackageDir, "MyCharSequence.class"));
        Files.write(Interfaces.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(Interfaces.myImpl(), new File(mTestPackageDir, "MyImpl.class"));
        Files.write(Interfaces.namedRunnable(), new File(mTestPackageDir, "NamedRunnable.class"));
        Files.write(Interfaces.namedRunnableImpl(), new File(mTestPackageDir, "NamedRunnableImpl.class"));
        Files.write(Interfaces.doesSomething(), new File(mTestPackageDir, "DoesSomething.class"));
        Files.write(
                Interfaces.implementationFromSuperclass(),
                new File(mTestPackageDir, "ImplementationFromSuperclass.class"));

        // When:
        run(
                "Main",
                "buildMyImpl:()Ltest/MyImpl;",
                "useMyInterface:(Ltest/MyInterface;)V");

        // Then:
        assertMembersLeft(
                "Main",
                "buildMyImpl:()Ltest/MyImpl;",
                "useMyInterface:(Ltest/MyInterface;)V");
        assertMembersLeft(
                "MyInterface",
                "doSomething:(Ljava/lang/Object;)V");
        assertMembersLeft(
                "MyImpl",
                "<init>:()V",
                "doSomething:(Ljava/lang/Object;)V");
        assertClassSkipped("MyCharSequence");

        assertImplements("MyImpl", "test/MyInterface");
    }

    @Test
    public void interfaces_appInterface_concreteType() throws Exception {
        // Given:
        Files.write(Interfaces.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Interfaces.myCharSequence(), new File(mTestPackageDir, "MyCharSequence.class"));
        Files.write(Interfaces.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(Interfaces.myImpl(), new File(mTestPackageDir, "MyImpl.class"));
        Files.write(Interfaces.namedRunnable(), new File(mTestPackageDir, "NamedRunnable.class"));
        Files.write(Interfaces.namedRunnableImpl(), new File(mTestPackageDir, "NamedRunnableImpl.class"));
        Files.write(Interfaces.doesSomething(), new File(mTestPackageDir, "DoesSomething.class"));
        Files.write(
                Interfaces.implementationFromSuperclass(),
                new File(mTestPackageDir, "ImplementationFromSuperclass.class"));

        // When:
        run("Main", "useMyImpl_interfaceMethod:(Ltest/MyImpl;)V");

        // Then:
        assertMembersLeft(
                "Main",
                "useMyImpl_interfaceMethod:(Ltest/MyImpl;)V");
        assertClassSkipped("MyInterface");
        assertMembersLeft("MyImpl", "doSomething:(Ljava/lang/Object;)V");
        assertClassSkipped("MyCharSequence");

        assertDoesntImplement("MyImpl", "test/MyInterface");
    }

    @Test
    public void interfaces_appInterface_interfaceNotUsed() throws Exception {
        // Given:
        Files.write(Interfaces.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Interfaces.myCharSequence(), new File(mTestPackageDir, "MyCharSequence.class"));
        Files.write(Interfaces.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(Interfaces.myImpl(), new File(mTestPackageDir, "MyImpl.class"));
        Files.write(Interfaces.namedRunnable(), new File(mTestPackageDir, "NamedRunnable.class"));
        Files.write(Interfaces.namedRunnableImpl(), new File(mTestPackageDir, "NamedRunnableImpl.class"));
        Files.write(Interfaces.doesSomething(), new File(mTestPackageDir, "DoesSomething.class"));
        Files.write(
                Interfaces.implementationFromSuperclass(),
                new File(mTestPackageDir, "ImplementationFromSuperclass.class"));

        // When:
        run("Main", "useMyImpl_otherMethod:(Ltest/MyImpl;)V");

        // Then:
        assertMembersLeft(
                "Main",
                "useMyImpl_otherMethod:(Ltest/MyImpl;)V");
        assertClassSkipped("MyInterface");
        assertMembersLeft(
                "MyImpl",
                "someOtherMethod:()V");
        assertClassSkipped("MyCharSequence");

        assertDoesntImplement("MyImpl", "test/MyInterface");
    }

    @Test
    public void fields() throws Exception {
        // Given:
        Files.write(TestClasses.Fields.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.Fields.myFields(), new File(mTestPackageDir, "MyFields.class"));
        Files.write(TestClasses.Fields.myFieldsSubclass(), new File(mTestPackageDir, "MyFieldsSubclass.class"));
        Files.write(TestClasses.emptyClass("MyFieldType"), new File(mTestPackageDir, "MyFieldType.class"));

        // When:
        run("Main", "main:()I");

        // Then:
        assertMembersLeft(
                "Main",
                "main:()I");
        assertMembersLeft(
                "MyFields",
                "<init>:()V",
                "<clinit>:()V",
                "readField:()I",
                "f1:I",
                "f2:I",
                "f4:Ltest/MyFieldType;",
                "sString:Ljava/lang/String;");
        assertMembersLeft("MyFieldType");
    }

    @Test
    public void fields_fromSuperclass() throws Exception {
        // Given:
        Files.write(TestClasses.Fields.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.Fields.myFields(), new File(mTestPackageDir, "MyFields.class"));
        Files.write(TestClasses.Fields.myFieldsSubclass(), new File(mTestPackageDir, "MyFieldsSubclass.class"));
        Files.write(TestClasses.emptyClass("MyFieldType"), new File(mTestPackageDir, "MyFieldType.class"));

        // When:
        run("Main", "main_subclass:()I");

        // Then:
        assertMembersLeft(
                "Main",
                "main_subclass:()I");
        assertMembersLeft(
                "MyFields",
                "<init>:()V",
                "<clinit>:()V",
                "f1:I",
                "f2:I",
                "sString:Ljava/lang/String;");
        assertMembersLeft(
                "MyFieldsSubclass",
                "<init>:()V");
        assertClassSkipped("MyFieldType");
    }

    @Test
    public void overrides_methodNotUsed() throws Exception {
        // Given:
        Files.write(
                TestClasses.MultipleOverriddenMethods.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.MultipleOverriddenMethods.interfaceOne(), new File(mTestPackageDir, "InterfaceOne.class"));
        Files.write(TestClasses.MultipleOverriddenMethods.interfaceTwo(), new File(mTestPackageDir, "InterfaceTwo.class"));
        Files.write(TestClasses.MultipleOverriddenMethods.implementation(), new File(mTestPackageDir, "Implementation.class"));

        // When:
        run("Main", "buildImplementation:()V");

        // Then:
        assertMembersLeft(
                "Main",
                "buildImplementation:()V");
        assertClassSkipped("InterfaceOne");
        assertClassSkipped("InterfaceTwo");
        assertMembersLeft("Implementation", "<init>:()V");
    }

    @Test
    public void overrides_classNotUsed() throws Exception {
        // Given:
        Files.write(
                TestClasses.MultipleOverriddenMethods.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.MultipleOverriddenMethods.interfaceOne(), new File(mTestPackageDir, "InterfaceOne.class"));
        Files.write(TestClasses.MultipleOverriddenMethods.interfaceTwo(), new File(mTestPackageDir, "InterfaceTwo.class"));
        Files.write(TestClasses.MultipleOverriddenMethods.implementation(), new File(mTestPackageDir, "Implementation.class"));

        // When:
        run(
                "Main",
                "useInterfaceOne:(Ltest/InterfaceOne;)V",
                "useInterfaceTwo:(Ltest/InterfaceTwo;)V");

        // Then:
        assertMembersLeft(
                "Main",
                "useInterfaceOne:(Ltest/InterfaceOne;)V",
                "useInterfaceTwo:(Ltest/InterfaceTwo;)V");
        assertMembersLeft("InterfaceOne", "m:()V");
        assertMembersLeft("InterfaceTwo", "m:()V");
        assertClassSkipped("MyImplementation");
    }

    @Test
    public void overrides_interfaceOneUsed_classUsed() throws Exception {
        // Given:
        Files.write(
                TestClasses.MultipleOverriddenMethods.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.MultipleOverriddenMethods.interfaceOne(), new File(mTestPackageDir, "InterfaceOne.class"));
        Files.write(TestClasses.MultipleOverriddenMethods.interfaceTwo(), new File(mTestPackageDir, "InterfaceTwo.class"));
        Files.write(TestClasses.MultipleOverriddenMethods.implementation(), new File(mTestPackageDir, "Implementation.class"));

        // When:
        run(
                "Main",
                "useInterfaceOne:(Ltest/InterfaceOne;)V",
                "buildImplementation:()V");

        // Then:
        assertMembersLeft(
                "Main",
                "useInterfaceOne:(Ltest/InterfaceOne;)V",
                "buildImplementation:()V");
        assertMembersLeft("InterfaceOne", "m:()V");
        assertClassSkipped("InterfaceTwo");
        assertMembersLeft("Implementation", "<init>:()V", "m:()V");
    }

    @Test
    public void overrides_interfaceTwoUsed_classUsed() throws Exception {
        // Given:
        Files.write(
                TestClasses.MultipleOverriddenMethods.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.MultipleOverriddenMethods.interfaceOne(), new File(mTestPackageDir, "InterfaceOne.class"));
        Files.write(TestClasses.MultipleOverriddenMethods.interfaceTwo(), new File(mTestPackageDir, "InterfaceTwo.class"));
        Files.write(TestClasses.MultipleOverriddenMethods.implementation(), new File(mTestPackageDir, "Implementation.class"));

        // When:
        run(
                "Main",
                "useInterfaceTwo:(Ltest/InterfaceTwo;)V",
                "buildImplementation:()V");

        // Then:
        assertMembersLeft(
                "Main",
                "useInterfaceTwo:(Ltest/InterfaceTwo;)V",
                "buildImplementation:()V");
        assertMembersLeft("InterfaceTwo", "m:()V");
        assertClassSkipped("InterfaceOne");
        assertMembersLeft("Implementation", "<init>:()V", "m:()V");
    }

    @Test
    public void overrides_twoInterfacesUsed_classUsed() throws Exception {
        // Given:
        Files.write(
                TestClasses.MultipleOverriddenMethods.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.MultipleOverriddenMethods.interfaceOne(), new File(mTestPackageDir, "InterfaceOne.class"));
        Files.write(TestClasses.MultipleOverriddenMethods.interfaceTwo(), new File(mTestPackageDir, "InterfaceTwo.class"));
        Files.write(TestClasses.MultipleOverriddenMethods.implementation(), new File(mTestPackageDir, "Implementation.class"));

        // When:
        run(
                "Main",
                "useInterfaceOne:(Ltest/InterfaceOne;)V",
                "useInterfaceTwo:(Ltest/InterfaceTwo;)V",
                "buildImplementation:()V");

        // Then:
        assertMembersLeft(
                "Main",
                "useInterfaceOne:(Ltest/InterfaceOne;)V",
                "useInterfaceTwo:(Ltest/InterfaceTwo;)V",
                "buildImplementation:()V");
        assertMembersLeft("InterfaceOne", "m:()V");
        assertMembersLeft("InterfaceTwo", "m:()V");
        assertMembersLeft("Implementation", "<init>:()V", "m:()V");
    }

    @Test
    public void overrides_noInterfacesUsed_classUsed() throws Exception {
        // Given:
        Files.write(
                TestClasses.MultipleOverriddenMethods.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.MultipleOverriddenMethods.interfaceOne(), new File(mTestPackageDir, "InterfaceOne.class"));
        Files.write(TestClasses.MultipleOverriddenMethods.interfaceTwo(), new File(mTestPackageDir, "InterfaceTwo.class"));
        Files.write(TestClasses.MultipleOverriddenMethods.implementation(), new File(mTestPackageDir, "Implementation.class"));

        // When:
        run(
                "Main",
                "useImplementation:(Ltest/Implementation;)V",
                "buildImplementation:()V");

        // Then:
        assertMembersLeft(
                "Main",
                "useImplementation:(Ltest/Implementation;)V",
                "buildImplementation:()V");
        assertClassSkipped("InterfaceOne");
        assertClassSkipped("InterfaceTwo");
        assertMembersLeft("Implementation", "<init>:()V", "m:()V");
    }

    @Test
    public void annotations_annotatedClass() throws Exception {
        // Given:
        Files.write(TestClasses.Annotations.main_annotatedClass(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.Annotations.myAnnotation(), new File(mTestPackageDir, "MyAnnotation.class"));
        Files.write(TestClasses.Annotations.myEnum(), new File(mTestPackageDir, "MyEnum.class"));
        Files.write(TestClasses.Annotations.nested(), new File(mTestPackageDir, "Nested.class"));
        Files.write(TestClasses.emptyClass("SomeClass"), new File(mTestPackageDir, "SomeClass.class"));
        Files.write(TestClasses.emptyClass("SomeOtherClass"), new File(mTestPackageDir, "SomeOtherClass.class"));

        // When:
        run("Main", "main:()V");

        // Then:
        assertMembersLeft(
                "Main",
                "main:()V");
        assertMembersLeft("SomeClass");
        assertMembersLeft("SomeOtherClass");
        assertMembersLeft(
                "MyAnnotation",
                "nested:()[Ltest/Nested;",
                "f:()I",
                "klass:()Ljava/lang/Class;",
                "myEnum:()Ltest/MyEnum;");
        assertMembersLeft("Nested", "name:()Ljava/lang/String;");
    }

    @Test
    public void annotations_annotatedMethod() throws Exception {
        // Given:
        Files.write(TestClasses.Annotations.main_annotatedMethod(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.Annotations.myAnnotation(), new File(mTestPackageDir, "MyAnnotation.class"));
        Files.write(TestClasses.Annotations.nested(), new File(mTestPackageDir, "Nested.class"));
        Files.write(TestClasses.Annotations.myEnum(), new File(mTestPackageDir, "MyEnum.class"));
        Files.write(TestClasses.emptyClass("SomeClass"), new File(mTestPackageDir, "SomeClass.class"));
        Files.write(TestClasses.emptyClass("SomeOtherClass"), new File(mTestPackageDir, "SomeOtherClass.class"));

        // When:
        run("Main", "main:()V");

        // Then:
        assertMembersLeft(
                "Main",
                "main:()V");
        assertMembersLeft("SomeClass");
        assertMembersLeft("SomeOtherClass");
        assertMembersLeft(
                "MyAnnotation",
                "nested:()[Ltest/Nested;",
                "f:()I",
                "klass:()Ljava/lang/Class;",
                "myEnum:()Ltest/MyEnum;");
        assertMembersLeft("Nested", "name:()Ljava/lang/String;");
    }

    @Test
    public void annotations_annotationsStripped() throws Exception {
        // Given:
        Files.write(TestClasses.Annotations.main_annotatedMethod(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.Annotations.myAnnotation(), new File(mTestPackageDir, "MyAnnotation.class"));
        Files.write(TestClasses.Annotations.nested(), new File(mTestPackageDir, "Nested.class"));
        Files.write(TestClasses.Annotations.myEnum(), new File(mTestPackageDir, "MyEnum.class"));
        Files.write(TestClasses.emptyClass("SomeClass"), new File(mTestPackageDir, "SomeClass.class"));
        Files.write(TestClasses.emptyClass("SomeOtherClass"), new File(mTestPackageDir, "SomeOtherClass.class"));

        // When:
        run("Main", "notAnnotated:()V");

        // Then:
        assertMembersLeft(
                "Main",
                "notAnnotated:()V");
        assertClassSkipped("SomeClass");
        assertClassSkipped("SomeOtherClass");
        assertClassSkipped("MyAnnotation");
        assertClassSkipped("Nested");
    }

    @Test
    public void annotations_keepRules_class() throws Exception {
        Files.write(TestClasses.Annotations.main_annotatedClass(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.Annotations.myAnnotation(), new File(mTestPackageDir, "MyAnnotation.class"));
        Files.write(TestClasses.Annotations.nested(), new File(mTestPackageDir, "Nested.class"));
        Files.write(TestClasses.Annotations.myEnum(), new File(mTestPackageDir, "MyEnum.class"));
        Files.write(TestClasses.emptyClass("SomeClass"), new File(mTestPackageDir, "SomeClass.class"));
        Files.write(TestClasses.emptyClass("SomeOtherClass"), new File(mTestPackageDir, "SomeOtherClass.class"));

        run(parseKeepRules("-keep @test.MyAnnotation class **"));

        assertMembersLeft("Main", "<init>:()V");
    }

    @Test
    public void annotations_keepRules_atInterface() throws Exception {
        Files.write(TestClasses.Annotations.main_annotatedClass(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.Annotations.myAnnotation(), new File(mTestPackageDir, "MyAnnotation.class"));
        Files.write(TestClasses.Annotations.nested(), new File(mTestPackageDir, "Nested.class"));
        Files.write(TestClasses.Annotations.myEnum(), new File(mTestPackageDir, "MyEnum.class"));
        Files.write(TestClasses.emptyClass("SomeClass"), new File(mTestPackageDir, "SomeClass.class"));
        Files.write(TestClasses.emptyClass("SomeOtherClass"), new File(mTestPackageDir, "SomeOtherClass.class"));

        run(parseKeepRules("-keep @interface test.MyAnnotation"));

        assertMembersLeft(
                "MyAnnotation",
                "nested:()[Ltest/Nested;",
                "f:()I",
                "klass:()Ljava/lang/Class;",
                "myEnum:()Ltest/MyEnum;");
    }

    @Test
    public void annotations_keepRules_interface() throws Exception {
        Files.write(TestClasses.Annotations.main_annotatedClass(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.Annotations.myAnnotation(), new File(mTestPackageDir, "MyAnnotation.class"));
        Files.write(TestClasses.Annotations.nested(), new File(mTestPackageDir, "Nested.class"));
        Files.write(TestClasses.Annotations.myEnum(), new File(mTestPackageDir, "MyEnum.class"));
        Files.write(TestClasses.emptyClass("SomeClass"), new File(mTestPackageDir, "SomeClass.class"));
        Files.write(TestClasses.emptyClass("SomeOtherClass"), new File(mTestPackageDir, "SomeOtherClass.class"));

        run(parseKeepRules("-keep interface test.MyAnnotation"));

        assertMembersLeft(
                "MyAnnotation",
                "nested:()[Ltest/Nested;",
                "f:()I",
                "klass:()Ljava/lang/Class;",
                "myEnum:()Ltest/MyEnum;");
    }

    @Test
    public void annotations_keepRules_atClass() throws Exception {
        Files.write(TestClasses.Annotations.main_annotatedClass(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.Annotations.myAnnotation(), new File(mTestPackageDir, "MyAnnotation.class"));
        Files.write(TestClasses.Annotations.nested(), new File(mTestPackageDir, "Nested.class"));
        Files.write(TestClasses.Annotations.myEnum(), new File(mTestPackageDir, "MyEnum.class"));
        Files.write(TestClasses.emptyClass("SomeClass"), new File(mTestPackageDir, "SomeClass.class"));
        Files.write(TestClasses.emptyClass("SomeOtherClass"), new File(mTestPackageDir, "SomeOtherClass.class"));

        run(parseKeepRules("-keep @class test.MyAnnotation"));

        assertMembersLeft(
                "MyAnnotation",
                "nested:()[Ltest/Nested;",
                "f:()I",
                "klass:()Ljava/lang/Class;",
                "myEnum:()Ltest/MyEnum;");
    }

    @Test
    public void annotations_keepRules_atEnum() throws Exception {
        Files.write(TestClasses.Annotations.main_annotatedClass(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.Annotations.myAnnotation(), new File(mTestPackageDir, "MyAnnotation.class"));
        Files.write(TestClasses.Annotations.nested(), new File(mTestPackageDir, "Nested.class"));
        Files.write(TestClasses.Annotations.myEnum(), new File(mTestPackageDir, "MyEnum.class"));
        Files.write(TestClasses.emptyClass("SomeClass"), new File(mTestPackageDir, "SomeClass.class"));
        Files.write(TestClasses.emptyClass("SomeOtherClass"), new File(mTestPackageDir, "SomeOtherClass.class"));

        run(parseKeepRules("-keep @enum test.MyAnnotation"));

        assertClassSkipped("MyAnnotation");
    }

    @Test
    public void annotations_keepRules_classRule() throws Exception {
        Files.write(TestClasses.Annotations.main_annotatedClass(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.Annotations.myAnnotation(), new File(mTestPackageDir, "MyAnnotation.class"));
        Files.write(TestClasses.Annotations.nested(), new File(mTestPackageDir, "Nested.class"));
        Files.write(TestClasses.Annotations.myEnum(), new File(mTestPackageDir, "MyEnum.class"));
        Files.write(TestClasses.emptyClass("SomeClass"), new File(mTestPackageDir, "SomeClass.class"));
        Files.write(TestClasses.emptyClass("SomeOtherClass"), new File(mTestPackageDir, "SomeOtherClass.class"));

        run(parseKeepRules("-keep class test.MyAnnotation"));

        assertMembersLeft(
                "MyAnnotation",
                "nested:()[Ltest/Nested;",
                "f:()I",
                "klass:()Ljava/lang/Class;",
                "myEnum:()Ltest/MyEnum;");
    }

    @Test
    public void annotations_keepRules_wrongAtClass() throws Exception {
        Files.write(TestClasses.Annotations.main_annotatedClass(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.Annotations.myAnnotation(), new File(mTestPackageDir, "MyAnnotation.class"));
        Files.write(TestClasses.Annotations.nested(), new File(mTestPackageDir, "Nested.class"));
        Files.write(TestClasses.Annotations.myEnum(), new File(mTestPackageDir, "MyEnum.class"));
        Files.write(TestClasses.emptyClass("SomeClass"), new File(mTestPackageDir, "SomeClass.class"));
        Files.write(TestClasses.emptyClass("SomeOtherClass"), new File(mTestPackageDir, "SomeOtherClass.class"));

        run(parseKeepRules("-keep @class test.SomeClass"));

        assertClassSkipped("SomeClass");
    }

    @Test
    public void annotations_keepRules_method() throws Exception {
        Files.write(
                TestClasses.Annotations.main_annotatedMethod(),
                new File(mTestPackageDir, "Main.class"));
        Files.write(
                TestClasses.Annotations.myAnnotation(),
                new File(mTestPackageDir, "MyAnnotation.class"));
        Files.write(TestClasses.Annotations.nested(), new File(mTestPackageDir, "Nested.class"));
        Files.write(TestClasses.Annotations.myEnum(), new File(mTestPackageDir, "MyEnum.class"));
        Files.write(
                TestClasses.emptyClass("SomeClass"),
                new File(mTestPackageDir, "SomeClass.class"));
        Files.write(
                TestClasses.emptyClass("SomeOtherClass"),
                new File(mTestPackageDir, "SomeOtherClass.class"));

        run(parseKeepRules("-keep class ** { @test/MyAnnotation *(...);}"));

        assertMembersLeft("Main", "<init>:()V", "main:()V");
    }

    @Test
    public void signatures_classSignature() throws Exception {
        // Given:
        Files.write(TestClasses.Signatures.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.Signatures.named(), new File(mTestPackageDir, "Named.class"));
        Files.write(TestClasses.Signatures.namedMap(), new File(mTestPackageDir, "NamedMap.class"));
        Files.write(TestClasses.Signatures.hasAge(), new File(mTestPackageDir, "HasAge.class"));

        // When:
        run("Main", "main:(Ltest/NamedMap;)V");

        // Then:
        assertMembersLeft(
                "Main",
                "main:(Ltest/NamedMap;)V");
        assertMembersLeft("NamedMap");
        assertMembersLeft("Named");
        assertClassSkipped("HasAge");
    }

    @Test
    public void signatures_methodSignature() throws Exception {
        // Given:
        Files.write(TestClasses.Signatures.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.Signatures.named(), new File(mTestPackageDir, "Named.class"));
        Files.write(TestClasses.Signatures.namedMap(), new File(mTestPackageDir, "NamedMap.class"));
        Files.write(TestClasses.Signatures.hasAge(), new File(mTestPackageDir, "HasAge.class"));

        // When:
        run("Main", "callMethod:(Ltest/NamedMap;)V");

        // Then:
        assertMembersLeft(
                "Main",
                "callMethod:(Ltest/NamedMap;)V");
        assertMembersLeft("NamedMap", "method:(Ljava/util/Collection;)V");
        assertMembersLeft("Named");
        assertMembersLeft("HasAge");
    }

    @Test
    public void superCalls_directSuperclass() throws Exception {
        // Given:
        Files.write(TestClasses.SuperCalls.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(TestClasses.SuperCalls.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(TestClasses.SuperCalls.ccc(), new File(mTestPackageDir, "Ccc.class"));

        // When:
        run("Ccc", "callBbbMethod:()V");

        // Then:
        assertMembersLeft("Aaa");
        assertMembersLeft("Bbb", "onlyInBbb:()V");
        assertMembersLeft("Ccc", "callBbbMethod:()V");
    }

    @Test
    public void superCalls_indirectSuperclass() throws Exception {
        // Given:
        Files.write(TestClasses.SuperCalls.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(TestClasses.SuperCalls.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(TestClasses.SuperCalls.ccc(), new File(mTestPackageDir, "Ccc.class"));

        // When:
        run("Ccc", "callAaaMethod:()V");

        // Then:
        assertMembersLeft("Aaa", "onlyInAaa:()V");
        assertMembersLeft("Bbb");
        assertMembersLeft("Ccc", "callAaaMethod:()V");
    }

    @Test
    public void superCalls_both() throws Exception {
        // Given:
        Files.write(TestClasses.SuperCalls.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(TestClasses.SuperCalls.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(TestClasses.SuperCalls.ccc(), new File(mTestPackageDir, "Ccc.class"));

        // When:
        run("Ccc", "callOverriddenMethod:()V");

        // Then:
        assertMembersLeft("Aaa");
        assertMembersLeft("Bbb", "overridden:()V");
        assertMembersLeft("Ccc", "callOverriddenMethod:()V");
    }

    @Test
    public void innerClasses_useOuter() throws Exception {
        // Given:
        Files.write(InnerClasses.main_useOuterClass(), new File(mTestPackageDir, "Main.class"));
        Files.write(InnerClasses.outer(), new File(mTestPackageDir, "Outer.class"));
        Files.write(InnerClasses.inner(), new File(mTestPackageDir, "Outer$Inner.class"));
        Files.write(InnerClasses.staticInner(), new File(mTestPackageDir, "Outer$StaticInner.class"));
        Files.write(InnerClasses.anonymous(), new File(mTestPackageDir, "Outer$1.class"));

        // When:
        run("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "main:()V");
        assertMembersLeft("Outer", "outerMethod:()V", "<init>:()V");
        assertClassSkipped("Outer$Inner");
        assertClassSkipped("Outer$StaticInner");
        assertClassSkipped("Outer$1");

        assertNoInnerClasses("Outer");
        assertNoInnerClasses("Main");
    }

    @Test
    public void innerClasses_useOuter_anonymous() throws Exception {
        // Given:
        Files.write(
                InnerClasses.main_useOuterClass_makeAnonymousClass(),
                new File(mTestPackageDir, "Main.class"));
        Files.write(InnerClasses.outer(), new File(mTestPackageDir, "Outer.class"));
        Files.write(InnerClasses.inner(), new File(mTestPackageDir, "Outer$Inner.class"));
        Files.write(InnerClasses.staticInner(), new File(mTestPackageDir, "Outer$StaticInner.class"));
        Files.write(InnerClasses.anonymous(), new File(mTestPackageDir, "Outer$1.class"));

        // When:
        run("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "main:()V");
        assertMembersLeft("Outer", "makeRunnable:()V", "<init>:()V");
        assertMembersLeft("Outer$1",
                "run:()V",
                "<init>:(Ltest/Outer;)V",
                "this$0:Ltest/Outer;");
        assertClassSkipped("Outer$Inner");
        assertClassSkipped("Outer$StaticInner");

        assertSingleInnerClassesEntry("Outer", null, "test/Outer$1");
        assertNoInnerClasses("Main");
    }

    @Test
    public void innerClasses_useInner() throws Exception {
        // Given:
        Files.write(InnerClasses.main_useInnerClass(), new File(mTestPackageDir, "Main.class"));
        Files.write(InnerClasses.outer(), new File(mTestPackageDir, "Outer.class"));
        Files.write(InnerClasses.inner(), new File(mTestPackageDir, "Outer$Inner.class"));
        Files.write(InnerClasses.staticInner(), new File(mTestPackageDir, "Outer$StaticInner.class"));
        Files.write(InnerClasses.anonymous(), new File(mTestPackageDir, "Outer$1.class"));

        // When:
        run("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "main:()V");
        assertMembersLeft("Outer", "<init>:()V");
        assertMembersLeft(
                "Outer$Inner",
                "innerMethod:()V",
                "<init>:(Ltest/Outer;)V",
                "this$0:Ltest/Outer;");
        assertClassSkipped("Outer$StaticInner");
        assertClassSkipped("Outer$1");

        assertSingleInnerClassesEntry("Outer", "test/Outer", "test/Outer$Inner");
        assertSingleInnerClassesEntry("Outer$Inner", "test/Outer", "test/Outer$Inner");
        assertSingleInnerClassesEntry("Main", "test/Outer", "test/Outer$Inner");
    }

    @Test
    public void innerClasses_useStaticInner() throws Exception {
        // Given:
        Files.write(InnerClasses.main_useStaticInnerClass(), new File(mTestPackageDir, "Main.class"));
        Files.write(InnerClasses.outer(), new File(mTestPackageDir, "Outer.class"));
        Files.write(InnerClasses.inner(), new File(mTestPackageDir, "Outer$Inner.class"));
        Files.write(InnerClasses.staticInner(), new File(mTestPackageDir, "Outer$StaticInner.class"));
        Files.write(InnerClasses.anonymous(), new File(mTestPackageDir, "Outer$1.class"));

        // When:
        run("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "main:()V");
        assertMembersLeft("Outer");
        assertClassSkipped("Outer$Inner");
        assertClassSkipped("Outer$1");
        assertMembersLeft("Outer$StaticInner", "<init>:()V", "staticInnerMethod:()V");

        assertSingleInnerClassesEntry("Outer", "test/Outer", "test/Outer$StaticInner");
        assertSingleInnerClassesEntry("Main", "test/Outer", "test/Outer$StaticInner");
        assertSingleInnerClassesEntry("Outer$StaticInner", "test/Outer", "test/Outer$StaticInner");
    }

    @Test
    public void innerClasses_notUsed() throws Exception {
        // Given:
        Files.write(InnerClasses.main_empty(), new File(mTestPackageDir, "Main.class"));
        Files.write(InnerClasses.outer(), new File(mTestPackageDir, "Outer.class"));
        Files.write(InnerClasses.inner(), new File(mTestPackageDir, "Outer$Inner.class"));
        Files.write(InnerClasses.staticInner(), new File(mTestPackageDir, "Outer$StaticInner.class"));
        Files.write(InnerClasses.anonymous(), new File(mTestPackageDir, "Outer$1.class"));

        // When:
        run("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "main:()V");
        assertClassSkipped("Outer");
        assertClassSkipped("Outer$Inner");
        assertClassSkipped("Outer$StaticInner");
        assertClassSkipped("Outer$1");

        assertNoInnerClasses("Main");
    }

    @Test
    public void staticMethods() throws Exception {
        // Given:
        Files.write(TestClasses.StaticMembers.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.StaticMembers.utils(), new File(mTestPackageDir, "Utils.class"));

        // When:
        run("Main", "callStaticMethod:()Ljava/lang/Object;");

        // Then:
        assertMembersLeft("Main", "callStaticMethod:()Ljava/lang/Object;");
        assertMembersLeft("Utils", "staticMethod:()Ljava/lang/Object;");
    }

    @Test
    public void staticFields_uninitialized() throws Exception {
        // Given:
        Files.write(TestClasses.StaticMembers.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.StaticMembers.utils(), new File(mTestPackageDir, "Utils.class"));

        // When:
        run("Main", "getStaticField:()Ljava/lang/Object;");

        // Then:
        assertMembersLeft("Main", "getStaticField:()Ljava/lang/Object;");
        assertMembersLeft("Utils", "staticField:Ljava/lang/Object;");
    }

    @Test
    public void reflection_instanceOf() throws Exception {
        // Given:
        Files.write(Reflection.main_instanceOf(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.emptyClass("Foo"), new File(mTestPackageDir, "Foo.class"));

        // When:
        run("Main", "main:(Ljava/lang/Object;)Z");

        // Then:
        assertMembersLeft("Main", "main:(Ljava/lang/Object;)Z");
        assertMembersLeft("Foo");
    }

    @Test
    public void reflection_classForName() throws Exception {
        // Given:
        Files.write(
                Reflection.main_classForName(),
                new File(mTestPackageDir, "Main.class"));
        Files.write(
                Reflection.classWithFields(),
                new File(mTestPackageDir, "ClassWithFields.class"));

        // When:
        run("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "main:()V");
        assertMembersLeft("ClassWithFields");
    }

    @Test
    public void reflection_classForName_dynamic() throws Exception {
        // Given:
        Files.write(
                Reflection.main_classForName_dynamic(),
                new File(mTestPackageDir, "Main.class"));
        Files.write(
                Reflection.classWithFields(),
                new File(mTestPackageDir, "ClassWithFields.class"));

        // When:
        run("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "main:()V");
        assertClassSkipped("ClassWithFields");
    }

    @Test
    public void reflection_atomicIntegerFieldUpdater() throws Exception {
        // Given:
        Files.write(
                Reflection.main_atomicIntegerFieldUpdater(),
                new File(mTestPackageDir, "Main.class"));
        Files.write(
                Reflection.classWithFields(),
                new File(mTestPackageDir, "ClassWithFields.class"));

        // When:
        run("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "main:()V");
        assertMembersLeft("ClassWithFields", "intField:I");
    }

    @Test
    public void reflection_atomicLongFieldUpdater() throws Exception {
        // Given:
        Files.write(
                Reflection.main_atomicLongFieldUpdater(),
                new File(mTestPackageDir, "Main.class"));
        Files.write(
                Reflection.classWithFields(),
                new File(mTestPackageDir, "ClassWithFields.class"));

        // When:
        run("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "main:()V");
        assertMembersLeft("ClassWithFields", "longField:J");
    }

    @Test
    public void reflection_atomicReferenceFieldUpdater() throws Exception {
        // Given:
        Files.write(
                Reflection.main_atomicReferenceFieldUpdater(),
                new File(mTestPackageDir, "Main.class"));
        Files.write(
                Reflection.classWithFields(),
                new File(mTestPackageDir, "ClassWithFields.class"));

        // When:
        run("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "main:()V");
        assertMembersLeft("ClassWithFields", "stringField:Ljava/lang/String;");
    }


    @Test
    public void reflection_classLiteral() throws Exception {
        // Given:
        Files.write(
                Reflection.main_classLiteral(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.emptyClass("Foo"), new File(mTestPackageDir, "Foo.class"));

        // When:
        run("Main", "main:()Ljava/lang/Object;");

        // Then:
        assertMembersLeft("Main", "main:()Ljava/lang/Object;");
        assertMembersLeft("Foo");
    }

    @Test
    public void testTryCatch() throws Exception {
        // Given:
        Files.write(TestClasses.TryCatch.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.TryCatch.customException(), new File(mTestPackageDir, "CustomException.class"));

        // When:
        run("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "main:()V", "helper:()V");
        assertMembersLeft("CustomException");
    }

    @Test
    public void testTryFinally() throws Exception {
        // Given:
        Files.write(TestClasses.TryCatch.main_tryFinally(), new File(mTestPackageDir, "Main.class"));

        // When:
        run("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "main:()V", "helper:()V");
    }

    @Test
    public void abstractClasses_callToInterfaceMethodInAbstractClass() throws Exception {
        // Given:
        Files.write(TestClasses.AbstractClasses.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(TestClasses.AbstractClasses.abstractImpl(), new File(mTestPackageDir, "AbstractImpl.class"));
        Files.write(
                TestClasses.AbstractClasses.realImpl(), new File(mTestPackageDir, "RealImpl.class"));

        // When:
        run("RealImpl", "main:()V");

        // Then:
        assertMembersLeft("MyInterface", "m:()V");
        assertMembersLeft("RealImpl", "main:()V", "m:()V");
        assertMembersLeft("AbstractImpl", "helper:()V");
    }

    @Test
    public void primitives() throws Exception {
        // Given:
        Files.write(TestClasses.Primitives.main(), new File(mTestPackageDir, "Main.class"));

        // When:
        run("Main", "ldc:()Ljava/lang/Object;", "checkcast:(Ljava/lang/Object;)[I");

        // Then:
        assertMembersLeft("Main", "ldc:()Ljava/lang/Object;", "checkcast:(Ljava/lang/Object;)[I");
    }

    @Test
    public void invalidReferences_sunMiscUnsafe() throws Exception {
        // Given:
        Files.write(TestClasses.InvalidReferences.main_sunMiscUnsafe(), new File(mTestPackageDir, "Main.class"));

        // When:
        run("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "main:()V");
        mExpectedWarnings = 1;
    }

    @Test
    public void invalidReferences_Instrumentation() throws Exception {
        // Given:
        Files.write(TestClasses.InvalidReferences.main_javaInstrumentation(), new File(mTestPackageDir, "Main.class"));

        // When:
        run("Main", "main:()V");

        // Make sure we kept the method, even though we encountered unrecognized classes.
        assertMembersLeft("Main", "main:()V", "transform:(Ljava/lang/ClassLoader;Ljava/lang/String;Ljava/lang/Class;Ljava/security/ProtectionDomain;[B)[B");
        assertImplements("Main", "java/lang/instrument/ClassFileTransformer");
        mExpectedWarnings = 1;
    }

    private void run(String className, String... methods) throws IOException {
        run(new TestKeepRules(className, methods));
    }

    private void run(KeepRules keepRules) throws IOException {
        mShrinker.run(
                mInputs,
                Collections.<TransformInput>emptyList(),
                mOutput,
                ImmutableMap.of(
                        AbstractShrinker.CounterSet.SHRINK, keepRules),
                false);
    }
}