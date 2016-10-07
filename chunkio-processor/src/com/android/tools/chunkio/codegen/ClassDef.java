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

package com.android.tools.chunkio.codegen;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.Modifier;

public final class ClassDef {
    private final String name;
    private final List<MethodDef> methods;
    private final Set<Modifier> modifiers;

    private ClassDef(Builder builder) {
        name = builder.name;
        methods = Utils.immutableCopy(builder.methods);
        modifiers = Utils.immutableCopy(builder.modifiers);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    void emit(CodeGenerator generator) throws IOException {
        generator.emitModifiers(modifiers);
        generator.emit("class $L", name);
        generator.emit(" {\n");
        generator.indent();

        boolean needNewLine = false;
        for (MethodDef method : methods) {
            if (needNewLine) generator.emit("\n");
            method.emit(generator);
            needNewLine = true;
        }

        generator.unindent();
        generator.emit("}\n");
    }

    public static final class Builder {
        private final String name;
        private final List<MethodDef> methods = new ArrayList<>();
        private Set<Modifier> modifiers;

        private Builder(String name) {
            this.name = name;
        }

        public Builder addMethod(MethodDef method) {
            methods.add(method);
            return this;
        }

        public Builder modifiers(EnumSet<Modifier> modifiers) {
            this.modifiers = EnumSet.copyOf(modifiers);
            return this;
        }

        public ClassDef build() {
            return new ClassDef(this);
        }
    }
}
