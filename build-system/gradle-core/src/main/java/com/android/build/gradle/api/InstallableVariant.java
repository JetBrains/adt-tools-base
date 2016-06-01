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

package com.android.build.gradle.api;

import com.android.annotations.Nullable;

import org.gradle.api.DefaultTask;

/**
 * A Build variant that supports installation.
 */
public interface InstallableVariant {

    /**
     * Returns the install task for the variant.
     */
    @Nullable
    DefaultTask getInstall();

    /**
     * Returns the uninstallation task.
     *
     * For non-library project this is always true even if the APK is not created because
     * signing isn't setup.
     */
    @Nullable
    DefaultTask getUninstall();
}
