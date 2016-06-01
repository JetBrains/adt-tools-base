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

package com.android.build.gradle.internal.incremental;

import com.android.annotations.NonNull;

public enum InstantRunBuildMode {
    /**
     * Hot or warm swap
     */
    HOT_WARM,
    /**
     * Cold swap.
     *
     * <p>Can be caused by:
     * <ul>
     *     <li>A request from studio, by setting
     *     {@link com.android.builder.model.OptionalCompilationStep#RESTART_ONLY}.</li>
     *     <li>Some types of verifier failure. See {@link InstantRunVerifierStatus}.</li>
     * </ul>
     */
    COLD,
    /**
     * Full build.
     *
     * <p>Can be caused by:
     * <ul>
     *     <li>A request from studio, by setting
     *     {@link com.android.builder.model.OptionalCompilationStep#FULL_APK}.</li>
     *     <li>Some types of verifier failure. See {@link InstantRunVerifierStatus}.</li>
     * </ul>
     *
     * <p>
     */
    FULL;

    public InstantRunBuildMode combine(@NonNull InstantRunBuildMode other) {
        return values()[Math.max(ordinal(), other.ordinal())];
    }
}
