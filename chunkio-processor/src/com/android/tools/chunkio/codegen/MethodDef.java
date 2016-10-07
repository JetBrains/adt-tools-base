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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.Modifier;

public class MethodDef {
    private final String name;
    private final TypeDef returnType;
    private final Set<Modifier> modifiers;
    private final List<ParameterDef> parameters;
    private final List<TypeDef> exceptions;
    private final CodeSnippet body;

    private MethodDef(Builder builder) {
        name = builder.name;
        returnType = builder.returnType;
        modifiers = Utils.immutableCopy(builder.modifiers);
        parameters = Utils.immutableCopy(builder.parameters);
        exceptions = Utils.immutableCopy(builder.exceptions);
        body = builder.body.build();
    }

    String getName() {
        return name;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    void emit(CodeGenerator generator) throws IOException {
        generator.emitModifiers(modifiers);
        generator.emit("$T $L(", returnType, name);

        boolean firstParam = true;
        for (ParameterDef parameter : parameters) {
            if (!firstParam) {
                generator.emit(", ");
            }
            firstParam = false;
            parameter.emit(generator);
        }

        generator.emit(")");

        if (!exceptions.isEmpty()) {
            generator.emit(" throws ");
            boolean firstException = true;
            for (TypeDef exception : exceptions) {
                if (!firstException) {
                    generator.emit(", ");
                }
                firstException = false;
                generator.emit("$T", exception);
            }
        }

        generator.emit(" {\n");
        generator.indent();

        generator.emit(body);

        generator.unindent();
        generator.emit("}\n");
    }

    public static final class Builder {
        private final String name;
        private TypeDef returnType;
        private Set<Modifier> modifiers;
        private final List<ParameterDef> parameters = new ArrayList<>();
        private final List<TypeDef> exceptions = new ArrayList<>();

        private final CodeSnippet.Builder body = CodeSnippet.builder();

        private Builder(String name) {
            this.name = name;
        }

        public Builder modifiers(EnumSet<Modifier> modifiers) {
            this.modifiers = EnumSet.copyOf(modifiers);
            return this;
        }

        public Builder addParameter(Type type, String name) {
            return addParameter(type, name, EnumSet.noneOf(Modifier.class));
        }

        public Builder addParameter(Type type, String name, EnumSet<Modifier> modifiers) {
            parameters.add(ParameterDef.builder(type, name, modifiers).build());
            return this;
        }

        public Builder throwsException(Type... exceptions) {
            for (Type exception : exceptions) {
                this.exceptions.add(TypeDef.of(exception));
            }
            return this;
        }

        public Builder returns(String packageName, String name) {
            return returns(TypeDef.fromClass(packageName, name));
        }

        public Builder returns(Type type) {
            return returns(TypeDef.of(type));
        }

        public Builder returns(TypeDef type) {
            returnType = type;
            return this;
        }

        public Builder add(CodeSnippet snippet) {
            body.add(snippet);
            return this;
        }

        public Builder add(String format, Object... args) {
            body.add(format, args);
            return this;
        }

        public Builder addStatement(String format, Object... args) {
            body.addStatement(format, args);
            return this;
        }

        public Builder beginControlStatement(String format, Object... args) {
            body.beginControlStatement(format, args);
            return this;
        }

        public Builder continueControlStatement(String format, Object... args) {
            body.continueControlStatement(format, args);
            return this;
        }

        public Builder endControlStatement() {
            body.endControlStatement();
            return this;
        }

        public Builder beginBlock() {
            body.beginBlock();
            return this;
        }

        public Builder endBlock() {
            body.endBlock();
            return this;
        }

        public MethodDef build() {
            return new MethodDef(this);
        }
    }
}
