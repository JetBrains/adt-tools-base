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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformInput;
import com.android.build.gradle.shrinker.AbstractShrinker.CounterSet;
import com.android.build.gradle.shrinker.IncrementalShrinker.IncrementalRunImpossibleException;
import com.android.build.gradle.shrinker.TestClasses.Annotations;
import com.android.build.gradle.shrinker.TestClasses.Interfaces;
import com.android.build.gradle.shrinker.TestClassesForIncremental.Cycle;
import com.android.build.gradle.shrinker.TestClassesForIncremental.Simple;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * Tests for {@link IncrementalShrinker}.
 */
public class IncrementalShrinkerTest extends AbstractShrinkerTest {

    private FullRunShrinker<String> mFullRunShrinker;

    @Rule
    public ExpectedException mException = ExpectedException.none();

    @Before
    public void createShrinker() throws Exception {
        mFullRunShrinker = new FullRunShrinker<String>(
                WaitableExecutor.<Void>useGlobalSharedThreadPool(),
                JavaSerializationShrinkerGraph.empty(mIncrementalDir),
                getPlatformJars(),
                mShrinkerLogger);
    }

    @Test
    public void simple_testIncrementalUpdate() throws Exception {
        // Given:
        Files.write(Simple.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(Simple.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(Simple.main1(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.emptyClass("NotUsed"), new File(mTestPackageDir, "NotUsed.class"));

        fullRun("Main", "main:()V");

        assertTrue(new File(mIncrementalDir, "shrinker.bin").exists());
        assertMembersLeft("Main", "main:()V");
        assertMembersLeft("Aaa", "<init>:()V", "m1:()V");
        assertMembersLeft("Bbb", "<init>:()V");
        assertClassSkipped("NotUsed");

        long timestampBbb = getOutputClassFile("Bbb").lastModified();
        long timestampMain = getOutputClassFile("Main").lastModified();

        // Give file timestamps time to tick.
        Thread.sleep(1000);

        Files.write(Simple.main2(), new File(mTestPackageDir, "Main.class"));
        incrementalRun(ImmutableMap.of("Main", Status.CHANGED));

        // Then:
        assertMembersLeft("Main", "main:()V");
        assertMembersLeft("Aaa", "<init>:()V", "m2:()V");
        assertMembersLeft("Bbb", "<init>:()V");
        assertClassSkipped("NotUsed");

        assertTrue(timestampMain < getOutputClassFile("Main").lastModified());
        assertEquals(timestampBbb, getOutputClassFile("Bbb").lastModified());
    }

    @Test
    public void simple_testIncrementalUpdate_methodAdded() throws Exception {
        // Given:
        Files.write(Simple.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(Simple.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(Simple.main1(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.emptyClass("NotUsed"), new File(mTestPackageDir, "NotUsed.class"));

        fullRun("Main", "main:()V");

        Files.write(Simple.main_extraMethod(), new File(mTestPackageDir, "Main.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage("test/Main.extraMain:()V added");
        incrementalRun(ImmutableMap.of("Main", Status.CHANGED));
    }

    @Test
    public void simple_testIncrementalUpdate_methodRemoved() throws Exception {
        // Given:
        Files.write(Simple.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(Simple.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(Simple.main_extraMethod(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.emptyClass("NotUsed"), new File(mTestPackageDir, "NotUsed.class"));

        fullRun("Main", "main:()V");

        Files.write(Simple.main1(), new File(mTestPackageDir, "Main.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage("test/Main.extraMain:()V removed");
        incrementalRun(ImmutableMap.of("Main", Status.CHANGED));
    }

    @Test
    public void simple_testIncrementalUpdate_fieldAdded() throws Exception {
        // Given:
        Files.write(Simple.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(Simple.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(Simple.main1(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.emptyClass("NotUsed"), new File(mTestPackageDir, "NotUsed.class"));

        fullRun("Main", "main:()V");

        Files.write(Simple.main_extraField(), new File(mTestPackageDir, "Main.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage("test/Main.sString:Ljava/lang/String; added");
        incrementalRun(ImmutableMap.of("Main", Status.CHANGED));
    }

    @Test
    public void simple_testIncrementalUpdate_fieldRemoved() throws Exception {
        // Given:
        Files.write(Simple.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(Simple.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(Simple.main_extraField(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.emptyClass("NotUsed"), new File(mTestPackageDir, "NotUsed.class"));

        fullRun("Main", "main:()V");

        Files.write(Simple.main1(), new File(mTestPackageDir, "Main.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage("test/Main.sString:Ljava/lang/String; removed");
        incrementalRun(ImmutableMap.of("Main", Status.CHANGED));
    }

    @Test
    public void simple_testIncrementalUpdate_classAdded() throws Exception {
        // Given:
        Files.write(Simple.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(Simple.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(Simple.main1(), new File(mTestPackageDir, "Main.class"));

        fullRun("Main", "main:()V");

        Files.write(TestClasses.emptyClass("NotUsed"), new File(mTestPackageDir, "NotUsed.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage(FileUtils.toSystemDependentPath("test/NotUsed.class") + " added");
        incrementalRun(ImmutableMap.of(
                "Main", Status.CHANGED,
                "NotUsed", Status.ADDED));
    }

    @Test
    public void simple_testIncrementalUpdate_classRemoved() throws Exception {
        // Given:
        File notUsedClass = new File(mTestPackageDir, "NotUsed.class");

        Files.write(Simple.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(Simple.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(Simple.main1(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.emptyClass("NotUsed"), notUsedClass);

        fullRun("Main", "main:()V");

        FileUtils.delete(notUsedClass);

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage(
                FileUtils.toSystemDependentPath("test/NotUsed.class") + " removed");
        incrementalRun(ImmutableMap.of(
                "Main", Status.CHANGED,
                "NotUsed", Status.REMOVED));
    }

    @Test
    public void simple_testIncrementalUpdate_superclassChanged() throws Exception {
        // Given:
        Files.write(Simple.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(Simple.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(Simple.main1(), new File(mTestPackageDir, "Main.class"));

        fullRun("Main", "main:()V");

        Files.write(Simple.bbb_extendsAaa(), new File(mTestPackageDir, "Bbb.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage("test/Bbb superclass changed");
        incrementalRun(ImmutableMap.of(
                "Bbb", Status.CHANGED));
    }

    @Test
    public void simple_testIncrementalUpdate_interfacesChanged() throws Exception {
        // Given:
        Files.write(Simple.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(Simple.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(Simple.main1(), new File(mTestPackageDir, "Main.class"));

        fullRun("Main", "main:()V");

        Files.write(Simple.bbb_serializable(), new File(mTestPackageDir, "Bbb.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage("test/Bbb interfaces changed");
        incrementalRun(ImmutableMap.of(
                "Bbb", Status.CHANGED));
    }

    @Test
    public void simple_testIncrementalUpdate_methodModifiersChanged() throws Exception {
        // Given:
        Files.write(Simple.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(Simple.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(Simple.main1(), new File(mTestPackageDir, "Main.class"));

        fullRun("Main", "main:()V");

        Files.write(Simple.bbb_packagePrivateConstructor(), new File(mTestPackageDir, "Bbb.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage("test/Bbb.<init>:()V modifiers changed");
        incrementalRun(ImmutableMap.of(
                "Bbb", Status.CHANGED));
    }

    @Test
    public void simple_testIncrementalUpdate_fieldModifiersChanged() throws Exception {
        // Given:
        Files.write(Simple.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(Simple.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(Simple.main_extraField(), new File(mTestPackageDir, "Main.class"));

        fullRun("Main", "main:()V");

        Files.write(Simple.main_extraField_private(), new File(mTestPackageDir, "Main.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage("test/Main.sString:Ljava/lang/String; modifiers changed");
        incrementalRun(ImmutableMap.of(
                "Main", Status.CHANGED));
    }

    @Test
    public void simple_testIncrementalUpdate_classModifiersChanged() throws Exception {
        // Given:
        Files.write(Simple.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(Simple.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(Simple.main1(), new File(mTestPackageDir, "Main.class"));

        fullRun("Main", "main:()V");

        Files.write(Simple.bbb_packagePrivate(), new File(mTestPackageDir, "Bbb.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage("test/Bbb modifiers changed");
        incrementalRun(ImmutableMap.of(
                "Bbb", Status.CHANGED));
    }

    @Test
    public void simple_testIncrementalUpdate_classAnnotationAdded() throws Exception {
        // Given:
        Files.write(Annotations.main_annotatedClass(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.Annotations.myAnnotation(), new File(mTestPackageDir, "MyAnnotation.class"));
        Files.write(TestClasses.Annotations.nested(), new File(mTestPackageDir, "Nested.class"));
        Files.write(TestClasses.Annotations.myEnum(), new File(mTestPackageDir, "MyEnum.class"));
        Files.write(TestClasses.emptyClass("SomeClass"), new File(mTestPackageDir, "SomeClass.class"));
        Files.write(TestClasses.emptyClass("SomeOtherClass"), new File(mTestPackageDir, "SomeOtherClass.class"));

        fullRun("Main", "main:()V");

        Files.write(Annotations.main_noAnnotations(), new File(mTestPackageDir, "Main.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage("Annotation test/MyAnnotation on test/Main removed");
        incrementalRun(ImmutableMap.of("Main", Status.CHANGED));
    }

    @Test
    public void simple_testIncrementalUpdate_classAnnotationRemoved() throws Exception {
        // Given:
        Files.write(Annotations.main_noAnnotations(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.Annotations.myAnnotation(), new File(mTestPackageDir, "MyAnnotation.class"));
        Files.write(TestClasses.Annotations.nested(), new File(mTestPackageDir, "Nested.class"));
        Files.write(TestClasses.Annotations.myEnum(), new File(mTestPackageDir, "MyEnum.class"));
        Files.write(TestClasses.emptyClass("SomeClass"), new File(mTestPackageDir, "SomeClass.class"));
        Files.write(TestClasses.emptyClass("SomeOtherClass"), new File(mTestPackageDir, "SomeOtherClass.class"));

        fullRun("Main", "main:()V");

        Files.write(Annotations.main_annotatedClass(), new File(mTestPackageDir, "Main.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage("Annotation test/MyAnnotation on test/Main added");
        incrementalRun(ImmutableMap.of("Main", Status.CHANGED));
    }

    @Test
    public void simple_testIncrementalUpdate_methodAnnotationAdded() throws Exception {
        // Given:
        Files.write(Annotations.main_noAnnotations(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.Annotations.myAnnotation(), new File(mTestPackageDir, "MyAnnotation.class"));
        Files.write(TestClasses.Annotations.nested(), new File(mTestPackageDir, "Nested.class"));
        Files.write(TestClasses.Annotations.myEnum(), new File(mTestPackageDir, "MyEnum.class"));
        Files.write(TestClasses.emptyClass("SomeClass"), new File(mTestPackageDir, "SomeClass.class"));
        Files.write(TestClasses.emptyClass("SomeOtherClass"), new File(mTestPackageDir, "SomeOtherClass.class"));

        fullRun("Main", "main:()V");

        Files.write(Annotations.main_annotatedMethod(), new File(mTestPackageDir, "Main.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage("Annotation test/MyAnnotation on test/Main.main added");
        incrementalRun(ImmutableMap.of("Main", Status.CHANGED));
    }

    @Test
    public void simple_testIncrementalUpdate_methodAnnotationRemoved() throws Exception {
        // Given:
        Files.write(Annotations.main_annotatedMethod(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.Annotations.myAnnotation(), new File(mTestPackageDir, "MyAnnotation.class"));
        Files.write(TestClasses.Annotations.nested(), new File(mTestPackageDir, "Nested.class"));
        Files.write(TestClasses.Annotations.myEnum(), new File(mTestPackageDir, "MyEnum.class"));
        Files.write(TestClasses.emptyClass("SomeClass"), new File(mTestPackageDir, "SomeClass.class"));
        Files.write(TestClasses.emptyClass("SomeOtherClass"), new File(mTestPackageDir, "SomeOtherClass.class"));

        fullRun("Main", "main:()V");

        Files.write(Annotations.main_noAnnotations(), new File(mTestPackageDir, "Main.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage("Annotation test/MyAnnotation on test/Main.main removed");
        incrementalRun(ImmutableMap.of("Main", Status.CHANGED));
    }

    @Test
    public void cycle() throws Exception {
        // Given:
        Files.write(Cycle.main1(), new File(mTestPackageDir, "Main.class"));
        Files.write(Cycle.cycleOne(), new File(mTestPackageDir, "CycleOne.class"));
        Files.write(Cycle.cycleTwo(), new File(mTestPackageDir, "CycleTwo.class"));
        Files.write(TestClasses.emptyClass("NotUsed"), new File(mTestPackageDir, "NotUsed.class"));

        fullRun("Main", "main:()V");

        assertTrue(new File(mIncrementalDir, "shrinker.bin").exists());
        assertMembersLeft("Main", "main:()V");
        assertMembersLeft("CycleOne", "<init>:()V");
        assertMembersLeft("CycleTwo", "<init>:()V");
        assertClassSkipped("NotUsed");

        byte[] mainBytes = Files.toByteArray(getOutputClassFile("Main"));

        Files.write(Cycle.main2(), new File(mTestPackageDir, "Main.class"));
        incrementalRun(ImmutableMap.of("Main", Status.CHANGED));

        // Then:
        assertMembersLeft("Main", "main:()V");
        assertClassSkipped("CycleOne");
        assertClassSkipped("CycleTwo");
        assertClassSkipped("NotUsed");

        assertNotEquals(mainBytes, Files.toByteArray(getOutputClassFile("Main")));

        Files.write(Cycle.main1(), new File(mTestPackageDir, "Main.class"));
        incrementalRun(ImmutableMap.of("Main", Status.CHANGED));
        assertMembersLeft("Main", "main:()V");
        assertMembersLeft("CycleOne", "<init>:()V");
        assertMembersLeft("CycleTwo", "<init>:()V");
        assertClassSkipped("NotUsed");
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
        fullRun(
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

        incrementalRun(ImmutableMap.of("ImplementationFromSuperclass", Status.CHANGED));
    }

    private void fullRun(String className, String... methods) throws IOException {
        mFullRunShrinker.run(
                mInputs,
                Collections.<TransformInput>emptyList(),
                mOutput,
                ImmutableMap.<CounterSet, KeepRules>of(
                        CounterSet.SHRINK, new TestKeepRules(className, methods)),
                true);
    }

    private void incrementalRun(Map<String, Status> changes) throws Exception {
        IncrementalShrinker<String> incrementalShrinker = new IncrementalShrinker<String>(
                WaitableExecutor.<Void>useGlobalSharedThreadPool(),
                JavaSerializationShrinkerGraph.readFromDir(
                        mIncrementalDir,
                        this.getClass().getClassLoader()),
                mShrinkerLogger);

        Map<File, Status> files = Maps.newHashMap();
        for (Map.Entry<String, Status> entry : changes.entrySet()) {
            files.put(
                    new File(mTestPackageDir, entry.getKey() + ".class"),
                    entry.getValue());
        }

        when(mDirectoryInput.getChangedFiles()).thenReturn(files);

        incrementalShrinker.incrementalRun(mInputs, mOutput);
    }
}
