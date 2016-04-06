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

import com.android.annotations.NonNull;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;

/**
 * A deconstructed constructor, split up in the parts mentioned above.
 */
class Constructor {

    /**
     * The class this constructor belongs to.
     */
    @NonNull
    public String owner;

    /**
     * The sequence of instructions up to, but not including loadThis.
     * These instructions cannot be hot swapped.
     */
    @NonNull
    public final List<AbstractInsnNode> prelude;

    /**
     * The last LOAD_0 instruction of the original code, before the call to the delegated
     * constructor.
     */
    @NonNull
    public final VarInsnNode loadThis;

    /**
     * Line number of LOAD_0. Used to set the line number in the generated constructor call
     * so that a break point may be set at this(...) or super(...)
     */
    public final int lineForLoad;

    /**
     * The "args" part of the constructor. Described above.
     */
    @NonNull
    public final MethodNode args;

    /**
     * The INVOKESPECIAL instruction of the original code that calls the delegation.
     */
    @NonNull
    public final MethodInsnNode delegation;

    /**
     * A copy of the body of the constructor.
     */
    @NonNull
    public final MethodNode body;

    /**
     * The local variable order.
     */
    @NonNull
    public final List<LocalVariable> variables;

    /**
     * The number of local variables seen at the last loadThis.
     */
    public final int localsAtLoadThis;

    Constructor(@NonNull String owner,
            @NonNull List<AbstractInsnNode> prelude,
            @NonNull VarInsnNode loadThis,
            int lineForLoad,
            @NonNull MethodNode args,
            @NonNull MethodInsnNode delegation,
            @NonNull MethodNode body,
            @NonNull List<LocalVariable> variables,
            int localsAtLoadThis) {
        this.owner = owner;
        this.prelude = prelude;
        this.loadThis = loadThis;
        this.lineForLoad = lineForLoad;
        this.args = args;
        this.delegation = delegation;
        this.body = body;
        this.variables = variables;
        this.localsAtLoadThis = localsAtLoadThis;
    }
}
