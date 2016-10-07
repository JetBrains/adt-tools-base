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

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Wraps all creations of HTTPUrlConnection's with our own version.
 */
final class NetworkingAdapter extends ClassVisitor implements Opcodes {

    static final String URL_CLASS = "java/net/URL";
    static final String WRAPPER_CLASS = "com/android/tools/profiler/support/network/HttpWrapper";

    NetworkingAdapter(ClassVisitor classVisitor) {
        super(ASM5, classVisitor);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
            String[] exceptions) {

        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        return (mv != null) ? new MethodAdapter(mv) : null;
    }

    private static final class MethodAdapter extends MethodVisitor implements Opcodes {

        public MethodAdapter(MethodVisitor mv) {
            super(ASM5, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc,
                boolean itf) {

            if (opcode != INVOKEVIRTUAL || !owner.equals(URL_CLASS)) {
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                return;
            }
            assert !itf;
            if (name.equals("openConnection") && desc.equals("()Ljava/net/URLConnection;")) {
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                invoke("wrapURLConnection", "(Ljava/net/URLConnection;)Ljava/net/URLConnection;");
            } else if (name.equals("openConnection") && desc.equals("(Ljava/net/Proxy;)Ljava/net/URLConnection;")) {
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                invoke("wrapURLConnection", "(Ljava/net/URLConnection;)Ljava/net/URLConnection;");
            } else if (name.equals("openStream") && desc.equals("()Ljava/io/InputStream;")) {
                invoke("wrapOpenStream", "(Ljava/net/URL;)Ljava/io/InputStream;");
            } else if (name.equals("getContent") && desc.equals("()Ljava/lang/Object;")) {
                invoke("wrapGetContent", "(Ljava/net/URL;)Ljava/lang/Object;");
            } else if (name.equals("getContent") && desc.equals("([Ljava/lang/Class;)Ljava/lang/Object;")) {
                invoke("wrapGetContent", "(Ljava/net/URL;[Ljava/lang/Class;)Ljava/lang/Object;");
            } else {
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }
        }

        /**
         * Invokes a static method on our wrapper class.
         */
        private void invoke(String method, String desc) {
            super.visitMethodInsn(INVOKESTATIC, WRAPPER_CLASS, method, desc, false);
        }
    }
}
