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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.tasks.FileSupplier;

import org.gradle.api.Task;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;

import proguard.ParseException;
import proguard.gradle.ProGuardTask;

/**
 * Decoration for the {@link ProGuardTask} so it implements shared interfaces with our custom
 * tasks.
 */
public class AndroidProGuardTask extends ProGuardTask implements FileSupplier {

    /**
     * resulting obfuscation mapping file.
     */
    @Nullable
    @InputFile
    @Optional
    File mappingFile;

    /**
     * if this is a test related proguard task, this will point to tested application mapping file
     * which can be absent in case the tested application did not request obfuscation.
     */
    @Nullable
    @InputFile
    @Optional
    File testedAppMappingFile;

    @Override
    public void printmapping(Object printMapping) throws ParseException {
        mappingFile = (File) printMapping;
        super.printmapping(printMapping);
    }

    @Override
    public void applymapping(Object applyMapping) throws ParseException {
        testedAppMappingFile = (File) applyMapping;
    }

    @Override
    public File get() {
        return mappingFile;
    }

    @NonNull
    @Override
    public Task getTask() {
        return this;
    }

    @Override
    @TaskAction
    public void proguard() throws ParseException, IOException {
        // only set the tested application mapping file if it exists (it must at this point or that
        // means the tested application did not request obfuscation).
        if (testedAppMappingFile != null && testedAppMappingFile.exists()) {
            super.applymapping(testedAppMappingFile);
        }
        super.proguard();
    }
}
