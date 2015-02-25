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
package com.android.build.gradle.internal.dependency;

import com.android.annotations.NonNull;
import com.android.builder.model.SyncIssue;
import com.android.utils.ILogger;
import com.google.common.collect.Lists;

import org.gradle.api.artifacts.ModuleVersionIdentifier;

import java.util.List;

/**
 * Checks for dependencies to ensure Android compatibility
 */
public class DependencyChecker {

    @NonNull
    private final VariantDependencies configurationDependencies;
    @NonNull
    private final ILogger logger;
    @NonNull
    private final List<Integer> foundAndroidApis = Lists.newArrayList();
    @NonNull
    private final List<String> foundBouncyCastle = Lists.newArrayList();
    @NonNull
    private final List<SyncIssue> syncIssues = Lists.newArrayList();

    public DependencyChecker(
            @NonNull VariantDependencies configurationDependencies,
            @NonNull ILogger logger) {
        this.configurationDependencies = configurationDependencies;
        this.logger = logger;
    }

    @NonNull
    public List<Integer> getFoundAndroidApis() {
        return foundAndroidApis;
    }

    @NonNull
    public VariantDependencies getConfigurationDependencies() {
        return configurationDependencies;
    }

    public void addSyncIssue(SyncIssue syncIssue) {
        if (syncIssue != null) {
            syncIssues.add(syncIssue);
        }
    }

    @NonNull
    public List<SyncIssue> getSyncIssues() {
        return syncIssues;
    }

    public boolean excluded(ModuleVersionIdentifier id) {
        String group = id.getGroup();
        String name = id.getName();
        String version = id.getVersion();

        if ("com.google.android".equals(group) && "android".equals(name)) {
            int moduleLevel = getApiLevelFromMavenArtifact(version);
            foundAndroidApis.add(moduleLevel);

            logger.info("Ignoring Android API artifact %s for %s", id,
                    configurationDependencies.getName());
            return true;
        }

        if (("org.apache.httpcomponents".equals(group) && "httpclient".equals(name)) ||
                ("xpp3".equals(group) && name.equals("xpp3")) ||
                ("commons-logging".equals(group) && "commons-logging".equals(name)) ||
                ("xerces".equals(group) && "xmlParserAPIs".equals(name))) {

            logger.warning(
                    "WARNING: Dependency %s is ignored for %s as it may be conflicting with the internal version provided by Android.\n" +
                            "         In case of problem, please repackage it with jarjar to change the class packages",
                    id, configurationDependencies.getName());
            return true;
        }

        if ("org.json".equals(group) && "json".equals(name)) {
            logger.warning(
                    "WARNING: Dependency %s is ignored for %s as it may be conflicting with the internal version provided by Android.\n" +
                            "         In case of problem, please repackage with jarjar to change the class packages",
                    id, configurationDependencies.getName());
            return true;
        }

        if ("org.khronos".equals(group) && "opengl-api".equals(name)) {
            logger.warning(
                    "WARNING: Dependency %s is ignored for %s as it may be conflicting with the internal version provided by Android.\n" +
                            "         In case of problem, please repackage with jarjar to change the class packages",
                    id, configurationDependencies.getName());
            return true;
        }

        if ("org.bouncycastle".equals(group) && name.startsWith("bcprov")) {
            foundBouncyCastle.add(version);
        }

        return false;
    }

    private static int getApiLevelFromMavenArtifact(@NonNull String version) {
        if ("1.5_r3".equals(version) || "1.5_r4".equals(version)) {
            return 3;
        } else if ("1.6_r2".equals(version)) {
            return 4;
        } else if ("2.1_r1".equals(version) || version.equals("2.1.2")) {
            return 7;
        } else if ("2.2.1".equals(version)) {
            return 8;
        } else if ("2.3.1".equals(version)) {
            return 9;
        } else if ("2.3.3".equals(version)) {
            return 10;
        } else if ("4.0.1.2".equals(version)) {
            return 14;
        } else if ("4.1.1.4".equals(version)) {
            return 15;
        }

        return -1;
    }
}

