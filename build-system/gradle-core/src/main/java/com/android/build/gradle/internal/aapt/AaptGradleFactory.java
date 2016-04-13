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

package com.android.build.gradle.internal.aapt;

import com.android.annotations.NonNull;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.internal.aapt.Aapt;
import com.android.builder.internal.aapt.v1.AaptV1;
import com.android.builder.sdk.TargetInfo;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.google.common.base.Preconditions;

/**
 * Factory that creates instances of {@link com.android.builder.internal.aapt.Aapt} by looking
 * at project configuration.
 */
public final class AaptGradleFactory {

    private AaptGradleFactory() {}

    /**
     * Creates a new {@link Aapt} instance based on project configuration.
     *
     * @param builder the android builder project model
     * @return the newly-created instance
     */
    @NonNull
    public static Aapt make(@NonNull AndroidBuilder builder) {
        return make(builder, new LoggedProcessOutputHandler(builder.getLogger()));
    }

    /**
     * Creates a new {@link Aapt} instance based on project configuration.
     *
     * @param builder the android builder project model
     * @param outputHandler the output handler to use
     * @return the newly-created instance
     */
    @NonNull
    public static Aapt make(@NonNull AndroidBuilder builder,
            @NonNull ProcessOutputHandler outputHandler) {
        TargetInfo target = builder.getTargetInfo();
        Preconditions.checkNotNull(target, "target == null");
        BuildToolInfo buildTools = target.getBuildTools();

        return new AaptV1(builder.getProcessExecutor(), outputHandler, buildTools);
    }
}
