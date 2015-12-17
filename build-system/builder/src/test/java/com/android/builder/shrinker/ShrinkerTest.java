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

package com.android.builder.shrinker;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.builder.shrinker.TestClasses.AbstractClasses;
import com.android.builder.shrinker.TestClasses.Annotations;
import com.android.builder.shrinker.TestClasses.Fields;
import com.android.builder.shrinker.TestClasses.InnerClasses;
import com.android.builder.shrinker.TestClasses.Interfaces;
import com.android.builder.shrinker.TestClasses.MultipleOverriddenMethods;
import com.android.builder.shrinker.TestClasses.Reflection;
import com.android.builder.shrinker.TestClasses.SdkTypes;
import com.android.builder.shrinker.TestClasses.Signatures;
import com.android.builder.shrinker.TestClasses.SimpleScenario;
import com.android.builder.shrinker.TestClasses.StaticMembers;
import com.android.builder.shrinker.TestClasses.SuperCalls;
import com.android.builder.shrinker.TestClasses.TryCatch;
import com.android.builder.shrinker.TestClasses.VirtualCalls;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Tests for {@link Shrinker}.
 */
public class ShrinkerTest {
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    private File mTestPackageDir;

    private File mOutDir;

    private Collection<TransformInput> mInputs;
    private TransformOutputProvider mOutput;

    private Shrinker<String> mShrinker;

    @Before
    public void setUp() throws Exception {
        mTestPackageDir = tmpDir.newFolder("app-classes", "test");
        File classDir = new File(tmpDir.getRoot(), "app-classes");
        mOutDir = tmpDir.newFolder("out");
        File incrementalDir = tmpDir.newFolder("incremental");

        DirectoryInput directoryInput = mock(DirectoryInput.class);
        when(directoryInput.getFile()).thenReturn(classDir);
        TransformInput transformInput = mock(TransformInput.class);
        when(transformInput.getDirectoryInputs()).thenReturn(ImmutableList.of(directoryInput));
        mOutput = mock(TransformOutputProvider.class);
        // we probably want a better mock that than so that we can return different dir depending
        // on inputs.
        when(mOutput.getContentLocation(
                Mockito.anyString(), Mockito.anySet(), Mockito.anySet(), Mockito.any(Format.class)))
                .thenReturn(mOutDir);

        mInputs = ImmutableList.of(transformInput);

        mShrinker = new Shrinker<String>(
                new WaitableExecutor<Void>(),
                new JavaSerializationShrinkerGraph(incrementalDir),
                getAndroidJar());
    }

    @Test
    public void simple_oneClass() throws Exception {
        // Given:
        Files.write(SimpleScenario.aaa(), new File(mTestPackageDir, "Aaa.class"));

        // When:
        run("Aaa", "aaa:()V");

        // Then:
        assertMembersLeft("Aaa", "aaa:()V", "bbb:()V");
    }

    @Test
    public void simple_threeClasses() throws Exception {
        // Given:
        Files.write(SimpleScenario.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(SimpleScenario.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(SimpleScenario.ccc(), new File(mTestPackageDir, "Ccc.class"));

        // When:
        run("Bbb", "bbb:(Ltest/Aaa;)V");

        // Then:
        assertMembersLeft("Aaa", "aaa:()V", "bbb:()V");
        assertMembersLeft("Bbb", "bbb:(Ltest/Aaa;)V");
        assertClassSkipped("Ccc");
    }

    @Ignore
    @Test
    public void simple_testIncrementalUpdate() throws Exception {
        // Given:
        File bbbFile = new File(mTestPackageDir, "Bbb.class");
        Files.write(SimpleScenario.bbb(), bbbFile);
        Files.write(SimpleScenario.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(SimpleScenario.ccc(), new File(mTestPackageDir, "Ccc.class"));

        run("Bbb", "bbb:(Ltest/Aaa;)V");

        Files.write(SimpleScenario.bbb2(), bbbFile);

        // When:
        mShrinker.handleFileChanges(
                mInputs,
                mOutput,
                ImmutableMap.<Shrinker.ShrinkType, KeepRules>of(
                        Shrinker.ShrinkType.SHRINK, new TestKeepRules("Bbb", "bbb")));

        // Then:
        assertMembersLeft("Aaa", "ccc:()V");
        assertMembersLeft("Bbb", "bbb:(Ltest/Aaa;)V");
        assertClassSkipped("Ccc");
    }

    @Test
    public void virtualCalls_keepEntryPointsSuperclass() throws Exception {
        // Given:
        Files.write(VirtualCalls.abstractClass(), new File(mTestPackageDir, "AbstractClass.class"));
        Files.write(VirtualCalls.impl(1), new File(mTestPackageDir, "Impl1.class"));

        // When:
        run("Impl1", "abstractMethod:()V");

        // Then:
        assertMembersLeft("Impl1", "abstractMethod:()V");
        assertMembersLeft("AbstractClass");
    }

    @Test
    public void virtualCalls_abstractType() throws Exception {
        // Given:
        Files.write(VirtualCalls.main_abstractType(), new File(mTestPackageDir, "Main.class"));
        Files.write(VirtualCalls.abstractClass(), new File(mTestPackageDir, "AbstractClass.class"));
        Files.write(VirtualCalls.impl(1), new File(mTestPackageDir, "Impl1.class"));
        Files.write(VirtualCalls.impl(2), new File(mTestPackageDir, "Impl2.class"));
        Files.write(VirtualCalls.impl(3), new File(mTestPackageDir, "Impl3.class"));

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
        Files.write(VirtualCalls.main_concreteType(), new File(mTestPackageDir, "Main.class"));
        Files.write(VirtualCalls.abstractClass(), new File(mTestPackageDir, "AbstractClass.class"));
        Files.write(VirtualCalls.impl(1), new File(mTestPackageDir, "Impl1.class"));
        Files.write(VirtualCalls.impl(2), new File(mTestPackageDir, "Impl2.class"));
        Files.write(VirtualCalls.impl(3), new File(mTestPackageDir, "Impl3.class"));

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
        Files.write(VirtualCalls.main_parentChild(), new File(mTestPackageDir, "Main.class"));
        Files.write(VirtualCalls.parent(), new File(mTestPackageDir, "Parent.class"));
        Files.write(VirtualCalls.child(), new File(mTestPackageDir, "Child.class"));

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
        Files.write(SdkTypes.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(SdkTypes.myException(), new File(mTestPackageDir, "MyException.class"));

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
    }

    @Test
    public void interfaces_sdkInterface_classUsed_concreteType() throws Exception {
        // Given:
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
    }

    @Test
    public void interfaces_implementationFromSuperclass() throws Exception {
        // Given:
        Files.write(Interfaces.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Interfaces.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(Interfaces.myCharSequence(), new File(mTestPackageDir, "MyCharSequence.class"));
        Files.write(Interfaces.myImpl(), new File(mTestPackageDir, "MyImpl.class"));
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
    }

    @Test
    public void interfaces_sdkInterface_classNotUsed() throws Exception {
        // Given:
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
    public void interfaces_appInterface_abstractType() throws Exception {
        // Given:
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
    }

    @Test
    public void interfaces_appInterface_concreteType() throws Exception {
        // Given:
        Files.write(Interfaces.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Interfaces.myCharSequence(), new File(mTestPackageDir, "MyCharSequence.class"));
        Files.write(Interfaces.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(Interfaces.myImpl(), new File(mTestPackageDir, "MyImpl.class"));
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
    }

    @Test
    public void interfaces_appInterface_interfaceNotUsed() throws Exception {
        // Given:
        Files.write(Interfaces.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Interfaces.myCharSequence(), new File(mTestPackageDir, "MyCharSequence.class"));
        Files.write(Interfaces.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(Interfaces.myImpl(), new File(mTestPackageDir, "MyImpl.class"));
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
    }

    @Test
    public void fields() throws Exception {
        // Given:
        Files.write(Fields.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Fields.myFields(), new File(mTestPackageDir, "MyFields.class"));
        Files.write(Fields.myFieldsSubclass(), new File(mTestPackageDir, "MyFieldsSubclass.class"));
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
        Files.write(Fields.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Fields.myFields(), new File(mTestPackageDir, "MyFields.class"));
        Files.write(Fields.myFieldsSubclass(), new File(mTestPackageDir, "MyFieldsSubclass.class"));
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
        Files.write(MultipleOverriddenMethods.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(MultipleOverriddenMethods.interfaceOne(), new File(mTestPackageDir, "InterfaceOne.class"));
        Files.write(MultipleOverriddenMethods.interfaceTwo(), new File(mTestPackageDir, "InterfaceTwo.class"));
        Files.write(MultipleOverriddenMethods.implementation(), new File(mTestPackageDir, "Implementation.class"));

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
        Files.write(MultipleOverriddenMethods.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(MultipleOverriddenMethods.interfaceOne(), new File(mTestPackageDir, "InterfaceOne.class"));
        Files.write(MultipleOverriddenMethods.interfaceTwo(), new File(mTestPackageDir, "InterfaceTwo.class"));
        Files.write(MultipleOverriddenMethods.implementation(), new File(mTestPackageDir, "Implementation.class"));

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
        Files.write(MultipleOverriddenMethods.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(MultipleOverriddenMethods.interfaceOne(), new File(mTestPackageDir, "InterfaceOne.class"));
        Files.write(MultipleOverriddenMethods.interfaceTwo(), new File(mTestPackageDir, "InterfaceTwo.class"));
        Files.write(MultipleOverriddenMethods.implementation(), new File(mTestPackageDir, "Implementation.class"));

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
        Files.write(MultipleOverriddenMethods.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(MultipleOverriddenMethods.interfaceOne(), new File(mTestPackageDir, "InterfaceOne.class"));
        Files.write(MultipleOverriddenMethods.interfaceTwo(), new File(mTestPackageDir, "InterfaceTwo.class"));
        Files.write(MultipleOverriddenMethods.implementation(), new File(mTestPackageDir, "Implementation.class"));

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
        Files.write(MultipleOverriddenMethods.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(MultipleOverriddenMethods.interfaceOne(), new File(mTestPackageDir, "InterfaceOne.class"));
        Files.write(MultipleOverriddenMethods.interfaceTwo(), new File(mTestPackageDir, "InterfaceTwo.class"));
        Files.write(MultipleOverriddenMethods.implementation(), new File(mTestPackageDir, "Implementation.class"));

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
        Files.write(MultipleOverriddenMethods.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(MultipleOverriddenMethods.interfaceOne(), new File(mTestPackageDir, "InterfaceOne.class"));
        Files.write(MultipleOverriddenMethods.interfaceTwo(), new File(mTestPackageDir, "InterfaceTwo.class"));
        Files.write(MultipleOverriddenMethods.implementation(), new File(mTestPackageDir, "Implementation.class"));

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
        Files.write(Annotations.main_annotatedClass(), new File(mTestPackageDir, "Main.class"));
        Files.write(Annotations.myAnnotation(), new File(mTestPackageDir, "MyAnnotation.class"));
        Files.write(Annotations.myEnum(), new File(mTestPackageDir, "MyEnum.class"));
        Files.write(Annotations.nested(), new File(mTestPackageDir, "Nested.class"));
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
        Files.write(Annotations.main_annotatedMethod(), new File(mTestPackageDir, "Main.class"));
        Files.write(Annotations.myAnnotation(), new File(mTestPackageDir, "MyAnnotation.class"));
        Files.write(Annotations.nested(), new File(mTestPackageDir, "Nested.class"));
        Files.write(Annotations.myEnum(), new File(mTestPackageDir, "MyEnum.class"));
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
        Files.write(Annotations.main_annotatedMethod(), new File(mTestPackageDir, "Main.class"));
        Files.write(Annotations.myAnnotation(), new File(mTestPackageDir, "MyAnnotation.class"));
        Files.write(Annotations.nested(), new File(mTestPackageDir, "Nested.class"));
        Files.write(Annotations.myEnum(), new File(mTestPackageDir, "MyEnum.class"));
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
    public void signatures_classSignature() throws Exception {
        // Given:
        Files.write(Signatures.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Signatures.named(), new File(mTestPackageDir, "Named.class"));
        Files.write(Signatures.namedMap(), new File(mTestPackageDir, "NamedMap.class"));
        Files.write(Signatures.hasAge(), new File(mTestPackageDir, "HasAge.class"));

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
        Files.write(Signatures.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Signatures.named(), new File(mTestPackageDir, "Named.class"));
        Files.write(Signatures.namedMap(), new File(mTestPackageDir, "NamedMap.class"));
        Files.write(Signatures.hasAge(), new File(mTestPackageDir, "HasAge.class"));

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
        Files.write(SuperCalls.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(SuperCalls.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(SuperCalls.ccc(), new File(mTestPackageDir, "Ccc.class"));

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
        Files.write(SuperCalls.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(SuperCalls.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(SuperCalls.ccc(), new File(mTestPackageDir, "Ccc.class"));

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
        Files.write(SuperCalls.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(SuperCalls.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(SuperCalls.ccc(), new File(mTestPackageDir, "Ccc.class"));

        // When:
        run("Ccc", "callOverriddenMethod:()V");

        // Then:
        assertMembersLeft("Aaa");
        assertMembersLeft("Bbb", "overridden:()V");
        assertMembersLeft("Ccc", "callOverriddenMethod:()V");
    }

    @Test
    public void innerClasses() throws Exception {
        // Given:
        Files.write(InnerClasses.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(InnerClasses.hasInnerClass(), new File(mTestPackageDir, "HasInnerClass.class"));
        Files.write(InnerClasses.staticInnerClass(), new File(mTestPackageDir, "HasInnerClass$StaticInnerClass.class"));

        // When:
        run("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "main:()V");
        assertMembersLeft("HasInnerClass$StaticInnerClass", "method:()V", "<init>:()V");
        assertMembersLeft("HasInnerClass");
    }

    @Test
    public void staticMethods() throws Exception {
        // Given:
        Files.write(StaticMembers.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(StaticMembers.utils(), new File(mTestPackageDir, "Utils.class"));

        // When:
        run("Main", "callStaticMethod:()Ljava/lang/Object;");

        // Then:
        assertMembersLeft("Main", "callStaticMethod:()Ljava/lang/Object;");
        assertMembersLeft("Utils", "staticMethod:()Ljava/lang/Object;");
    }

    @Test
    public void staticFields_uninitialized() throws Exception {
        // Given:
        Files.write(StaticMembers.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(StaticMembers.utils(), new File(mTestPackageDir, "Utils.class"));

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
    public void reflection_classLiteral() throws Exception {
        // Given:
        Files.write(Reflection.main_classLiteral(), new File(mTestPackageDir, "Main.class"));
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
        Files.write(TryCatch.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(TryCatch.customException(), new File(mTestPackageDir, "CustomException.class"));

        // When:
        run("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "main:()V", "helper:()V");
        assertMembersLeft("CustomException");
    }

    @Test
    public void testTryFinally() throws Exception {
        // Given:
        Files.write(TryCatch.main_tryFinally(), new File(mTestPackageDir, "Main.class"));

        // When:
        run("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "main:()V", "helper:()V");
    }

    @Test
    public void abstractClasses_callToInterfaceMethodInAbstractClass() throws Exception {
        // Given:
        Files.write(AbstractClasses.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(AbstractClasses.abstractImpl(), new File(mTestPackageDir, "AbstractImpl.class"));
        Files.write(AbstractClasses.realImpl(), new File(mTestPackageDir, "RealImpl.class"));

        // When:
        run("RealImpl", "main:()V");

        // Then:
        assertMembersLeft("MyInterface", "m:()V");
        assertMembersLeft("RealImpl", "main:()V", "m:()V");
        assertMembersLeft("AbstractImpl", "helper:()V");
    }

    private void assertClassSkipped(String className) {
        assertFalse(
                "Class " + className + " exists in output.",
                getOutputClassFile(className).exists());
    }

    private void assertMembersLeft(String className, String... members) throws IOException {
        File outFile = getOutputClassFile(className);

        assertTrue(
                String.format("Class %s does not exist in output.", className),
                outFile.exists());

        assertThat(getMembers(outFile))
                .named("Members in class " + className)
                .containsExactly(members);
    }

    @NonNull
    private static File getAndroidJar() {
        File androidHome = new File(System.getenv(SdkConstants.ANDROID_HOME_ENV));
        File androidJar = FileUtils.join(
                androidHome,
                SdkConstants.FD_PLATFORMS,
                "android-22",
                "android.jar");
        assertTrue(androidJar.getAbsolutePath(), androidJar.exists());

        return androidJar;
    }

    @NonNull
    private File getOutputClassFile(String className) {
        return FileUtils.join(mOutDir, "test", className + ".class");
    }

    private void run(String className, String... methods) throws IOException {
        mShrinker.run(
                mInputs,
                Collections.<TransformInput>emptyList(),
                mOutput,
                ImmutableMap.<Shrinker.ShrinkType, KeepRules>of(
                        Shrinker.ShrinkType.SHRINK, new TestKeepRules(className, methods)),
                false);
    }


    private static Set<String> getMembers(File classFile) throws IOException {
        ClassReader classReader = new ClassReader(Files.toByteArray(classFile));
        ClassNode classNode = new ClassNode(Opcodes.ASM5);
        classReader.accept(
                classNode,
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        Set<String> methods = Sets.newHashSet();
        //noinspection unchecked - ASM API
        for (MethodNode methodNode : (List<MethodNode>) classNode.methods) {
            methods.add(methodNode.name + ":" + methodNode.desc);
        }

        //noinspection unchecked - ASM API
        for (FieldNode fieldNode : (List<FieldNode>) classNode.fields) {
            methods.add(fieldNode.name + ":" + fieldNode.desc);
        }

        return methods;
    }
}