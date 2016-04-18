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

package com.android.build.gradle.internal.publishing;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.tasks.FileSupplier;

/**
 * Custom implementation of PublishArtifact for published manifest files.
 */
public class ManifestPublishArtifact extends BasePublishArtifact {
    /**
     * Creates new instance with specified name and {@link FileSupplier} that will provide
     * the manifest file for the artifact.
     */
    public ManifestPublishArtifact(@NonNull String name, @NonNull FileSupplier outputFileSupplier) {
        super(name, null /*classifier*/, outputFileSupplier);
    }

    @Override
    @NonNull
    public String getExtension() {
        return "xml";
    }

    @Override
    @NonNull
    public String getType() {
        return "manifest";
    }
}
