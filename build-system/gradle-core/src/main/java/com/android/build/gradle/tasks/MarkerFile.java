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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.utils.FileUtils;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.logging.Logger;

/**
 * Marker file that can be used by tasks to communicate. The marker file will contain a command.
 */
public class MarkerFile {

    public enum Command {
        RUN,
        BLOCK
    }

    public static void createMarkerFile(@NonNull File markerFile, @NonNull Command command)
            throws IOException {
        if (markerFile.exists()) {
            markerFile.delete();
        }
        FileUtils.createFile(markerFile, command.name());
    }

    @NonNull
    public static Command readMarkerFile(@NonNull File markerFile) throws IOException {
        String line = Files.readFirstLine(markerFile, Charset.defaultCharset());
        return Command.valueOf(line);
    }
}
