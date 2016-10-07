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

package com.android.build.gradle.internal.variant;

/**
 * Describes ways in which splits can be packaged.
 */
public enum SplitHandlingPolicy {
    /**
     * Any release before L will create fake splits where each split will be the entire
     * application with the split specific resources.
     */
    PRE_21_POLICY,

    /**
     * Android L and after, the splits are pure splits where splits only contain resources
     * specific to the split characteristics.
     */
    RELEASE_21_AND_AFTER_POLICY
}
