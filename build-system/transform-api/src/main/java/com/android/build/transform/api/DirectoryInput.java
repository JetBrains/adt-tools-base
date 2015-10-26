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

package com.android.build.transform.api;

import com.android.annotations.NonNull;
import com.google.common.annotations.Beta;

import java.io.File;
import java.util.Collection;
import java.util.Map;

/**
 * A {@link QualifiedContent} of type directory.
 * </p>
 * This means the {@link #getFile()} is the root directory containing the content.
 * </p>
 * This also contain incremental data if the transform is in incremental mode (both
 * {@link Transform#isIncremental()} must return true, and
 * {@link Transform#transform(Context, Collection, Collection, TransformOutputProvider, boolean)} must
 * have its last parameter set to true). If the transform is not in incremental mode, the
 * list is empty.
 */
@Beta
public interface DirectoryInput extends QualifiedContent {

    /**
     * Returns the changed files. This is only valid if the transform is in incremental mode.
     */
    @NonNull
    Map<File, Status> getChangedFiles();
}
