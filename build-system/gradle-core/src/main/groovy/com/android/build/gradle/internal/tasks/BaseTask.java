/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.build.gradle.internal.tasks;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.builder.core.AndroidBuilder;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;

import org.gradle.api.DefaultTask;

import java.io.File;

public abstract class BaseTask extends DefaultTask {

    @Nullable
    private AndroidBuilder androidBuilder;

    @Nullable
    private ILogger iLogger;

    @Nullable
    protected AndroidBuilder getBuilder() {
        return androidBuilder;
    }

    @NonNull
    protected ILogger getILogger() {
        if (iLogger == null) {
            iLogger = new LoggerWrapper(getLogger());
        }
        return iLogger;
    }

    protected void emptyFolder(File folder) {
        getLogger().info("deleteDir(" + folder + ") returned: " + FileUtils.deleteFolder(folder));
        folder.mkdirs();
    }

    protected BuildToolInfo getBuildTools() {
        return androidBuilder.getTargetInfo().getBuildTools();
    }

    public void setAndroidBuilder(@NonNull AndroidBuilder androidBuilder) {
        this.androidBuilder = androidBuilder;
    }
}
