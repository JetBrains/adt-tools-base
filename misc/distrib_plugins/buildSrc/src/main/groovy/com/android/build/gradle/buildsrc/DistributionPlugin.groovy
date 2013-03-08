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
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Jar

class DistributionPlugin implements org.gradle.api.Plugin<Project> {

    private Project project

    @Override
    void apply(Project project) {
        this.project = project

        // put some tasks on the project.
        Task pushDistribution = project.tasks.create("pushDistribution")
        pushDistribution.group = "Upload"
        pushDistribution.description = "Push the distribution artifacts into the prebuilt folder"

        // if this is the top project.
        if (project.rootProject == project) {
            DistributionExtension extension = project.extensions.create('distribution',
                    DistributionExtension)

            // deal with NOTICE files from all the sub projects
            GatherNoticesTask gatherNoticesTask = project.tasks.add(
                    "gatherNotices", GatherNoticesTask)
            gatherNoticesTask.project = project
            gatherNoticesTask.conventionMapping.distributionDir =  {
                project.file(extension.destinationPath + "/notices")
            }
            gatherNoticesTask.conventionMapping.repoDir =  {
                project.file(extension.dependenciesRepo)
            }

            pushDistribution.dependsOn gatherNoticesTask
        } else {
            Jar buildTask = project.tasks.add("buildDistributionJar", Jar)
            buildTask.from(project.sourceSets.main.output)
            buildTask.conventionMapping.destinationDir = {
                project.file(project.rootProject.distribution.destinationPath + "/tools/lib")
            }
            buildTask.conventionMapping.archiveName = {project.archivesBaseName + ".jar" }

            pushDistribution.dependsOn buildTask

            // delay computing the manifest classpath only if the
            // prebuiltJar task is set to run.
            project.gradle.taskGraph.whenReady { taskGraph ->
                if (taskGraph.hasTask(project.tasks.buildDistributionJar)) {
                    project.tasks.buildDistributionJar.manifest.attributes(
                            "Class-Path": getClassPath())
                }
            }

            Copy copyTask = project.tasks.add("copyLauncherScripts", Copy)
            copyTask.from {
                if (project.shipping.launcherScripts != null) {
                    return project.files(project.shipping.launcherScripts.toArray())
                }
                return null
            }
            copyTask.conventionMapping.destinationDir = {
                project.file(project.rootProject.distribution.destinationPath + "/tools")
            }
            pushDistribution.dependsOn copyTask

            // also copy the dependencies
            CopyDependenciesTask copyDependenciesTask = project.tasks.add(
                    "copyDependencies", CopyDependenciesTask)
            copyDependenciesTask.project = project
            copyDependenciesTask.conventionMapping.distributionDir =  {
                project.file(project.rootProject.distribution.destinationPath + "/tools/lib")
            }

            copyDependenciesTask.onlyIf { project.shipping.isShipping }

            pushDistribution.dependsOn copyDependenciesTask

            // only push distribution of projects that are shipped.
            // When the task are created the project is not fully evaluated.
            project.afterEvaluate {
                if (!project.shipping.isShipping) {
                    buildTask.enabled = false
                    copyDependenciesTask.enabled = false
                    copyTask.enabled = false
                }
            }
        }
    }

    private String getClassPath() {
        StringBuilder sb = new StringBuilder()

        Configuration configuration = project.configurations.runtime
        getClassPathFromConfiguration(configuration, sb)

        try {
            configuration = project.configurations.getByName("swt")
            getClassPathFromConfiguration(configuration, sb)
        } catch (UnknownDomainObjectException e) {
            // ignore
        }

        return sb.toString()
    }

    protected void getClassPathFromConfiguration(Configuration configuration, StringBuilder sb) {
        for (File file : configuration.files) {
            String name = file.name
            String suffix = "-" + project.version + ".jar"
            if (name.endsWith(suffix)) {
                name = name.substring(0, name.size() - suffix.size()) + ".jar"
            }
            sb.append(' ').append(name)
        }
    }
}
