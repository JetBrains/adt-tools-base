/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.pipeline;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.google.common.collect.Lists;

import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.OutputFiles;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * A base task with stream fields that properly use Gradle's input/output annotations to
 * return the stream's content as input/output.
 */
public class StreamBasedTask extends BaseTask {

    protected Collection<TransformStream> consumedInputStreams;
    protected Collection<TransformStream> referencedInputStreams;
    protected Collection<TransformStream> outputStreams;

    @NonNull
    @InputFiles
    public List<File> getStreamInputs() {
        List<File> inputs = Lists.newArrayList();
        for (TransformStream s : consumedInputStreams) {
            inputs.addAll(s.getFiles().get());
        }

        for (TransformStream s : referencedInputStreams) {
            inputs.addAll(s.getFiles().get());
        }

        return inputs;
    }

    @NonNull
    @OutputDirectories
    public List<File> getStreamOutputFolders() {
        List<File> outputs = Lists.newArrayList();
        for (TransformStream s : outputStreams) {
            switch (s.getFormat()) {
                case SINGLE_FOLDER:
                case MULTI_FOLDER:
                    outputs.addAll(s.getFiles().get());
                    break;
                case JAR:
                    // do nothing
                    break;
                default:
                    throw new RuntimeException("Unsupported ScopedContent.Format value: " + s.getFormat().name());
            }
        }

        return outputs;
    }

    @NonNull
    @OutputFiles
    public List<File> getStreamOutputFiles() {
        List<File> outputs = Lists.newArrayList();
        for (TransformStream s : outputStreams) {
            switch (s.getFormat()) {
                case SINGLE_FOLDER:
                case MULTI_FOLDER:
                    // do nothing
                    break;
                case JAR:
                    outputs.addAll(s.getFiles().get());
                    break;
                default:
                    throw new RuntimeException("Unsupported ScopedContent.Format value: " + s.getFormat().name());
            }
        }

        return outputs;
    }
}
