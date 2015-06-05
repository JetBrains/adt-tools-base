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
package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.dependency.DependencyChecker
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.builder.model.ApiVersion
import com.android.builder.model.SyncIssue
import com.android.sdklib.SdkVersionInfo
import com.android.utils.Pair
import groovy.transform.CompileStatic
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

@CompileStatic
public class PrepareDependenciesTask extends BaseTask {
    BaseVariantData variant
    final List<DependencyChecker> checkers = []
    final Set<Pair<Integer, String>> androidDependencies = []

    public void addDependency(Pair<Integer, String> api) {
        androidDependencies.add(api)
    }

    @TaskAction
    protected void prepare() {
        ApiVersion minSdkVersion = variant.variantConfiguration.minSdkVersion
        int minSdk = 1
        if (minSdkVersion != null) {
            if (minSdkVersion.getCodename() != null) {
                minSdk = SdkVersionInfo.getApiByBuildCode(minSdkVersion.getCodename(), true)
            } else {
                minSdk = minSdkVersion.getApiLevel()
            }
        }

        boolean foundError = false;

        for (DependencyChecker checker : checkers) {
            checker.legacyApiLevels.each { mavenVersion, api ->
                if (api > minSdk) {
                    foundError = true;
                    def configurationName = checker.configurationDependencies.name.capitalize()
                    logger.error(
                            "Variant ${configurationName} has a dependency on version ${mavenVersion.version} " +
                                    "of the legacy ${mavenVersion.group} Maven artifact, which corresponds to " +
                                    "API level ${api}. This is not compatible with min SDK of this module, " +
                                    "which is ${minSdk}. Please use the 'gradle dependencies' task to debug your " +
                                    "dependencies graph.")
                }
            }

            for (SyncIssue syncIssue : checker.getSyncIssues()) {
                foundError = true
                logger.error(syncIssue.message);
            }
        }

        if (foundError) {
            throw new GradleException("Dependency Error. See console for details.");
        }

    }

    def addChecker(DependencyChecker checker) {
        checkers.add(checker)
    }
}
