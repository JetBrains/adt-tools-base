/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.buildsrc

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact

abstract class BaseTask extends DefaultTask {

    public static boolean isLocalArtifact(ModuleVersionIdentifier id) {
        return id.group == "base" || id.group == "swt"
    }

    public static boolean isAndroidArtifact(ModuleVersionIdentifier id) {
        return id.group.startsWith("com.android.tools") && !id.group.startsWith("com.android.tools.external")
    }

    public static boolean isValidArtifactType(ResolvedArtifact artifact) {
        return artifact.type.equals("jar") || artifact.type.equals("bundle")
    }
}
