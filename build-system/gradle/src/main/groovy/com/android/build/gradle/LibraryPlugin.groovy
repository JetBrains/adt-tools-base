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
package com.android.build.gradle

import com.android.build.gradle.internal.variant.LibraryVariantFactory
import com.android.build.gradle.internal.variant.VariantFactory
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.internal.reflect.Instantiator
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

import javax.inject.Inject
/**
 * Gradle plugin class for 'library' projects.
 */
public class LibraryPlugin extends BasePlugin implements Plugin<Project> {

    /**
     * Default assemble task for the default-published artifact. this is needed for
     * the prepare task on the consuming project.
     */
    Task assembleDefault

    @Inject
    public LibraryPlugin(Instantiator instantiator, ToolingModelBuilderRegistry registry) {
        super(instantiator, registry)
    }

    @Override
    public Class<? extends BaseExtension> getExtensionClass() {
        return LibraryExtension.class
    }

    @Override
    protected VariantFactory getVariantFactory() {
        return new LibraryVariantFactory(this, (LibraryExtension) getExtension(), this.taskManager);
    }

    @Override
    protected boolean isLibrary() {
        return true;
    }

    @Override
    void apply(Project project) {
        super.apply(project)

        assembleDefault = project.tasks.create("assembleDefault")
    }
}
