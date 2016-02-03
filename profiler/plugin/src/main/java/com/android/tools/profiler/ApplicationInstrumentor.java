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

import org.objectweb.asm.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Instrument an app's custom Application class, adding profiler hooks in its onCreate method.
 *
 * This works by finding a custom Application in a user's project and, if found, injecting
 * supportlib's {@code ProfilerApplication} into its inheritance hierarchy. In other words,
 * converting this:
 *
 * {@code MyApplication extends Application}
 *
 * to
 *
 * {@code MyApplication extends ProfilerApplication extends Application}
 *
 * TODO: If a project has no Application in it already, put a ProfilerApplication in there.
 */
public final class ApplicationInstrumentor {

    private static final String ANDROID_APPLICATION_CLASSNAME = "android/app/Application";
    private static final String PROFILER_APPLICATION_CLASSNAME
            = "com/android/tools/profiler/support/ProfilerApplication";

    public static void instrument(File activityClass, File outputFile) throws IOException {

        ClassWriter cw = new ClassWriter(0);
        ClassAdapter ca = new ClassAdapter(cw);

        FileInputStream fis = new FileInputStream(activityClass);
        try {
            ClassReader cr = new ClassReader(fis);
            cr.accept(ca, 0);
        } finally {
            fis.close();
        }

        FileOutputStream fos = new FileOutputStream(outputFile);
        try {
            fos.write(cw.toByteArray());
        } finally {
            fos.close();
        }

        // Uncomment to output ASM for the output file
        //{
        //    int flags = ClassReader.SKIP_DEBUG;
        //    System.out.println("TRACING " + outputFile);
        //    ClassReader cr = new ClassReader(new FileInputStream(outputFile));
        //    cr.accept(new TraceClassVisitor(null, new ASMifier(), new PrintWriter(
        //            System.out)), flags);
        //}
    }

    private static final class ClassAdapter extends ClassVisitor implements Opcodes {

        public ClassAdapter(ClassVisitor classVisitor) {
            super(ASM5, classVisitor);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            if (superName.equals(ANDROID_APPLICATION_CLASSNAME)) {
                superName = PROFILER_APPLICATION_CLASSNAME;
            }

            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                String[] exceptions) {

            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            return (mv != null) ? new MethodAdapter(mv) : null;
        }
    }

    private static final class MethodAdapter extends MethodVisitor implements Opcodes {

        public MethodAdapter(MethodVisitor mv) {
            super(ASM5, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc,
                boolean itf) {
            if (owner.equals(ANDROID_APPLICATION_CLASSNAME)) {
                owner = PROFILER_APPLICATION_CLASSNAME;
            }

            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }
    }
}
