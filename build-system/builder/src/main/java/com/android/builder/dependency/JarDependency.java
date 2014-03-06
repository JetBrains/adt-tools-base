/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.builder.dependency;

import com.android.annotations.NonNull;

import java.io.File;

/**
 * Represents a Jar dependency. This could be the output of a Java project.
 */
public class JarDependency {

    private final File mJarFile;
    private final boolean mCompiled;
    private boolean mPackaged;
    private final boolean mProguarded;

    public JarDependency(@NonNull File jarFile, boolean compiled, boolean packaged,
                         boolean proguarded) {
        mJarFile = jarFile;
        mCompiled = compiled;
        mPackaged = packaged;
        mProguarded = proguarded;
    }


    public JarDependency(@NonNull File jarFile, boolean compiled, boolean packaged) {
        this(jarFile, compiled, packaged, true);
    }

    @NonNull
    public File getJarFile() {
        return mJarFile;
    }

    public boolean isCompiled() {
        return mCompiled;
    }

    public boolean isPackaged() {
        return mPackaged;
    }

    public void setPackaged(boolean packaged) {
        mPackaged = packaged;
    }

    public boolean isProguarded() {
        return mProguarded;
    }

    @Override
    public String toString() {
        return "JarDependency{" +
                "mJarFile=" + mJarFile +
                ", mCompiled=" + mCompiled +
                ", mPackaged=" + mPackaged +
                ", mProguarded=" + mProguarded +
                '}';
    }
}
