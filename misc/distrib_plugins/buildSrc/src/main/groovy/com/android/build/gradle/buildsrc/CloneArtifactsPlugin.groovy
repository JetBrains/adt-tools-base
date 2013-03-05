/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.buildsrc
import org.gradle.api.Project
import org.gradle.api.Task

class CloneArtifactsPlugin implements org.gradle.api.Plugin<Project> {

    @Override
    void apply(Project project) {
        // put some tasks on the project.
        Task cloneArtifacts = project.tasks.create("cloneArtifacts")
        cloneArtifacts.setDescription("Clone dependencies")

        ShippingExtension shippingExtension = project.extensions.create('shipping', ShippingExtension)

        Task setupTask = project.tasks.add("setupMaven")
        setupTask << {
            project.repositories {
                mavenCentral()
            }
        }

        cloneArtifacts.dependsOn setupTask

        // if this is the top project.
        if (project.rootProject == project) {
            def extension = project.extensions.create('cloneArtifacts', CloneArtifactsExtension)

            // default shipping for root project is false
            shippingExtension.isShipping = false

            DownloadArtifactsTask downloadArtifactsTask = project.tasks.add("downloadArtifacts",
                    DownloadArtifactsTask)
            downloadArtifactsTask.project = project
            downloadArtifactsTask.conventionMapping.mainRepo =  { project.file(extension.mainRepo) }
            downloadArtifactsTask.conventionMapping.secondaryRepo =  {
                project.file(extension.secondaryRepo)
            }

            downloadArtifactsTask.dependsOn setupTask
            cloneArtifacts.dependsOn downloadArtifactsTask

            project.afterEvaluate {
                downloadArtifactsTask.dependsOn project.subprojects*.tasks.cloneArtifacts
            }
        }
    }
}
