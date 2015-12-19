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

package com.android.build.gradle.internal.transforms;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.VariantScope;

import java.io.File;

/**
 * Expected dex file use.
 */
public enum InstantRunBuildType {
    /**
     * dex file will contain files that can be used to reload classes in a running application.
     */
    RELOAD {
        @NonNull
        @Override
        File getOutputFolder(VariantScope variantScope) {
            return variantScope.getReloadDexOutputFolder();
        }
    },
    /**
     * dex file will contain the delta files (from the last incremental build) that can be used
     * to restart the application
     */
    RESTART {
        @NonNull
        @Override
        File getOutputFolder(VariantScope variantScope) {
            return variantScope.getRestartDexOutputFolder();
        }
    };

    @NonNull
    abstract File getOutputFolder(VariantScope variantScope);

    @NonNull
    public File getIncrementalChangesFile(VariantScope variantScope) {
        return new File(variantScope.getInstantRunSupportDir(),
                name().toLowerCase() + "-changes.txt");
    }
}
