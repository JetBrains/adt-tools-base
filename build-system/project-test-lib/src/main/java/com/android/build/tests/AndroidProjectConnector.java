/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.tests;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidProject;
import com.android.io.StreamException;
import com.google.common.collect.Lists;

import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 */
public class AndroidProjectConnector {

    @NonNull
    private final File mSdkDir;

    @Nullable
    private final File mNdkDir;

    public AndroidProjectConnector(@NonNull File sdkDir, @Nullable File ndkDir) {
        mSdkDir = sdkDir;
        mNdkDir = ndkDir;
    }

    public void runGradleTasks(
            @NonNull File project,
            @NonNull String gradleVersion,
            @NonNull List<String> arguments,
            @NonNull Map<String, String> jvmDefines,
            @NonNull String... tasks) throws IOException, StreamException {
        File localProp = createLocalProp(project);

        try {
            GradleConnector connector = GradleConnector.newConnector();

            ProjectConnection connection = connector
                    .useGradleVersion(gradleVersion)
                    .forProjectDirectory(project)
                    .connect();
            try {
                List<String> args = Lists.newArrayListWithCapacity(2 + arguments.size());
                args.add("-i");
                args.add("-u");
                args.addAll(arguments);

                BuildLauncher build = connection.newBuild().forTasks(tasks).withArguments(
                        args.toArray(new String[args.size()]));

                if (!jvmDefines.isEmpty()) {
                    String[] jvmArgs = new String[jvmDefines.size()];
                    int index = 0;
                    for (Map.Entry<String, String> entry : jvmDefines.entrySet()) {
                        jvmArgs[index++] = "-D" + entry.getKey() + "=" + entry.getValue();
                    }

                    build.setJvmArguments(jvmArgs);
                }

                build.run();
            } finally {
                connection.close();
            }
        } finally {
            localProp.delete();
        }
    }

    public AndroidProject getModel(@NonNull File project) {
        // Configure the connector and create the connection
        GradleConnector connector = GradleConnector.newConnector();

        connector.forProjectDirectory(project);

        ProjectConnection connection = connector.connect();
        try {
            // Load the custom model for the project
            return connection.getModel(AndroidProject.class);
        } finally {
            connection.close();
        }
    }

    private File createLocalProp(@NonNull File project) throws IOException, StreamException {
        Properties p = new Properties();
        p.put("sdk.dir", mSdkDir.getAbsolutePath());
        if (mNdkDir != null) {
            p.put("ndk.dir", mNdkDir.getAbsolutePath());
        }

        File file = new File(project, "local.properties");
        p.store(new FileOutputStream(file), "automatically generated local.prop. Should be removed after test.");

        return file;
    }
}
