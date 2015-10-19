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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.List;
import java.util.Set;

/**
 * {@link ClassVisitor} that skips class members which are not reachable. It also filters the list
 * of implemented interfaces.
 */
public class FilterMembersVisitor extends ClassVisitor {
    private final Set<String> mMembers;
    private final Predicate<String> mKeepInterface;

    public FilterMembersVisitor(Set<String> members, Predicate<String> keepInterface, ClassVisitor cv) {
        super(Opcodes.ASM5, cv);
        mMembers = members;
        mKeepInterface = keepInterface;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName,
            String[] interfaces) {
        List<String> interfacesToKeep = Lists.newArrayList();
        for (String iface : interfaces) {
            if (mKeepInterface.apply(iface)) {
                interfacesToKeep.add(iface);
            }
        }

        super.visit(version, access, name, signature, superName, Iterables.toArray(interfacesToKeep, String.class));
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature,
            Object value) {
        if (mMembers.contains(name + ":" + desc)) {
            return super.visitField(access, name, desc, signature, value);
        } else {
            return null;
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
            String[] exceptions) {
        if (mMembers.contains(name + ":" + desc)) {
            return super.visitMethod(access, name, desc, signature, exceptions);
        } else {
            return null;
        }
    }
}
