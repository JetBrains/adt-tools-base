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

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;

import proguard.ParseException;
import proguard.gradle.ProGuardTask;

/**
 * Specialization of the {@link ProGuardTask} that can use {@link Configuration} objects to retrieve
 * input files like the tested application classes and the tested application mapping file.
 */
public class TestModuleProGuardTask extends ProGuardTask {
    private Configuration mappingConfiguration;
    private Configuration classesConfiguration;


    /**
     * Sets the {@link Configuration} to later retrieve the tested application mapping file
     */
    public void setMappingConfiguration(Configuration configuration) {
        this.mappingConfiguration = configuration;
        dependsOn(configuration);
    }

    /**
     * Sets the {@link Configuration} to later retrieve the test application classes jar file.
     */
    public void setClassesConfiguration(Configuration configuration) {
        this.classesConfiguration = configuration;
        dependsOn(configuration);
    }

    @Override
    @TaskAction
    public void proguard() throws ParseException, IOException {
        if (mappingConfiguration.getFiles().isEmpty() || classesConfiguration.getFiles().isEmpty()) {
            return;
        }
        applymapping(mappingConfiguration.getSingleFile());
        libraryjars(classesConfiguration.getSingleFile());
        super.proguard();
    }
}
