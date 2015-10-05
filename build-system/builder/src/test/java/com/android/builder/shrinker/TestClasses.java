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

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Code for generating test classes for the {@link Shrinker}. This were created using the ASM
 * bytecode outliner plugin for IJ.
 */
@SuppressWarnings("unused") // Outliner plugin generates some unused visitors.
public class TestClasses implements Opcodes {

    /**
     * Simple scenario with two related classes and one unused class.
     */
    public static class SimpleScenario {
        static byte[] aaa() {
            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Aaa", null,
                    "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "aaa", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/Aaa", "bbb", "()V",
                        false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "bbb", "()V", null, null);
                mv.visitCode();
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "ccc", "()V", null, null);
                mv.visitCode();
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        /**
         * Same as the Bbb class, but the bbb method calls A.ccc instead of A.aaa. For testing
         * incremental updates.
         */
        static byte[] bbb2() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Bbb", null,
                    "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "bbb", "(Ltest/Aaa;)V", null,
                        null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/Aaa", "ccc", "()V",
                        false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 2);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        static byte[] bbb() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Bbb", null,
                    "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "bbb", "(Ltest/Aaa;)V", null,
                        null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/Aaa", "aaa", "()V",
                        false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 2);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        static byte[] ccc() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Ccc", null,
                    "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "ccc", "(Ltest/Aaa;)V", null,
                        null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/Aaa", "aaa", "()V",
                        false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 2);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }
    }

    /**
     * Virtual calls.
     */
    public static class VirtualCalls {
        public static byte[] abstractClass() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER + ACC_ABSTRACT,
                    "test/AbstractClass", null, "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_ABSTRACT, "abstractMethod", "()V", null, null);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] impl(int i) throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Impl" + i, null,
                    "test/AbstractClass", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "test/AbstractClass",
                        "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "abstractMethod", "()V", null, null);
                mv.visitCode();
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] main_concreteType() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Main", null,
                    "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null,
                        null);
                mv.visitCode();
                mv.visitTypeInsn(NEW, "test/Impl2");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Impl2", "<init>", "()V",
                        false);
                mv.visitInsn(POP);
                mv.visitTypeInsn(NEW, "test/Impl1");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Impl1", "<init>", "()V",
                        false);
                mv.visitVarInsn(ASTORE, 1);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/Impl1",
                        "abstractMethod", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 2);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] main_abstractType() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Main", null,
                    "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null,
                        null);
                mv.visitCode();
                mv.visitTypeInsn(NEW, "test/Impl2");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Impl2", "<init>", "()V",
                        false);
                mv.visitInsn(POP);
                mv.visitTypeInsn(NEW, "test/Impl1");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Impl1", "<init>", "()V",
                        false);
                mv.visitVarInsn(ASTORE, 1);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/AbstractClass",
                        "abstractMethod", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 2);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] main_parentChild() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Main", null,
                    "java/lang/Object", null);

            cw.visitSource("Main.java", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(22, l0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLocalVariable("this", "Ltest/Main;", null, l0, l1,
                        0);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(0, "main", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(24, l0);
                mv.visitTypeInsn(NEW, "test/Child");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Child", "<init>",
                        "()V", false);
                mv.visitVarInsn(ASTORE, 1);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLineNumber(25, l1);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/Child",
                        "onlyInParent", "()V", false);
                Label l2 = new Label();
                mv.visitLabel(l2);
                mv.visitLineNumber(26, l2);
                mv.visitInsn(RETURN);
                Label l3 = new Label();
                mv.visitLabel(l3);
                mv.visitLocalVariable("this", "Ltest/Main;", null, l0, l3,
                        0);
                mv.visitLocalVariable("c", "Ltest/Child;", null, l1, l3,
                        1);
                mv.visitMaxs(2, 2);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] parent() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Parent", null,
                    "java/lang/Object", null);

            cw.visitSource("Parent.java", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(22, l0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLocalVariable("this", "Ltest/Parent;", null, l0,
                        l1, 0);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "onlyInParent", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(25, l0);
                mv.visitInsn(RETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLocalVariable("this", "Ltest/Parent;", null, l0,
                        l1, 0);
                mv.visitMaxs(0, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] child() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Child", null,
                    "test/Parent", null);

            cw.visitSource("Child.java", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(22, l0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Parent", "<init>",
                        "()V", false);
                mv.visitInsn(RETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLocalVariable("this", "Ltest/Child;", null, l0, l1,
                        0);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }
    }

    /**
     * SDK types.
     */
    public static class SdkTypes {
        public static byte[] main() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Main", null, "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null,
                        null);
                mv.visitCode();
                mv.visitTypeInsn(NEW, "test/MyException");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/MyException", "<init>", "()V", false);
                mv.visitVarInsn(ASTORE, 1);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 2);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        /**
         * It inherits from an SDK class, overrides one method from Exception, one from Object (we
         * may have special handling code for Object, so check both) and adds one unused method.
         */
        public static byte[] myException() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/MyException",
                    null, "java/lang/Exception", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Exception", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "getMessage", "()Ljava/lang/String;", null, null);
                mv.visitCode();
                mv.visitLdcInsn("custom message");
                mv.visitInsn(ARETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "hashCode", "()I", null, null);
                mv.visitCode();
                mv.visitIntInsn(BIPUSH, 42);
                mv.visitInsn(IRETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "m", "()V", null, null);
                mv.visitCode();
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }
    }

    /**
     * Interfaces.
     */
    public static class Interfaces {
        public static byte[] main() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Main", null,
                    "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "buildMyCharSequence",
                        "()Ltest/MyCharSequence;", null, null);
                mv.visitCode();
                mv.visitTypeInsn(NEW, "test/MyCharSequence");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/MyCharSequence",
                        "<init>", "()V", false);
                mv.visitInsn(ARETURN);
                mv.visitMaxs(2, 0);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "buildMyImpl",
                        "()Ltest/MyImpl;", null, null);
                mv.visitCode();
                mv.visitTypeInsn(NEW, "test/MyImpl");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/MyImpl", "<init>",
                        "()V", false);
                mv.visitInsn(ARETURN);
                mv.visitMaxs(2, 0);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "callCharSequence",
                        "(Ljava/lang/CharSequence;)V",
                        null,
                        new String[]{"java/lang/Exception"});
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKEINTERFACE, "java/lang/CharSequence", "length",
                        "()I", true);
                mv.visitInsn(POP);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "callMyCharSequence",
                        "(Ltest/MyCharSequence;)V", null,
                        new String[]{"java/lang/Exception"});
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/MyCharSequence",
                        "length", "()I", false);
                mv.visitInsn(POP);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "useMyInterface",
                        "(Ltest/MyInterface;)V",
                        null,
                        null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitInsn(ACONST_NULL);
                mv.visitMethodInsn(INVOKEINTERFACE, "test/MyInterface",
                        "doSomething", "(Ljava/lang/Object;)V", true);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "useImplementationFromSuperclass",
                        "(Ltest/ImplementationFromSuperclass;)V",
                        null,
                        null);
                mv.visitCode();
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "useMyImpl_interfaceMethod",
                        "(Ltest/MyImpl;)V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitLdcInsn("foo");
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/MyImpl",
                        "doSomething", "(Ljava/lang/Object;)V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "useMyImpl_otherMethod",
                        "(Ltest/MyImpl;)V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/MyImpl",
                        "someOtherMethod", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] myCharSequence() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/MyCharSequence",
                    null, "java/lang/Object", new String[]{"java/lang/CharSequence"});

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "length", "()I", null, null);
                mv.visitCode();
                mv.visitInsn(ICONST_0);
                mv.visitInsn(IRETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "charAt", "(I)C", null, null);
                mv.visitCode();
                mv.visitInsn(ICONST_0);
                mv.visitInsn(IRETURN);
                mv.visitMaxs(1, 2);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "subSequence", "(II)Ljava/lang/CharSequence;", null,
                        null);
                mv.visitCode();
                mv.visitInsn(ACONST_NULL);
                mv.visitInsn(ARETURN);
                mv.visitMaxs(1, 3);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] myInterface() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_ABSTRACT + ACC_INTERFACE,
                    "test/MyInterface", null, "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_ABSTRACT, "doSomething", "(Ljava/lang/Object;)V",
                        "(TT;)V", null);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] myImpl() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/MyImpl",
                    null, "java/lang/Object", new String[]{"test/MyInterface"});

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "doSomething", "(Ljava/lang/Object;)V", null, null);
                mv.visitCode();
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 2);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "someOtherMethod", "()V", null, null);
                mv.visitCode();
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        /**
         * This class happens to have a method matching the interface signature. It does not
         * implement the interface.
         */
        public static byte[] doesSomething() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/DoesSomething",
                    null, "java/lang/Object", null);

            cw.visitSource("DoesSomething.java", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(22, l0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLocalVariable("this", "Ltest/DoesSomething;", null,
                        l0, l1, 0);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "doSomething", "(Ljava/lang/Object;)V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(25, l0);
                mv.visitInsn(RETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLocalVariable("this", "Ltest/DoesSomething;", null,
                        l0, l1, 0);
                mv.visitMaxs(0, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        /**
         * This class extends DoesSomething and implements MyInterface. Extending DoesSomething 
         * is enough to implement the interface.
         */
        public static byte[] implementationFromSuperclass() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER,
                    "test/ImplementationFromSuperclass", null,
                    "test/DoesSomething",
                    new String[]{"test/MyInterface"});

            cw.visitSource("ImplementationFromSuperclass.java", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(22, l0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "test/DoesSomething",
                        "<init>", "()V", false);
                mv.visitInsn(RETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLocalVariable("this",
                        "Ltest/ImplementationFromSuperclass;", null, l0,
                        l1, 0);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }
    }

    /**
     * Fields.
     */
    public static class Fields {

        public static byte[] main() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Main", null,
                    "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "()I", null, null);
                mv.visitCode();
                mv.visitTypeInsn(NEW, "test/MyFields");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/MyFields",
                        "<init>", "()V", false);
                mv.visitVarInsn(ASTORE, 0);
                mv.visitFieldInsn(GETSTATIC, "test/MyFields", "sString",
                        "Ljava/lang/String;");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, "test/MyFields", "f1", "I");
                mv.visitInsn(IADD);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/MyFields",
                        "readField", "()I", false);
                mv.visitInsn(IADD);
                mv.visitInsn(IRETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main_subclass", "()I", null, null);
                mv.visitCode();
                mv.visitTypeInsn(NEW, "test/MyFieldsSubclass");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/MyFieldsSubclass",
                        "<init>", "()V", false);
                mv.visitVarInsn(ASTORE, 0);
                mv.visitFieldInsn(GETSTATIC, "test/MyFieldsSubclass", "sString",
                        "Ljava/lang/String;");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, "test/MyFieldsSubclass", "f1", "I");
                mv.visitInsn(IADD);
                mv.visitInsn(IRETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] myFields() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/MyFields", null,
                    "java/lang/Object", null);

            {
                fv = cw.visitField(ACC_PUBLIC + ACC_STATIC, "sString", "Ljava/lang/String;", null,
                        null);
                fv.visitEnd();
            }
            {
                fv = cw.visitField(ACC_PUBLIC, "f1", "I", null, null);
                fv.visitEnd();
            }
            {
                fv = cw.visitField(ACC_PRIVATE, "f2", "I", null, null);
                fv.visitEnd();
            }
            {
                fv = cw.visitField(ACC_PRIVATE, "f3", "I", null, null);
                fv.visitEnd();
            }
            {
                fv = cw.visitField(ACC_PRIVATE, "f4", "Ltest/MyFieldType;",
                        null, null);
                fv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitIntInsn(BIPUSH, 17);
                mv.visitFieldInsn(PUTFIELD, "test/MyFields", "f1", "I");
                mv.visitVarInsn(ALOAD, 0);
                mv.visitIntInsn(BIPUSH, 42);
                mv.visitFieldInsn(PUTFIELD, "test/MyFields", "f2", "I");
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "readField", "()I", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, "test/MyFields", "f4",
                        "Ltest/MyFieldType;");
                Label l0 = new Label();
                mv.visitJumpInsn(IFNULL, l0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, "test/MyFields", "f2", "I");
                mv.visitInsn(IRETURN);
                mv.visitLabel(l0);
                mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                mv.visitInsn(ICONST_0);
                mv.visitInsn(IRETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
                mv.visitCode();
                mv.visitLdcInsn("foo");
                mv.visitFieldInsn(PUTSTATIC, "test/MyFields", "sString",
                        "Ljava/lang/String;");
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 0);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] myFieldsSubclass() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/MyFieldsSubclass", null,
                    "test/MyFields", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "test/MyFields", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }
    }

    /**
     * Method overrides multiples interfaces.
     */
    public static class MultipleOverriddenMethods {
        public static byte[] main() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Main", null,
                    "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(0, "useInterfaceOne",
                        "(Ltest/InterfaceOne;)V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEINTERFACE, "test/InterfaceOne",
                        "m", "()V", true);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 2);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(0, "useInterfaceTwo",
                        "(Ltest/InterfaceTwo;)V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEINTERFACE, "test/InterfaceTwo",
                        "m", "()V", true);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 2);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(0, "useImplementation",
                        "(Ltest/Implementation;)V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/Implementation",
                        "m", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 2);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(0, "buildImplementation", "()V", null, null);
                mv.visitCode();
                mv.visitTypeInsn(NEW, "test/Implementation");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Implementation",
                        "<init>", "()V", false);
                mv.visitInsn(POP);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] interfaceOne() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_ABSTRACT + ACC_INTERFACE,
                    "test/InterfaceOne", null, "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_ABSTRACT, "m", "()V", null, null);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] interfaceTwo() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_ABSTRACT + ACC_INTERFACE,
                    "test/InterfaceTwo", null, "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_ABSTRACT, "m", "()V", null, null);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] implementation() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Implementation",
                    null, "java/lang/Object",
                    new String[]{"test/InterfaceOne",
                            "test/InterfaceTwo"});

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "m", "()V", null, null);
                mv.visitCode();
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }
    }

    /**
     * Annotations.
     */
    public static class Annotations {
        public static byte[] myAnnotation() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_ANNOTATION + ACC_ABSTRACT + ACC_INTERFACE,
                    "test/MyAnnotation", null, "java/lang/Object",
                    new String[]{"java/lang/annotation/Annotation"});

            {
                av0 = cw.visitAnnotation("Ljava/lang/annotation/Target;", true);
                {
                    AnnotationVisitor av1 = av0.visitArray("value");
                    av1.visitEnum(null, "Ljava/lang/annotation/ElementType;", "TYPE");
                    av1.visitEnum(null, "Ljava/lang/annotation/ElementType;", "METHOD");
                    av1.visitEnd();
                }
                av0.visitEnd();
            }
            {
                av0 = cw.visitAnnotation("Ljava/lang/annotation/Retention;", true);
                av0.visitEnum("value", "Ljava/lang/annotation/RetentionPolicy;", "RUNTIME");
                av0.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_ABSTRACT, "klass", "()Ljava/lang/Class;",
                        "()Ljava/lang/Class<*>;", null);
                {
                    av0 = mv.visitAnnotationDefault();
                    av0.visit(null, Type.getType("Ltest/SomeClass;"));
                    av0.visitEnd();
                }
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_ABSTRACT, "myEnum",
                        "()Ltest/MyEnum;", null, null);
                {
                    av0 = mv.visitAnnotationDefault();
                    av0.visitEnum(null, "Ltest/MyEnum;", "ONE");
                    av0.visitEnd();
                }
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_ABSTRACT, "f", "()I", null, null);
                {
                    av0 = mv.visitAnnotationDefault();
                    av0.visit(null, 14);
                    av0.visitEnd();
                }
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_ABSTRACT, "nested",
                        "()[Ltest/Nested;", null, null);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] myEnum() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_FINAL + ACC_SUPER + ACC_ENUM,
                    "test/MyEnum",
                    "Ljava/lang/Enum<Ltest/MyEnum;>;", "java/lang/Enum",
                    null);

            cw.visitSource("MyEnum.java", null);

            {
                fv = cw.visitField(ACC_PUBLIC + ACC_FINAL + ACC_STATIC + ACC_ENUM, "ONE",
                        "Ltest/MyEnum;", null, null);
                fv.visitEnd();
            }
            {
                fv = cw.visitField(ACC_PUBLIC + ACC_FINAL + ACC_STATIC + ACC_ENUM, "TWO",
                        "Ltest/MyEnum;", null, null);
                fv.visitEnd();
            }
            {
                fv = cw.visitField(ACC_PRIVATE + ACC_FINAL + ACC_STATIC + ACC_SYNTHETIC, "$VALUES",
                        "[Ltest/MyEnum;", null, null);
                fv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "values",
                        "()[Ltest/MyEnum;", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(22, l0);
                mv.visitFieldInsn(GETSTATIC, "test/MyEnum", "$VALUES",
                        "[Ltest/MyEnum;");
                mv.visitMethodInsn(INVOKEVIRTUAL, "[Ltest/MyEnum;",
                        "clone", "()Ljava/lang/Object;", false);
                mv.visitTypeInsn(CHECKCAST, "[Ltest/MyEnum;");
                mv.visitInsn(ARETURN);
                mv.visitMaxs(1, 0);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "valueOf",
                        "(Ljava/lang/String;)Ltest/MyEnum;", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(22, l0);
                mv.visitLdcInsn(Type.getType("Ltest/MyEnum;"));
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Enum", "valueOf",
                        "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;", false);
                mv.visitTypeInsn(CHECKCAST, "test/MyEnum");
                mv.visitInsn(ARETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLocalVariable("name", "Ljava/lang/String;", null, l0, l1, 0);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PRIVATE, "<init>", "(Ljava/lang/String;I)V", "()V", null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(22, l0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitVarInsn(ILOAD, 2);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V",
                        false);
                mv.visitInsn(RETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLocalVariable("this", "Ltest/MyEnum;", null, l0,
                        l1, 0);
                mv.visitMaxs(3, 3);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(23, l0);
                mv.visitTypeInsn(NEW, "test/MyEnum");
                mv.visitInsn(DUP);
                mv.visitLdcInsn("ONE");
                mv.visitInsn(ICONST_0);
                mv.visitMethodInsn(INVOKESPECIAL, "test/MyEnum", "<init>",
                        "(Ljava/lang/String;I)V", false);
                mv.visitFieldInsn(PUTSTATIC, "test/MyEnum", "ONE",
                        "Ltest/MyEnum;");
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLineNumber(24, l1);
                mv.visitTypeInsn(NEW, "test/MyEnum");
                mv.visitInsn(DUP);
                mv.visitLdcInsn("TWO");
                mv.visitInsn(ICONST_1);
                mv.visitMethodInsn(INVOKESPECIAL, "test/MyEnum", "<init>",
                        "(Ljava/lang/String;I)V", false);
                mv.visitFieldInsn(PUTSTATIC, "test/MyEnum", "TWO",
                        "Ltest/MyEnum;");
                Label l2 = new Label();
                mv.visitLabel(l2);
                mv.visitLineNumber(22, l2);
                mv.visitInsn(ICONST_2);
                mv.visitTypeInsn(ANEWARRAY, "test/MyEnum");
                mv.visitInsn(DUP);
                mv.visitInsn(ICONST_0);
                mv.visitFieldInsn(GETSTATIC, "test/MyEnum", "ONE",
                        "Ltest/MyEnum;");
                mv.visitInsn(AASTORE);
                mv.visitInsn(DUP);
                mv.visitInsn(ICONST_1);
                mv.visitFieldInsn(GETSTATIC, "test/MyEnum", "TWO",
                        "Ltest/MyEnum;");
                mv.visitInsn(AASTORE);
                mv.visitFieldInsn(PUTSTATIC, "test/MyEnum", "$VALUES",
                        "[Ltest/MyEnum;");
                mv.visitInsn(RETURN);
                mv.visitMaxs(4, 0);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] main_annotatedClass() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Main", null,
                    "java/lang/Object", null);

            {
                av0 = cw.visitAnnotation("Ltest/MyAnnotation;", true);
                av0.visit("klass", Type.getType("Ltest/SomeOtherClass;"));
                av0.visitEnum("myEnum", "Ltest/MyEnum;", "TWO");
                {
                    AnnotationVisitor av1 = av0.visitArray("nested");
                    {
                        AnnotationVisitor av2 = av1
                                .visitAnnotation(null, "Ltest/Nested;");
                        av2.visit("name", "foo");
                        av2.visitEnd();
                    }
                    av1.visitEnd();
                }
                av0.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "()V", null, null);
                mv.visitCode();
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] main_annotatedMethod() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Main", null,
                    "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "()V", null, null);
                {
                    av0 = mv.visitAnnotation("Ltest/MyAnnotation;", true);
                    av0.visit("klass",
                            Type.getType("Ltest/SomeOtherClass;"));
                    av0.visitEnum("myEnum", "Ltest/MyEnum;", "TWO");
                    {
                        AnnotationVisitor av1 = av0.visitArray("nested");
                        {
                            AnnotationVisitor av2 = av1.visitAnnotation(null,
                                    "Ltest/Nested;");
                            av2.visit("name", "foo");
                            av2.visitEnd();
                        }
                        av1.visitEnd();
                    }
                    av0.visitEnd();
                }
                mv.visitCode();
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "notAnnotated", "()V", null, null);
                mv.visitCode();
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] nested() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_ANNOTATION + ACC_ABSTRACT + ACC_INTERFACE,
                    "test/Nested", null, "java/lang/Object",
                    new String[]{"java/lang/annotation/Annotation"});

            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_ABSTRACT, "name", "()Ljava/lang/String;", null,
                        null);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }
    }

    /**
     * Generic signatures.
     */
    public static class Signatures {
        public static byte[] main() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Main", null,
                    "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main",
                        "(Ltest/NamedMap;)V",
                        "(Ltest/NamedMap<*>;)V", null);
                mv.visitCode();
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "callMethod",
                        "(Ltest/NamedMap;)V",
                        "(Ltest/NamedMap<*>;)V", null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitInsn(ACONST_NULL);
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/NamedMap",
                        "method", "(Ljava/util/Collection;)V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] namedMap() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/NamedMap",
                    "<T::Ljava/io/Serializable;:Ltest/Named;>Ljava/lang/Object;",
                    "java/lang/Object", null);

            {
                fv = cw.visitField(0, "instance", "Ljava/io/Serializable;", "TT;", null);
                fv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(0, "method", "(Ljava/util/Collection;)V",
                        "<I::Ltest/HasAge;>(Ljava/util/Collection<TI;>;)V",
                        null);
                mv.visitCode();
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 2);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] named() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_ABSTRACT + ACC_INTERFACE,
                    "test/Named", null, "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_ABSTRACT, "getName", "()Ljava/lang/String;", null,
                        null);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion") // Mirrors interface name.
        public static byte[] hasAge() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_ABSTRACT + ACC_INTERFACE,
                    "test/HasAge", null, "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_ABSTRACT, "getAge", "()I", null, null);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }
    }


    /**
     * invokespecial when making "normal" super calls.
     */
    public static class SuperCalls {
        public static byte[] aaa() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Aaa", null,
                    "java/lang/Object", null);

            cw.visitSource("Aaa.java", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(22, l0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLocalVariable("this", "Ltest/Aaa;", null, l0, l1,
                        0);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "onlyInAaa", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(23, l0);
                mv.visitInsn(RETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLocalVariable("this", "Ltest/Aaa;", null, l0, l1,
                        0);
                mv.visitMaxs(0, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "overridden", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(24, l0);
                mv.visitInsn(RETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLocalVariable("this", "Ltest/Aaa;", null, l0, l1,
                        0);
                mv.visitMaxs(0, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] bbb() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Bbb", null,
                    "test/Aaa", null);

            cw.visitSource("Bbb.java", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(22, l0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Aaa", "<init>",
                        "()V", false);
                mv.visitInsn(RETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLocalVariable("this", "Ltest/Bbb;", null, l0, l1,
                        0);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "overridden", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(25, l0);
                mv.visitInsn(RETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLocalVariable("this", "Ltest/Bbb;", null, l0, l1,
                        0);
                mv.visitMaxs(0, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "onlyInBbb", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(27, l0);
                mv.visitInsn(RETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLocalVariable("this", "Ltest/Bbb;", null, l0, l1,
                        0);
                mv.visitMaxs(0, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] ccc() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Ccc", null,
                    "test/Bbb", null);

            cw.visitSource("Ccc.java", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(22, l0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Bbb", "<init>",
                        "()V", false);
                mv.visitInsn(RETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLocalVariable("this", "Ltest/Ccc;", null, l0, l1,
                        0);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(0, "callAaaMethod", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(24, l0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Bbb", "onlyInAaa",
                        "()V", false);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLineNumber(25, l1);
                mv.visitInsn(RETURN);
                Label l2 = new Label();
                mv.visitLabel(l2);
                mv.visitLocalVariable("this", "Ltest/Ccc;", null, l0, l2,
                        0);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(0, "callBbbMethod", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(28, l0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Bbb", "onlyInBbb",
                        "()V", false);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLineNumber(29, l1);
                mv.visitInsn(RETURN);
                Label l2 = new Label();
                mv.visitLabel(l2);
                mv.visitLocalVariable("this", "Ltest/Ccc;", null, l0, l2,
                        0);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(0, "callOverriddenMethod", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(32, l0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Bbb", "overridden",
                        "()V", false);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLineNumber(33, l1);
                mv.visitInsn(RETURN);
                Label l2 = new Label();
                mv.visitLabel(l2);
                mv.visitLocalVariable("this", "Ltest/Ccc;", null, l0, l2,
                        0);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }
    }

    public static class InnerClasses {
        public static byte[] main() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Main", null,
                    "java/lang/Object", null);

            cw.visitInnerClass("test/HasInnerClass$StaticInnerClass",
                    "test/HasInnerClass", "StaticInnerClass",
                    ACC_PUBLIC + ACC_STATIC);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(0, "main", "()V", null, null);
                mv.visitCode();
                mv.visitTypeInsn(NEW,
                        "test/HasInnerClass$StaticInnerClass");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL,
                        "test/HasInnerClass$StaticInnerClass", "<init>",
                        "()V", false);
                mv.visitVarInsn(ASTORE, 1);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEVIRTUAL,
                        "test/HasInnerClass$StaticInnerClass", "method",
                        "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 2);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion") // Mirrors class name.
        public static byte[] hasInnerClass() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/HasInnerClass",
                    null, "java/lang/Object", null);

            cw.visitInnerClass("test/HasInnerClass$StaticInnerClass",
                    "test/HasInnerClass", "StaticInnerClass",
                    ACC_PUBLIC + ACC_STATIC);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] staticInnerClass() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER,
                    "test/HasInnerClass$StaticInnerClass", null,
                    "java/lang/Object", null);

            cw.visitInnerClass("test/HasInnerClass$StaticInnerClass",
                    "test/HasInnerClass", "StaticInnerClass",
                    ACC_PUBLIC + ACC_STATIC);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "method", "()V", null, null);
                mv.visitCode();
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }
    }

    public static class StaticMembers {
        public static byte[] main() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Main",
                    null, "java/lang/Object", null);

            cw.visitSource("Main.java", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(22, l0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLocalVariable("this", "Ltest/Main;", null,
                        l0, l1, 0);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(0, "callStaticMethod", "()Ljava/lang/Object;", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(24, l0);
                mv.visitMethodInsn(INVOKESTATIC, "test/Utils",
                        "staticMethod", "()Ljava/lang/Object;", false);
                mv.visitInsn(ARETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLocalVariable("this", "Ltest/Main;", null,
                        l0, l1, 0);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(0, "getStaticField", "()Ljava/lang/Object;", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(28, l0);
                mv.visitFieldInsn(GETSTATIC, "test/Utils",
                        "staticField", "Ljava/lang/Object;");
                mv.visitInsn(ARETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLocalVariable("this", "Ltest/Main;", null,
                        l0, l1, 0);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] utils() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Utils",
                    null, "java/lang/Object", null);

            cw.visitSource("Utils.java", null);

            {
                fv = cw.visitField(ACC_PUBLIC + ACC_STATIC, "staticField", "Ljava/lang/Object;", null,
                        null);
                fv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(22, l0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLocalVariable("this", "Ltest/Utils;", null,
                        l0, l1, 0);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "staticMethod", "()Ljava/lang/Object;",
                        null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(24, l0);
                mv.visitTypeInsn(NEW, "java/lang/Object");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(ARETURN);
                mv.visitMaxs(2, 0);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }
    }

    public static class Reflection {

        public static byte[] main_instanceOf() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Main", null,
                    "java/lang/Object", null);

            cw.visitSource("Main.java", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(22, l0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLocalVariable("this", "Ltest/Main;", null, l0, l1,
                        0);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "main", "(Ljava/lang/Object;)Z", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(24, l0);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitTypeInsn(INSTANCEOF, "test/Foo");
                mv.visitInsn(IRETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLocalVariable("this", "Ltest/Main;", null, l0, l1,
                        0);
                mv.visitLocalVariable("o", "Ljava/lang/Object;", null, l0, l1, 1);
                mv.visitMaxs(1, 2);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] main_classLiteral() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Main", null,
                    "java/lang/Object", null);

            cw.visitSource("Main.java", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLocalVariable("this", "Ltest/Main;", null, l0, l1,
                        0);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(0, "main", "()Ljava/lang/Object;", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLdcInsn(Type.getType("Ltest/Foo;"));
                mv.visitInsn(ARETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLocalVariable("this", "Ltest/Main;", null, l0, l1,
                        0);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }
    }

    public static class TryCatch {
        public static byte[] main() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Main",
                    null, "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(0, "main", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                Label l1 = new Label();
                Label l2 = new Label();
                mv.visitTryCatchBlock(l0, l1, l2,
                        "test/CustomException");
                mv.visitLabel(l0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/Main",
                        "helper", "()V", false);
                mv.visitLabel(l1);
                Label l3 = new Label();
                mv.visitJumpInsn(GOTO, l3);
                mv.visitLabel(l2);
                mv.visitVarInsn(ASTORE, 1);
                mv.visitInsn(RETURN);
                mv.visitLabel(l3);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 2);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(0, "helper", "()V", null, null);
                mv.visitCode();
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] customException() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_SUPER, "test/CustomException", null,
                    "java/lang/RuntimeException", null);

            {
                mv = cw.visitMethod(0, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] main_tryFinally() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "testclasses/Main",
                    null, "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(0, "main", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                Label l1 = new Label();
                Label l2 = new Label();
                mv.visitTryCatchBlock(l0, l1, l2, null);
                Label l3 = new Label();
                mv.visitTryCatchBlock(l2, l3, l2, null);
                mv.visitLabel(l0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKEVIRTUAL, "testclasses/Main",
                        "helper", "()V", false);
                mv.visitLabel(l1);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKEVIRTUAL, "testclasses/Main",
                        "helper", "()V", false);
                Label l4 = new Label();
                mv.visitJumpInsn(GOTO, l4);
                mv.visitLabel(l2);
                mv.visitVarInsn(ASTORE, 1);
                mv.visitLabel(l3);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKEVIRTUAL, "testclasses/Main",
                        "helper", "()V", false);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitInsn(ATHROW);
                mv.visitLabel(l4);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 2);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(0, "helper", "()V", null, null);
                mv.visitCode();
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }
    }

    public static class AbstractClasses {
        public static byte[] myInterface() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_ABSTRACT + ACC_INTERFACE,
                    "test/MyInterface", null, "java/lang/Object", null);

            cw.visitSource("MyInterface.java", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_ABSTRACT, "m", "()V", null, null);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] abstractImpl() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER + ACC_ABSTRACT,
                    "test/AbstractImpl", null, "java/lang/Object",
                    new String[]{"test/MyInterface"});

            cw.visitSource("AbstractImpl.java", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(22, l0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLocalVariable("this", "Ltest/AbstractImpl;", null,
                        l0, l1, 0);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "helper", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(24, l0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/AbstractImpl", "m",
                        "()V", false);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLineNumber(25, l1);
                mv.visitInsn(RETURN);
                Label l2 = new Label();
                mv.visitLabel(l2);
                mv.visitLocalVariable("this", "Ltest/AbstractImpl;", null,
                        l0, l2, 0);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] realImpl() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/RealImpl", null,
                    "test/AbstractImpl", null);

            cw.visitSource("RealImpl.java", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(19, l0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "test/AbstractImpl",
                        "<init>", "()V", false);
                mv.visitInsn(RETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLocalVariable("this", "Ltest/RealImpl;", null, l0,
                        l1, 0);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "m", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(22, l0);
                mv.visitInsn(RETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLocalVariable("this", "Ltest/RealImpl;", null, l0,
                        l1, 0);
                mv.visitMaxs(0, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "main", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitLineNumber(25, l0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/RealImpl",
                        "helper", "()V", false);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitLineNumber(26, l1);
                mv.visitInsn(RETURN);
                Label l2 = new Label();
                mv.visitLabel(l2);
                mv.visitLocalVariable("this", "Ltest/RealImpl;", null, l0,
                        l2, 0);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }
    }

    public static byte[] emptyClass(String name) throws Exception {

        ClassWriter cw = new ClassWriter(0);
        FieldVisitor fv;
        MethodVisitor mv;
        AnnotationVisitor av0;

        cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/" + name, null,
                "java/lang/Object", null);

        {
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        cw.visitEnd();

        return cw.toByteArray();
    }
}
