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
import java.lang.reflect.Type;
import java.util.EnumSet;
import java.util.Set;

import javax.lang.model.element.Modifier;

public final class ParameterDef {
    private final String name;
    private final TypeDef type;
    private final Set<Modifier> modifiers;

    private ParameterDef(Builder builder) {
        name = builder.name;
        type = builder.type;
        modifiers = Utils.immutableCopy(builder.modifiers);
    }

    public static Builder builder(Type type, String name, EnumSet<Modifier> modifiers) {
        return new Builder(type, name).modifiers(modifiers);
    }

    public static Builder builder(TypeDef type, String name, EnumSet<Modifier> modifiers) {
        return new Builder(type, name).modifiers(modifiers);
    }

    void emit(CodeGenerator generator) throws IOException {
        generator.emitModifiers(modifiers);
        generator.emit("$T $L", type, name);
    }

    public static final class Builder {
        private final String name;
        private TypeDef type;
        private Set<Modifier> modifiers;

        private Builder(Type type, String name) {
            this.name = name;
            this.type = TypeDef.of(type);
        }

        private Builder(TypeDef type, String name) {
            this.type = type;
            this.name = name;
        }

        private Builder modifiers(EnumSet<Modifier> modifiers) {
            this.modifiers = EnumSet.copyOf(modifiers);
            return this;
        }

        ParameterDef build() {
            return new ParameterDef(this);
        }
    }
}
