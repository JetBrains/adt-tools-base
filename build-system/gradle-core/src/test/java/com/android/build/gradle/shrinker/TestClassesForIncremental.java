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

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * TODO: Document.
 */
public class TestClassesForIncremental implements Opcodes {

    public static class Simple {
        public static byte[] main1() throws Exception {

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
                mv = cw.visitMethod(ACC_PUBLIC, "main", "()V", null, null);
                mv.visitCode();
                mv.visitTypeInsn(NEW, "test/Bbb");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Bbb", "<init>",
                        "()V", false);
                mv.visitInsn(POP);
                mv.visitTypeInsn(NEW, "test/Aaa");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Aaa", "<init>",
                        "()V", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/Aaa", "m1", "()V",
                        false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }

            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] main2() throws Exception {

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
                mv = cw.visitMethod(ACC_PUBLIC, "main", "()V", null, null);
                mv.visitCode();
                mv.visitTypeInsn(NEW, "test/Bbb");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Bbb", "<init>",
                        "()V", false);
                mv.visitInsn(POP);
                mv.visitTypeInsn(NEW, "test/Aaa");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Aaa", "<init>",
                        "()V", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/Aaa", "m2", "()V",
                        false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] main_extraMethod() throws Exception {

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
                mv = cw.visitMethod(ACC_PUBLIC, "main", "()V", null, null);
                mv.visitCode();
                mv.visitTypeInsn(NEW, "test/Bbb");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Bbb", "<init>",
                        "()V", false);
                mv.visitInsn(POP);
                mv.visitTypeInsn(NEW, "test/Aaa");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Aaa", "<init>",
                        "()V", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/Aaa", "m1", "()V",
                        false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }

            {
                mv = cw.visitMethod(ACC_PUBLIC, "extraMain", "()V", null, null);
                mv.visitCode();
                mv.visitTypeInsn(NEW, "test/Aaa");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Aaa", "<init>",
                        "()V", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/Aaa", "m1", "()V",
                        false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }

            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] main_extraField() throws Exception {

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
                mv = cw.visitMethod(ACC_PUBLIC, "main", "()V", null, null);
                mv.visitCode();
                mv.visitTypeInsn(NEW, "test/Bbb");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Bbb", "<init>",
                        "()V", false);
                mv.visitInsn(POP);
                mv.visitTypeInsn(NEW, "test/Aaa");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Aaa", "<init>",
                        "()V", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/Aaa", "m1", "()V",
                        false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }
            {
                fv = cw.visitField(ACC_PUBLIC + ACC_STATIC, "sString", "Ljava/lang/String;", null,
                        null);
                fv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] main_extraField_private() throws Exception {

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
                mv = cw.visitMethod(ACC_PUBLIC, "main", "()V", null, null);
                mv.visitCode();
                mv.visitTypeInsn(NEW, "test/Bbb");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Bbb", "<init>",
                        "()V", false);
                mv.visitInsn(POP);
                mv.visitTypeInsn(NEW, "test/Aaa");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Aaa", "<init>",
                        "()V", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/Aaa", "m1", "()V",
                        false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }
            {
                fv = cw.visitField(ACC_PRIVATE + ACC_STATIC, "sString", "Ljava/lang/String;", null,
                        null);
                fv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] aaa() throws Exception {

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
                mv = cw.visitMethod(0, "m1", "()V", null, null);
                mv.visitCode();
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(0, "m2", "()V", null, null);
                mv.visitCode();
                mv.visitInsn(RETURN);
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

        public static byte[] bbb_packagePrivateConstructor() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Bbb", null,
                    "java/lang/Object", null);

            {
                mv = cw.visitMethod(0, "<init>", "()V", null, null);
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

        public static byte[] bbb_packagePrivate() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_SUPER, "test/Bbb", null,
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

        public static byte[] bbb_serializable() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Bbb", null,
                    "java/lang/Object", new String[] {"java/io/Serializable"});

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

        public static byte[] bbb_extendsAaa() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Bbb", null,
                    "test/Aaa", null);

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
    
    public static class Cycle {
        public static byte[] main1() throws Exception {

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
                mv = cw.visitMethod(ACC_PUBLIC, "main", "()V", null, null);
                mv.visitCode();
                mv.visitTypeInsn(NEW, "test/CycleOne");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/CycleOne",
                        "<init>", "()V", false);
                mv.visitInsn(POP);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] main2() throws Exception {

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
                mv = cw.visitMethod(ACC_PUBLIC, "main", "()V", null, null);
                mv.visitCode();
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] cycleOne() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/CycleOne", null,
                    "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitTypeInsn(NEW, "test/CycleTwo");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/CycleTwo",
                        "<init>", "()V", false);
                mv.visitInsn(POP);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] cycleTwo() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/CycleTwo", null,
                    "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitTypeInsn(NEW, "test/CycleOne");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/CycleOne",
                        "<init>", "()V", false);
                mv.visitInsn(POP);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }
    }
}
