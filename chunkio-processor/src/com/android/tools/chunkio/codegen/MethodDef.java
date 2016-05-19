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
    private final String mName;
    private final TypeDef mReturnType;
    private final Set<Modifier> mModifiers;
    private final List<ParameterDef> mParameters;
    private final List<TypeDef> mExceptions;
    private final CodeSnippet mBody;

    private MethodDef(Builder builder) {
        mName = builder.mName;
        mReturnType = builder.mReturnType;
        mModifiers = Utils.immutableCopy(builder.mModifiers);
        mParameters = Utils.immutableCopy(builder.mParameters);
        mExceptions = Utils.immutableCopy(builder.mExceptions);
        mBody = builder.mBody.build();
    }

    String getName() {
        return mName;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    void emit(CodeGenerator generator) throws IOException {
        generator.emitModifiers(mModifiers);
        generator.emit("$T $L(", mReturnType, mName);

        boolean firstParam = true;
        for (ParameterDef parameter : mParameters) {
            if (!firstParam) {
                generator.emit(", ");
            }
            firstParam = false;
            parameter.emit(generator);
        }

        generator.emit(")");

        if (!mExceptions.isEmpty()) {
            generator.emit(" throws ");
            boolean firstException = true;
            for (TypeDef exception : mExceptions) {
                if (!firstException) {
                    generator.emit(", ");
                }
                firstException = false;
                generator.emit("$T", exception);
            }
        }

        generator.emit(" {\n");
        generator.indent();

        generator.emit(mBody);

        generator.unindent();
        generator.emit("}\n");
    }

    public static final class Builder {
        private final String mName;
        private TypeDef mReturnType;
        private Set<Modifier> mModifiers;
        private final List<ParameterDef> mParameters = new ArrayList<>();
        private final List<TypeDef> mExceptions = new ArrayList<>();

        private final CodeSnippet.Builder mBody = CodeSnippet.builder();

        private Builder(String name) {
            mName = name;
        }

        public Builder modifiers(EnumSet<Modifier> modifiers) {
            mModifiers = EnumSet.copyOf(modifiers);
            return this;
        }

        public Builder addParameter(Type type, String name) {
            return addParameter(type, name, EnumSet.noneOf(Modifier.class));
        }

        public Builder addParameter(Type type, String name, EnumSet<Modifier> modifiers) {
            mParameters.add(ParameterDef.builder(type, name, modifiers).build());
            return this;
        }

        public Builder throwsException(Type... exceptions) {
            for (Type exception : exceptions) {
                mExceptions.add(TypeDef.of(exception));
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
            mReturnType = type;
            return this;
        }

        public Builder add(CodeSnippet snippet) {
            mBody.add(snippet);
            return this;
        }

        public Builder add(String format, Object... args) {
            mBody.add(format, args);
            return this;
        }

        public Builder addStatement(String format, Object... args) {
            mBody.addStatement(format, args);
            return this;
        }

        public Builder beginControlStatement(String format, Object... args) {
            mBody.beginControlStatement(format, args);
            return this;
        }

        public Builder continueControlStatement(String format, Object... args) {
            mBody.continueControlStatement(format, args);
            return this;
        }

        public Builder endControlStatement() {
            mBody.endControlStatement();
            return this;
        }

        public Builder beginBlock() {
            mBody.beginBlock();
            return this;
        }

        public Builder endBlock() {
            mBody.endBlock();
            return this;
        }

        public MethodDef build() {
            return new MethodDef(this);
        }
    }
}
