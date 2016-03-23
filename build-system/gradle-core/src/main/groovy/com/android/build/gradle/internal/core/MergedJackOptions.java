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

package com.android.build.gradle.internal.core;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.dsl.CoreJackOptions;

/**
 * Implementation of CoreJackOptions used to merge multiple configs together.
 */
public class MergedJackOptions implements CoreJackOptions {
    private boolean isEnabledFlag = false;
    private boolean isJackInProcessFlag = true;

    public void merge(CoreJackOptions that) {
        if (that.isEnabled() != null) {
            isEnabledFlag = that.isEnabled();
        }
        if (that.isJackInProcess() != null) {
            isJackInProcessFlag = that.isJackInProcess();
        }
    }

    @Override
    @NonNull
    public Boolean isEnabled() {
        return isEnabledFlag;
    }

    @Override
    @NonNull
    public Boolean isJackInProcess() {
        return isJackInProcessFlag;
    }
}
