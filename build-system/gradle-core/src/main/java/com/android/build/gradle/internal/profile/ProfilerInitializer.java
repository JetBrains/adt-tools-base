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

package com.android.build.gradle.internal.profile;

import static com.android.utils.NullLogger.getLogger;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.LibraryCache;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.builder.internal.compiler.JackConversionCache;
import com.android.builder.internal.compiler.PreDexCache;
import com.android.builder.profile.AsyncRecorder;
import com.android.builder.profile.ProcessRecorderFactory;
import com.android.builder.profile.Recorder;
import com.android.builder.profile.ThreadRecorder;
import com.android.dx.command.dexer.Main;
import com.android.ide.common.internal.ExecutorSingleton;

import org.gradle.BuildAdapter;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;

import java.io.File;

/**
 * Initialize the {@link ProcessRecorderFactory} using a given project.
 *
 * <p>Is separate from {@code ProcessRecorderFactory} as {@code ProcessRecorderFactory} does not
 * depend on gradle classes.
 */
public final class ProfilerInitializer {

    private static final Object sLOCK = new Object();

    @Nullable
    private static RecordingBuildListener sRecordingBuildListener;

    private ProfilerInitializer() {
        //Static singleton class.
    }

    /**
     * Initialize the {@link ProcessRecorderFactory}. Idempotent.
     *
     * @param project the current Gradle {@link Project}.
     */
    public static void init(@NonNull Project project) {
        ProcessRecorderFactory.initialize(
                project.getGradle().getGradleVersion(),
                new LoggerWrapper(project.getLogger()),
                project.getRootProject().file("profiler" + System.currentTimeMillis() + ".json"));
        synchronized (sLOCK) {
            if (sRecordingBuildListener == null) {
                sRecordingBuildListener = new RecordingBuildListener(AsyncRecorder.get());
                project.getGradle().addListener(sRecordingBuildListener);
            }
        }

        project.getGradle().addBuildListener(new BuildAdapter() {
            @Override
            public void buildFinished(@NonNull BuildResult buildResult) {
                try {
                    synchronized (sLOCK) {
                        if (sRecordingBuildListener != null) {
                            project.getGradle().removeListener(sRecordingBuildListener);
                            sRecordingBuildListener = null;
                        }
                    }
                    ProcessRecorderFactory.shutdown();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }
}
