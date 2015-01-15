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
package com.android.build.gradle.internal.tasks
import com.android.build.gradle.internal.LoggerWrapper
import com.android.builder.core.AndroidBuilder
import com.android.sdklib.BuildToolInfo
import com.android.utils.ILogger
import org.gradle.api.DefaultTask

public abstract class BaseTask extends DefaultTask {

    AndroidBuilder androidBuilder

    private ILogger iLogger

    protected AndroidBuilder getBuilder() {
        return androidBuilder
    }

    protected getILogger() {
        if (iLogger == null) {
            iLogger = new LoggerWrapper(getLogger())
        }
        return iLogger
    }

    protected void emptyFolder(File folder) {
        logger.info("deleteDir(" + folder + ") returned: " + folder.deleteDir());
        folder.mkdirs()
    }

    protected BuildToolInfo getBuildTools() {
        androidBuilder.getTargetInfo().getBuildTools()
    }
}
