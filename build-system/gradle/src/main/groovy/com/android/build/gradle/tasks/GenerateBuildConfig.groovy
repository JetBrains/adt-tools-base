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
package com.android.build.gradle.tasks

import com.android.build.gradle.internal.tasks.IncrementalTask
import com.android.builder.compiling.BuildConfigGenerator
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory

public class GenerateBuildConfig extends IncrementalTask {

    // ----- PUBLIC TASK API -----

    @OutputDirectory
    File sourceOutputDir

    // ----- PRIVATE TASK API -----

    @Input
    String buildConfigPackageName

    @Input
    String appPackageName

    @Input
    boolean debuggable

    @Input
    String flavorName

    @Input
    List<String> flavorNamesWithDimensionNames

    @Input
    String buildTypeName

    @Input @Optional
    String versionName

    @Input
    int versionCode

    @Input
    List<Object> items;

    @Override
    protected void doFullTaskAction() {
        // must clear the folder in case the packagename changed, otherwise,
        // there'll be two classes.
        File destinationDir = getSourceOutputDir()
        emptyFolder(destinationDir)

        BuildConfigGenerator generator = new BuildConfigGenerator(
                getSourceOutputDir().absolutePath,
                getBuildConfigPackageName());

        // Hack (see IDEA-100046): We want to avoid reporting "condition is always true"
        // from the data flow inspection, so use a non-constant value. However, that defeats
        // the purpose of this flag (when not in debug mode, if (BuildConfig.DEBUG && ...) will
        // be completely removed by the compiler), so as a hack we do it only for the case
        // where debug is true, which is the most likely scenario while the user is looking
        // at source code.
        //map.put(PH_DEBUG, Boolean.toString(mDebug));
        generator.addField("boolean", "DEBUG", getDebuggable() ? "Boolean.parseBoolean(\"true\")" : "false")
            .addField("String", "PACKAGE_NAME", "\"${getAppPackageName()}\"")
            .addField("String", "BUILD_TYPE", "\"${getBuildTypeName()}\"")
            .addField("String", "FLAVOR", "\"${getFlavorName()}\"")
            .addField("int", "VERSION_CODE", Integer.toString(getVersionCode()))
            .addItems(getItems());

        if (getVersionName() != null) {
            generator.addField("String", "VERSION_NAME", "\"${getVersionName()}\"")
        }

        List<String> flavors = getFlavorNamesWithDimensionNames();
        int count = flavors.size();
        if (count > 1) {
            for (int i = 0; i < count ; i+=2) {
                generator.addField("String", "FLAVOR_${flavors.get(i+1)}", "\"${flavors.get(i)}\"")
            }
        }

        generator.generate();
    }
}
