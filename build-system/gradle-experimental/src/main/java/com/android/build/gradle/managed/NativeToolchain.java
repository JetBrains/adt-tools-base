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

package com.android.build.gradle.managed;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import org.gradle.api.Named;
import org.gradle.model.Managed;

import java.io.File;

/**
 * A @Managed interface for {@link com.android.builder.model.NativeToolchain}.
 */
@Managed
public interface NativeToolchain extends com.android.builder.model.NativeToolchain, Named {
    /**
     * Returns the full path of the C compiler.
     *
     * @return the C compiler path.
     */
    @Override
    @Nullable
    File getCCompilerExecutable();
    void setCCompilerExecutable(@Nullable File exe);

    /**
     * Returns the full path of the C++ compiler.
     *
     * @return the C++ compiler path.
     */
    @Override
    @Nullable
    File getCppCompilerExecutable();
    void setCppCompilerExecutable(@Nullable File exe);
}
