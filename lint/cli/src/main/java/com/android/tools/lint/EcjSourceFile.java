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

package com.android.tools.lint;

import static com.android.SdkConstants.UTF_8;

import com.android.annotations.NonNull;

import org.eclipse.jdt.internal.compiler.batch.CompilationUnit;

import java.io.File;

/**
 * Source file for ECJ. Subclassed to let us hold on to the String contents (ECJ operates
 * on char[]'s exclusively, whereas for PSI we'll need Strings) and serve it back quickly.
 */
public class EcjSourceFile extends CompilationUnit {

    private String mSource;

    public EcjSourceFile(@NonNull char[] source, @NonNull File file,
            @NonNull String encoding) {
        super(source, file.getPath(), encoding);
        mSource = new String(source);
    }

    public EcjSourceFile(@NonNull String source, @NonNull File file,
            @NonNull String encoding) {
        super(source.toCharArray(), file.getPath(), encoding);
        mSource = source;
    }

    public EcjSourceFile(@NonNull String source, @NonNull File file) {
        this(source, file, UTF_8);
    }

    @NonNull
    public String getSource() {
        if (mSource == null) {
            mSource = new String(getContents());
        }
        return mSource;
    }
}
