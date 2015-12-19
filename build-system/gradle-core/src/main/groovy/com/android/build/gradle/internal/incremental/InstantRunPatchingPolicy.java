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

package com.android.build.gradle.internal.incremental;

import com.android.annotations.NonNull;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.builder.model.AndroidProject;
import com.android.sdklib.AndroidVersion;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

/**
 * Patching policy for delivering incremental code changes and triggering a cold start (application
 * restart).
 */
public enum InstantRunPatchingPolicy {

    /**
     * For Dalvik, a patch dex file will be generated with the incremental changes from the last
     * non incremental build or the last build that contained changes identified by the verifier as
     * incompatible.
     */
    PRE_LOLLIPOP,

    /**
     * For Lollipop, the application will be split in shards of dex files upon initial build and
     * packaged as a native multi dex application. Each incremental changes will trigger rebuilding
     * the affected shard dex files. Such dex files will be pushed on the device using the
     * embedded micro-server and installed by it.
     */
    LOLLIPOP,

    /**
     * For M and above, each shard dex file described above will be packaged in a single pure
     * split APK that will be pushed and installed on the device using adb install-multiple
     * commands.
     */
    MARSHMALLOW_AND_ABOVE;

    /**
     * Returns the patching policy following the {@link AndroidProject#PROPERTY_BUILD_API} value
     * passed by Android Studio.
     * @param version the {@link AndroidVersion}
     * @return a {@link InstantRunPatchingPolicy} instance.
     */
    @NonNull
    public static InstantRunPatchingPolicy getPatchingPolicy(@NonNull AndroidVersion version) {
        if (version.getApiLevel() < 21) {
            return PRE_LOLLIPOP;
        } else if (version.getApiLevel() < 23) {
            return LOLLIPOP;
        } else {
            return MARSHMALLOW_AND_ABOVE;
        }
    }

    /**
     * Returns the {@link AndroidVersion} for the target device.
     * @param logger logger to log failures
     * @param project the project being built
     * @return a {@link AndroidVersion} for the targeted device, following the
     * {@link AndroidProject#PROPERTY_BUILD_API} value passed by Android Studio.
     */
    @NonNull
    public static AndroidVersion getApiLevel(@NonNull Logger logger, @NonNull Project project) {
        String apiVersion = AndroidGradleOptions.getBuildTargetApi(project);
        AndroidVersion version = AndroidVersion.DEFAULT;
        if (apiVersion != null) {
            try {
                version = new AndroidVersion(apiVersion);
            } catch (AndroidVersion.AndroidVersionException e) {
                logger.warn("Wrong build target version passed ", e);
            }
        }
        if (logger.isQuietEnabled()) {
            logger.info(String.format("InstantRun: apiLevel set to %d", version.getApiLevel()));
        }
        return version;
    }
}
