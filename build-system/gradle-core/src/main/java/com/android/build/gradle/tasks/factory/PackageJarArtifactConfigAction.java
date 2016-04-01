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

package com.android.build.gradle.tasks.factory;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;

import org.gradle.api.tasks.bundling.Jar;

import java.io.File;

public class PackageJarArtifactConfigAction implements TaskConfigAction<Jar> {

    private final VariantScope scope;

    public PackageJarArtifactConfigAction(VariantScope scope) {
        this.scope = scope;
    }

    @NonNull
    @Override
    public String getName() {
        return scope.getTaskName("package", "JarArtifact");
    }

    @NonNull
    @Override
    public Class<Jar> getType() {
        return Jar.class;
    }

    @Override
    public void execute(@NonNull Jar jar) {
        scope.getVariantData().classesJarTask = jar;

        // add the class files (whether they are instrumented or not.
        jar.from(scope.getJavaOutputDir());

        jar.setDestinationDir(new File(
                scope.getGlobalScope().getIntermediatesDir(),
                "classes-jar/" +
                        scope.getVariantData().getVariantConfiguration().getDirName()));
        jar.setArchiveName("classes.jar");
    }
}
