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
import java.util.Set;

// The codegen API was inspired from JavaPoet (https://github.com/square/javapoet)
// JavaPoet requires Java 7 and we need to support Java 6
// This package contains a subset of JavaPoet's capabilities but uses similar APIs
// and similar substitution syntax ($L, $T, etc.)
public final class JavaFile {
    private static final NullAppendable NULL_APPENDABLE = new NullAppendable();

    private final String mPackageName;
    private final ClassDef mClass;
    private final String mIndent;

    private JavaFile(Builder builder) {
        mPackageName = builder.mPackageName;
        mClass = builder.mClass;
        mIndent = builder.mIndent;
    }

    public void emit(Appendable out) throws IOException {
        CodeGenerator generator = new CodeGenerator(NULL_APPENDABLE, "");
        emit(generator);
        generator = new CodeGenerator(out, mIndent, generator.getImportCandidates());
        emit(generator);
    }

    private void emit(CodeGenerator generator) throws IOException {
        if (!mPackageName.isEmpty()) {
            generator.emit("package $L;\n\n", mPackageName);
            generator.setPackage(mPackageName);
        }

        Set<String> imports = generator.getImports();
        if (imports.size() > 0) {
            for (String imported : imports) {
                generator.emit("import $L;\n", imported);
            }
            generator.emit("\n");
        }

        mClass.emit(generator);

        generator.setPackage(null);
    }

    public static Builder builder(String packageName, ClassDef classDef) {
        return new Builder(packageName, classDef);
    }

    public static final class Builder {
        private final String mPackageName;
        private final ClassDef mClass;
        private String mIndent = "    ";

        private Builder(String packageName, ClassDef classDef) {
            mPackageName = packageName;
            mClass = classDef;
        }

        public JavaFile build() {
            return new JavaFile(this);
        }
    }

    private static class NullAppendable implements Appendable {
        @Override
        public Appendable append(CharSequence charSequence) throws IOException {
            return this;
        }

        @Override
        public Appendable append(CharSequence charSequence, int i, int i1) throws IOException {
            return this;
        }

        @Override
        public Appendable append(char c) throws IOException {
            return this;
        }
    }
}
