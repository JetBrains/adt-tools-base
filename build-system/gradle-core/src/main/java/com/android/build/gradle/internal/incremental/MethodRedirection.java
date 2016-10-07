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

import com.android.annotations.NonNull;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.LabelNode;

import java.util.List;

public class MethodRedirection extends Redirection {

    /**
     * The name of the method we redirect to.
     */
    @NonNull
    private final String name;

    MethodRedirection(@NonNull LabelNode label, @NonNull String name, @NonNull List<Type> types, @NonNull Type type) {
        super(label, types, type);
        this.name = name;
    }

    @Override
    protected void doRedirect(@NonNull GeneratorAdapter mv, int change) {
        // Push the three arguments
        mv.loadLocal(change);
        mv.push(name);
        ByteCodeUtils.newVariableArray(mv, ByteCodeUtils.toLocalVariables(types));

        // now invoke the generic dispatch method.
        mv.invokeInterface(IncrementalVisitor.CHANGE_TYPE, Method.getMethod("Object access$dispatch(String, Object[])"));
    }
}
