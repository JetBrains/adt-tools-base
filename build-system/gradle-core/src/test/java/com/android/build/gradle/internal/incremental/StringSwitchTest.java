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

import org.gradle.internal.reflect.NoSuchMethodException;
import org.junit.Test;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.MethodNode;

import static org.objectweb.asm.Opcodes.*;

import com.android.utils.AsmUtils;

import java.lang.reflect.*;
import java.lang.reflect.Method;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


/**
 * Tests for StringSwitch.
 */
public class StringSwitchTest {

    public static String switchOn(String string) {
        return string;
    }

    public class TestClassLoader extends ClassLoader {
        public TestClassLoader(ClassLoader parent) {
            super(parent);
        }
        public Method getStringSwitchMethod(final String ...strings) {
            try {
                ClassReader cr = new ClassReader(
                        StringSwitchTest.class.getResourceAsStream(
                                "/com/android/build/gradle/internal/incremental/StringSwitchTest.class"));
                ClassWriter cw = new ClassWriter(0);
                ClassVisitor cv = new ClassVisitor(Opcodes.ASM5, cw) {
                    public MethodVisitor visitMethod(int access, String name, String desc,
                            String signature, String[] exceptions) {
                        MethodVisitor visitor = super
                                .visitMethod(access, name, desc, signature, exceptions);
                        if (name.equals("switchOn")) {
                            try {
                                org.objectweb.asm.commons.Method m =
                                        org.objectweb.asm.commons.Method.getMethod(
                                                StringSwitchTest.class
                                                        .getMethod("switchOn", String.class));
                                final GeneratorAdapter mv = new GeneratorAdapter(access, m,
                                        visitor);

                                mv.visitCode();

                                new StringSwitch() {
                                    @Override
                                    void visitString() {
                                        mv.visitVarInsn(Opcodes.ALOAD, 0);
                                    }

                                    @Override
                                    void visitCase(String methodName) {
                                        mv.push(methodName);
                                        mv.visitInsn(Opcodes.ARETURN);
                                    }

                                    @Override
                                    void visitDefault() {
                                        mv.push("No Match Found");
                                        mv.visitInsn(Opcodes.ARETURN);
                                    }
                                }.visit(mv, new HashSet<String>(Arrays.asList(strings)));

                                mv.visitMaxs(0, 0);
                                mv.visitEnd();
                            }  catch (Exception e) {
                                throw new RuntimeException(e.toString());
                            }
                        }
                        System.out.println(name);
                        return visitor;
                    }
                };
                cr.accept(cv, 0);
                byte[] bytes = cw.toByteArray();

                Class clazz = super.defineClass(
                        "com.android.build.gradle.internal.incremental.StringSwitchTest", bytes, 0, bytes.length);
                return clazz.getMethod("switchOn", String.class);
            } catch (IllegalArgumentException e) {
                throw e;
            }catch (Exception e) {
                throw new RuntimeException(e.toString());
            }
        }
    }

    Method getGeneratedSwitchMethod(String ...strings) {
        return new TestClassLoader(
                StringSwitchTest.class.getClassLoader())
                    .getStringSwitchMethod(strings);
    }

    void exercise(String ...strings) {
        Method m = getGeneratedSwitchMethod(strings);
        for (String string : strings) {
            try {
                String result = (String) m.invoke(null, string);
                if (!result.equals(string)) {
                    throw new RuntimeException(result);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test public void largeSet() {
        exercise(
                "com/example/basic/AllAccessFieldsSubclass.()V",
                "com/example/basic/AllAccessFields.()V",
                "com/example/basic/AllAccessMethods.()V",
                "com/example/basic/AllAccessStaticFields.()V",
                "com/example/basic/AllAccessStaticMethods.()V",
                "com/example/basic/AllTypesFields.()V",
                "com/example/basic/AnonymousClasses$1.()V",
                "com/example/basic/AnonymousClasses$2.()V",
                "com/example/basic/AnonymousClasses.()V",
                "com/example/basic/AnotherPackagePrivateClass$Builder.(Lcom/example/basic/AnotherPackagePrivateClass;)V",
                "com/example/basic/AnotherPackagePrivateClass.(Ljava/lang/Object;)V",
                "com/example/basic/ClashStaticMethod.(Ljava/lang/String;)V",
                "com/example/basic/Constructors$AbstractBase.(DLjava/lang/String;I)V",
                "com/example/basic/Constructors$AbstractBase.()V",
                "com/example/basic/Constructors$Base.(DLjava/lang/String;I)V",
                "com/example/basic/Constructors$Base.()V",
                "com/example/basic/Constructors$DupInvokeSpecialBase.(Lcom/example/basic/Constructors;Lcom/example/basic/Constructors$DupInvokeSpecialBase;)V",
                "com/example/basic/Constructors$DupInvokeSpecialSub.(Lcom/example/basic/Constructors;)V",
                "com/example/basic/Constructors.(Ljava/lang/String;)V",
                "com/example/basic/Constructors$Sub.(DLjava/lang/String;I)V",
                "com/example/basic/Constructors$Sub.(IIII)V",
                "com/example/basic/Constructors$Sub.(III)V",
                "com/example/basic/Constructors$Sub.(JF)V",
                "com/example/basic/Constructors$Sub.(Ljava/lang/String;Ljava/lang/String;Z)V",
                "com/example/basic/Constructors$Sub.(Ljava/lang/String;ZLjava/lang/String;)V",
                "com/example/basic/Constructors$Sub.(Ljava/lang/String;Z)V",
                "com/example/basic/Constructors$Sub.(Ljava/util/List;Z)V",
                "com/example/basic/Constructors$SubOfAbstract.(IIII)V",
                "com/example/basic/Constructors$Sub.()V",
                "com/example/basic/Constructors$Sub.(ZLjava/util/List;)V",
                "com/example/basic/Constructors$Sub.(Z)V",
                "com/example/basic/Constructors$Utility.()V",
                "com/example/basic/ControlClass.()V",
                "com/example/basic/CovariantChild.()V",
                "com/example/basic/CovariantParent.()V",
                "com/example/basic/Enums$1.(Ljava/lang/String;ILjava/lang/String;)V",
                "com/example/basic/Enums.(Ljava/lang/String;ILjava/lang/String;Lcom/example/basic/Enums$1;)V",
                "com/example/basic/Enums.(Ljava/lang/String;ILjava/lang/String;)V",
                "com/example/basic/Exceptions.(Ljava/lang/String;)V",
                "com/example/basic/Exceptions$MyException.(Ljava/lang/String;)V",
                "com/example/basic/Exceptions.()V",
                "com/example/basic/FieldOverridingChild.()V",
                "com/example/basic/FieldOverridingGrandChild.()V",
                "com/example/basic/FieldOverridingParent.()V",
                "com/example/basic/FinalFieldsInCtor.(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
                "com/example/basic/GrandChild.()V",
                "com/example/basic/InnerClass$Builder.()V",
                "com/example/basic/InnerClassInvoker.()V",
                "com/example/basic/InnerClass.(Ljava/lang/String;)V",
                "com/example/basic/MultipleMethodInvocations.()V",
                "com/example/basic/NoPackageAccess.()V",
                "com/example/basic/PackagePrivateClass$1.(Lcom/example/basic/PackagePrivateClass;)V",
                "com/example/basic/PackagePrivateClass.(Ljava/lang/String;)V",
                "com/example/basic/PackagePrivateFieldAccess.()V",
                "com/example/basic/PackagePrivateInvoker.()V",
                "com/example/basic/ParentInvocation.()V",
                "com/example/basic/PublicMethodInvoker.(I)V",
                "com/example/basic/ReflectiveUser.()V",
                "com/example/basic/StaticMethodsInvoker.()V",
                "com/example/basic/SuperCall$Base.()V",
                "com/example/basic/SuperCall$Sub.()V",
                "com/example/basic/SuperCall.()V",
                "com/ir/disable/InstantRunDisabledClass.()V",
                "com/ir/disable/InstantRunDisabledMethod.(Ljava/lang/String;)V",
                "com/ir/disable/InstantRunDisabledMethod.()V",
                "com/verifier/tests/AddClassAnnotation.()V",
                "com/verifier/tests/AddInstanceField.()V",
                "com/verifier/tests/AddInterfaceImplementation.()V",
                "com/verifier/tests/AddMethodAnnotation.()V",
                "com/verifier/tests/AddNotRuntimeClassAnnotation.()V",
                "com/verifier/tests/ChangedClassInitializer1.()V",
                "com/verifier/tests/ChangedClassInitializer2.()V",
                "com/verifier/tests/ChangedClassInitializer3.()V",
                "com/verifier/tests/ChangeFieldType.()V",
                "com/verifier/tests/ChangeInstanceFieldToStatic.()V",
                "com/verifier/tests/ChangeInstanceFieldVisibility.()V",
                "com/verifier/tests/ChangeStaticFieldToInstance.()V",
                "com/verifier/tests/ChangeStaticFieldVisibility.()V",
                "com/verifier/tests/ChangeSuperClass.()V",
                "com/verifier/tests/MethodAddedClass.()V",
                "com/verifier/tests/MethodCollisionClass.()V",
                "com/verifier/tests/RemoveClassAnnotation.()V",
                "com/verifier/tests/RemoveInterfaceImplementation.()V",
                "com/verifier/tests/RemoveMethodAnnotation.()V",
                "com/verifier/tests/RemoveNotRuntimeClassAnnotation.()V",
                "com/verifier/tests/UnchangedClassInitializer1.()V",
                "com/verifier/tests/UnchangedClass.()V",
                "java/lang/Enum.(Ljava/lang/String;I)V",
                "java/lang/Exception.(Ljava/lang/String;Ljava/lang/Throwable;)V",
                "java/lang/Exception.(Ljava/lang/String;)V",
                "java/lang/Exception.(Ljava/lang/Throwable;)V",
                "java/lang/Exception.()V",
                "java/lang/Object.()V",
                "NoPackage.(Ljava/lang/String;)V"
        );
    }

    @Test public void hashCollide() {
        exercise("FB", "Ea");
    }

}


