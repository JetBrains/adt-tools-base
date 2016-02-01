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

package com.android.tools.profiler;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Add a log message at the top of an onCreate method, if found.
 *
 * This class is a proof of concept that we can instrument an Android app at compile time.
 * Eventually, this will go away and be replaced with logic that will instrument an app with classes
 * useful for profiling.
 */
public final class OnCreateInstrumentor {

    public static void instrument(File activityClass, File outputFile) throws IOException {

        ClassWriter cw = new ClassWriter(0);
        ActivityClassVisitor acv = new ActivityClassVisitor(cw);

        FileInputStream fis = new FileInputStream(activityClass);
        try {
            ClassReader cr = new ClassReader(fis);
            cr.accept(acv, 0);
        } finally {
            fis.close();
        }

        FileOutputStream fos = new FileOutputStream(outputFile);
        try {
            fos.write(cw.toByteArray());
        } finally {
            fos.close();
        }

        //// Uncomment to output ASM for the output file
        //{
        //    int flags = ClassReader.SKIP_DEBUG;
        //    System.out.println("TRACING " + outputFile);
        //    ClassReader cr = new ClassReader(new FileInputStream(outputFile));
        //    cr.accept(new TraceClassVisitor(null, new ASMifier(), new PrintWriter(
        //            System.out)), flags);
        //}
    }

    private static final class ActivityClassVisitor extends ClassVisitor implements Opcodes {

        private String mClassName;

        public ActivityClassVisitor(ClassVisitor classVisitor) {
            super(ASM5, classVisitor);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {

            String[] parts = name.split("/");
            mClassName = parts[parts.length - 1];

            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                String[] exceptions) {
            assert mClassName != null;

            MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
            if (name.equals("onCreate")) {
                return mv != null ? new OnCreateVisitor(mClassName, mv) : null;
            }
            return mv;
        }
    }

    private static final class OnCreateVisitor extends MethodVisitor implements Opcodes {

        private final String mClassName;

        public OnCreateVisitor(String className, MethodVisitor methodVisitor) {
            super(ASM5, methodVisitor);
            mClassName = className;
        }

        @Override
        public void visitCode() {
            // Log.d(TAG, "onCreate called"); // where TAG is the current class name
            mv.visitLdcInsn(mClassName);
            mv.visitLdcInsn(mClassName + "#onCreate called");
            mv.visitMethodInsn(INVOKESTATIC, "android/util/Log", "d",
                               "(Ljava/lang/String;Ljava/lang/String;)I", false);
            mv.visitInsn(POP);
            super.visitCode();
        }
    }
}
