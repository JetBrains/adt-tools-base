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

package com.google.gms.googleservices

import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

class GoogleServicesPlugin implements Plugin<Project> {

    public final static String JSON_FILE_NAME = 'google-services.json'

    public final static String MODULE_GROUP = "com.google.android.gms"
    public final static String MODULE_NAME = "play-services-measurement"
    public final static String MODULE_VERSION = "8.3.0"
    public final static String MINIMUM_VERSION = "8.1.0"

    private static String targetVersion;

    @Override
    void apply(Project project) {
        if (project.plugins.hasPlugin("android") ||
                project.plugins.hasPlugin("com.android.application")) {
            // this is a bit fragile but since this is internal usage this is ok
            // (another plugin could declare itself to be 'android')
            addDependency(project)
            setupPlugin(project, false)
            return
        }
        if (project.plugins.hasPlugin("android-library") ||
                project.plugins.hasPlugin("com.android.library")) {
            // this is a bit fragile but since this is internal usage this is ok
            // (another plugin could declare itself to be 'android-library')
            addDependency(project)
            setupPlugin(project, true)
            return
        }
        // If the google-service plugin is applied before any android plugin.
        // We should warn that google service plugin should be applied at
        // the bottom of build file.
        showWarningForPluginLocation(project)

        // Setup google-services plugin after android plugin is applied.
        project.plugins.withId("android", {
            setupPlugin(project, false)
        })
        project.plugins.withId("android-library", {
            setupPlugin(project, true)
        })

        // Add dependencies after the build file is evaluate and hopefully it
        // can be execute before android plugin process the dependencies.
        project.afterEvaluate({
            addDependency(project)
        })
    }

    private void showWarningForPluginLocation(Project project) {
        project.getLogger().warn(
            "please apply google-services plugin at the bottom of the build file.")
    }

    private boolean checkMinimumVersion() {
        String[] subTargetVersions = targetVersion.split("\\.")
        String[] subMinimumVersions = MINIMUM_VERSION.split("\\.")
        for (int i = 0; i < subTargetVersions.length && i < subMinimumVersions.length; i++) {
            Integer subTargetVersion = Integer.valueOf(subTargetVersions[i])
            Integer subMinimumVersion = Integer.valueOf(subMinimumVersions[i])
            if (subTargetVersion > subMinimumVersion) {
                return true;
            } else if (subTargetVersion < subMinimumVersion) {
                return false;
            }
        }
        return subTargetVersions.length >= subMinimumVersions.length;
    }

    private void addDependency(Project project) {
        targetVersion = findTargetVersion(project).split("-")[0]
        if (checkMinimumVersion()) {
            // If the target version is not lower than the minimum version.
            project.dependencies.add('compile', MODULE_GROUP + ':' + MODULE_NAME + ':' + targetVersion)
        } else {
            throw new GradleException("Version: " + targetVersion + " is lower than the minimum version (" +
                MINIMUM_VERSION + ") required for google-services plugin.")
        }
    }

    private String findTargetVersion(Project project) {
        def configurations = project.getConfigurations()
        if (configurations == null) return
        for (def configuration : configurations) {
            if (configuration == null) continue
            def dependencies = configuration.getDependencies()
            if (dependencies == null) continue
            for (def dependency : dependencies) {
                if (dependency == null) continue
                if (dependency.getGroup() == MODULE_GROUP) {
                    // Use the first version found in the dependencies.
                    return dependency.getVersion()
                }
            }
        }

        // If none of the version for Google play services is found, default
        // version is used and a warning that google-services plugin should be
        // applied at the bottom of the build file.
        project.getLogger().warn("google-services plugin could not detect any version for " +
            MODULE_GROUP + ", default version: " + MODULE_VERSION + " will be used.")
        showWarningForPluginLocation(project)
        // If no version is found, use the default one for the plugin.
        return MODULE_VERSION
    }

    private void setupPlugin(Project project, boolean isLibrary) {
        if (isLibrary) {
            project.android.libraryVariants.all { variant ->
                handleVariant(project, variant)
            }
        } else {
            project.android.applicationVariants.all { variant ->
                handleVariant(project, variant)
            }
        }
    }

    private static void handleVariant(Project project, def variant) {
        File quickstartFile = project.file(JSON_FILE_NAME)

        String variantName = "$variant.dirName";
        String[] variantTokens = variantName.split('/')
        // If flavor is found.
        if (variantTokens.length == 2) {
            String flavorName = variantTokens[0]
            // check google-services.json at flavor source folder.
            // If file exists, it will be used instead of the one at root.
            File flavorFile = project.file('src/' + flavorName + '/' + JSON_FILE_NAME)
            if (flavorFile.isFile()) {
                quickstartFile = flavorFile
            }
        }

        File outputDir = project.file("$project.buildDir/generated/res/google-services/$variant.dirName")

        GoogleServicesTask task = project.tasks.create("process${variant.name.capitalize()}GoogleServices", GoogleServicesTask)

        task.quickstartFile = quickstartFile
        task.intermediateDir = outputDir
        task.packageName = variant.applicationId
        task.moduleGroup = MODULE_GROUP;
        // Use the target version for the task.
        task.moduleVersion = targetVersion;

        variant.registerResGeneratingTask(task, outputDir)
    }
}
