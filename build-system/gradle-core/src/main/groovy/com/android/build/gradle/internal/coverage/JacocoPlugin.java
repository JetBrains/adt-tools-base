/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.internal.coverage;
import com.google.common.collect.Lists;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.ResolvedArtifact;

import java.util.List;
import java.util.Set;


/**
 * Jacoco plugin. This is very similar to the built-in support for Jacoco but we dup it in order
 * to control it as we need our own offline instrumentation.
 *
 * This may disappear if we can ever reuse the built-in support.
 *
 */
public class JacocoPlugin implements Plugin<Project> {
    public static final String ANT_CONFIGURATION_NAME = "androidJacocoAnt";
    public static final String AGENT_CONFIGURATION_NAME = "androidJacocoAgent";

    private Project project;

    @Override
    public void apply(Project project) {
        this.project = project;
        String jacocoVersion = getJacocoVersion();
        addJacocoConfigurations();
        configureAgentDependencies(jacocoVersion);
        configureTaskClasspathDefaults(jacocoVersion);
    }

    /**
     * Creates the configurations used by plugin.
     */
    private void addJacocoConfigurations() {
        this.project.getConfigurations().create(AGENT_CONFIGURATION_NAME,
                new Action<Configuration>() {
                    @Override
                    public void execute(Configuration files) {
                        files.setVisible(false);
                        files.setTransitive(true);
                        files.setDescription("The Jacoco agent to use to get coverage data.");
                    }
                });
        this.project.getConfigurations().create(ANT_CONFIGURATION_NAME,
                new Action<Configuration>() {
                    @Override
                    public void execute(Configuration files) {
                        files.setVisible(false);
                        files.setTransitive(true);
                        files.setDescription(
                                "The Jacoco ant tasks to use to get execute Gradle tasks.");
                    }
                });
    }

    private String getJacocoVersion() {
        Set<ResolvedArtifact> resolvedArtifacts =
                project.getRootProject().getBuildscript().getConfigurations().getByName("classpath")
                        .getResolvedConfiguration().getResolvedArtifacts();
        for (ResolvedArtifact artifact: resolvedArtifacts) {
            ModuleVersionIdentifier moduleVersion = artifact.getModuleVersion().getId();
            if ("org.jacoco.core".equals(moduleVersion.getName())) {
                return moduleVersion.getVersion();
            }
        }
        if (resolvedArtifacts.isEmpty()) {
            // DSL test case, dependencies are not loaded.
            project.getLogger().error(
                    "No resolved dependencies found when searching for the jacoco version.");
            return null;
        }
        throw new IllegalStateException(
                "Could not find project build script dependency on org.jacoco.core");
    }

    /**
     * Configures the agent dependencies using the 'jacocoAnt' configuration.
     * Uses the version declared as a build script dependency if no other versions are specified.
     */
    private void configureAgentDependencies(final String jacocoVersion) {
        final Configuration config = project.getConfigurations().getByName(AGENT_CONFIGURATION_NAME);
        config.getIncoming().beforeResolve(new Action<ResolvableDependencies>() {
            @Override
            public void execute(ResolvableDependencies resolvableDependencies) {
                if (config.getDependencies().isEmpty()) {
                    config.getDependencies().add(project.getDependencies().create(
                                    "org.jacoco:org.jacoco.agent:" + jacocoVersion));
                }
            }
        });
    }

    /**
     * Configures the classpath for Jacoco tasks using the 'jacocoAnt' configuration.
     * Uses the version declared as a build script dependency if no other versions are specified.
     */
    private void configureTaskClasspathDefaults(final String jacocoVersion) {
        final Configuration config = project.getConfigurations().getByName(ANT_CONFIGURATION_NAME);
        config.getIncoming().beforeResolve(new Action<ResolvableDependencies>() {
            @Override
            public void execute(ResolvableDependencies resolvableDependencies) {
                if (config.getDependencies().isEmpty()) {
                    config.getDependencies().add(project.getDependencies().create(
                            "org.jacoco:org.jacoco.ant:" + jacocoVersion));
                }
            }
        });
    }
}
