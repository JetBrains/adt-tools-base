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

package com.android.build.gradle.internal.incremental;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests for IncrementalSupportVisitor class.
 */
public class IncrementalSupportVisitorTest {

    /**
     * All test classes located in package_private_one
     *
     * public class A defines publicMethod
     * package private class B extends A and overrides publicMethod.
     * public class C extends B
     *
     * different package class D extends C.
     */
    @Test
    public void testAccessSuperWithPackagePrivateSuperclasses_One()
            throws ClassNotFoundException, NoSuchFieldException, InstantiationException,
            IllegalAccessException, IOException {

        assertThat(getOwnerForAccessSuperMethodDispatch(com.package_private_one.b.D.class))
                .isEqualTo("com/package_private_one/a/A");
    }

    /**
     * All test classes located in package_private_two
     *
     * public class A defines publicMethod
     * package private class B extends A and overrides publicMethod.
     * public class C extends B
     * package private class D extends C and overrides publicMethod.
     * public class E extends D
     *
     * different package class F extends D.
     */
    @Test
    public void testAccessSuperWithPackagePrivateSuperclasses_Two()
            throws ClassNotFoundException, NoSuchFieldException, InstantiationException,
            IllegalAccessException, IOException {

        assertThat(getOwnerForAccessSuperMethodDispatch(com.package_private_two.b.F.class))
                .isEqualTo("com/package_private_two/a/A");
    }

    /**
     * All test classes located in package_private_three
     *
     * public class A defines publicMethod
     * package private class B extends A and overrides publicMethod.
     * package private class C extends B and overrides publicMethod.
     * public class D extends C
     * public class E extends D
     *
     * different package class E extends D.
     */
    @Test
    public void testAccessSuperWithPackagePrivateSuperclasses_Three()
            throws ClassNotFoundException, NoSuchFieldException, InstantiationException,
            IllegalAccessException, IOException {

        assertThat(getOwnerForAccessSuperMethodDispatch(com.package_private_three.b.E.class))
                .isEqualTo("com/package_private_three/a/A");
    }

    /**
     * All test classes located in package_private_four
     *
     * public class A defines publicMethod
     * public class B extends A and overrides publicMethod with an abstract definition
     * package private class C extends B and overrides publicMethod.
     * public class D extends C
     *
     * different package class E extends D.
     */
    @Test
    public void testAccessSuperWithPackagePrivateSuperclasses_Four()
            throws ClassNotFoundException, NoSuchFieldException, InstantiationException,
            IllegalAccessException, IOException {

        assertThat(getOwnerForAccessSuperMethodDispatch(com.package_private_four.b.E.class))
                .isEqualTo("com/package_private_four/a/A");
    }

    /**
     * All test classes located in package_private_five
     *
     * package private class A defines publicMethod
     * public class B extends A
     *
     * different package class C extends B.
     */
    @Test
    public void testAccessSuperWithPackagePrivateSuperclasses_Five()
            throws ClassNotFoundException, NoSuchFieldException, InstantiationException,
            IllegalAccessException, IOException {

        assertThat(getOwnerForAccessSuperMethodDispatch(com.package_private_five.b.C.class))
                .isEqualTo("com/package_private_five/a/B");
    }

    /**
     * All test classes located in package_private_six
     *
     * public class A
     * package private class B extends A and defines publicMethod
     * public class C extends B
     *
     * different package class E extends D.
     */
    @Test
    public void testAccessSuperWithPackagePrivateSuperclasses_Six()
            throws ClassNotFoundException, NoSuchFieldException, InstantiationException,
            IllegalAccessException, IOException {

        assertThat(getOwnerForAccessSuperMethodDispatch(com.package_private_six.b.D.class))
                .isEqualTo("com/package_private_six/a/C");
    }

    /**
     * All test classes located in package_private_seven
     *
     * public class A defines publicMethod
     * package private class B extends A and overrides publicMethod
     * same package C extends B
     */
    @Test
    public void testAccessSuperWithPackagePrivateSuperclasses_Seven()
            throws ClassNotFoundException, NoSuchFieldException, InstantiationException,
            IllegalAccessException, IOException {

        assertThat(getOwnerForAccessSuperMethodDispatch(com.package_private_seven.a.C.class))
                .isEqualTo("com/package_private_seven/a/B");
    }

    /**
     * All test classes located in package_private_eight
     *
     * public class A defines publicMethod
     * public class B extends A defines publicMethod(int)
     * package private class C extends B and overrides both publicMethods
     * different package D extends C
     */
    @Test
    public void testAccessSuperWithPackagePrivateSuperclasses_eight()
            throws ClassNotFoundException, NoSuchFieldException, InstantiationException,
            IllegalAccessException, IOException {

        assertThat(getOwnerForAccessSuperMethodDispatch(
                com.package_private_eight.b.E.class, "publicMethod", "()V"))
                .isEqualTo("com/package_private_eight/a/A");
        assertThat(getOwnerForAccessSuperMethodDispatch(
                com.package_private_eight.b.E.class, "publicMethod", "(I)V"))
                .isEqualTo("com/package_private_eight/a/B");
    }

    @Nullable
    static String getOwnerForAccessSuperMethodDispatch(Class<?> clazz) throws IOException {
        return getOwnerForAccessSuperMethodDispatch(clazz, "publicMethod", null);
    }

    @Nullable
    static String getOwnerForAccessSuperMethodDispatch(
            @NonNull Class<?> clazz,
            @NonNull String methodName,
            @Nullable String methodDesc) throws IOException {

        // parse the class with asm.
        ClassNode classNode = parseClass(clazz);
        MethodNode methodNode = findMethodByName(classNode.methods, "access$super");
        assertThat(methodNode).isNotNull();

        return findOwnerForMethodDispatch(methodNode, methodName, methodDesc);
    }

    @NonNull
    static ClassNode parseClass(@NonNull Class<?> clazz) throws IOException {
        // parse the class with asm.
        Type type = Type.getType(clazz);
        ClassReader cr = new ClassReader(type.getInternalName());
        ClassNode classNode = new ClassNode();
        cr.accept(classNode, 0);
        return classNode;
    }

    @Nullable
    static MethodNode findMethodByName(@NonNull List<MethodNode> methods, @NonNull String name) {
        for (MethodNode method : methods) {
            if (method.name.equals(name)) {
                return method;
            }
        }
        return null;
    }

    @Nullable
    public static String findOwnerForMethodDispatch(
            @NonNull MethodNode methodNode,
            @NonNull String dispatchedMethod,
            @Nullable String dispatchedDesc) {
        AtomicReference<String> ownerRef = new AtomicReference<>(null);
        MethodVisitor methodVisitor = new MethodVisitor(Opcodes.ASM5) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc,
                    boolean itf) {
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                if (name.equals(dispatchedMethod) &&
                        (dispatchedDesc == null || dispatchedDesc.equals(desc))) {
                    ownerRef.set(owner);
                }
            }
        };
        methodNode.accept(methodVisitor);
        return ownerRef.get();
    }
}
