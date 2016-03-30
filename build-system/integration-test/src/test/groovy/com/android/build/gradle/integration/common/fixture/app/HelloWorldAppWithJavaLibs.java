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

package com.android.build.gradle.integration.common.fixture.app;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.TestProject;
import com.google.common.collect.ImmutableMap;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Project containing the Android application and multiple java libraries
 */
public class HelloWorldAppWithJavaLibs extends MultiModuleTestProject implements TestProject {

    private HelloWorldAppWithJavaLibs(@NonNull Map<String, ? extends TestProject> subprojects) {
        super(subprojects);
    }

    /**
     * Create project containing one android application, and the specified number of java
     * libraries
     *
     * @param numJavaLibs number of java libraries to create in the project
     * @return project
     */
    public static HelloWorldAppWithJavaLibs createWithLibs(int numJavaLibs) {
        Map<String, TestProject> subProjects = new HashMap<>();
        subProjects.put(":app", HelloWorldApp.forPlugin("com.android.application"));

        for (int i = 0; i < numJavaLibs; i++) {
            subProjects.put(":lib" + (i + 1),
                    new JavaGradleModule(new File("./"), "",
                            Collections.<GradleModule>emptyList()));
        }
        return new HelloWorldAppWithJavaLibs(subProjects);
    }
}