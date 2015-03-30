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

package com.android.build.gradle.integration.common.fixture;

import com.android.builder.model.AndroidProject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.util.List;
import java.util.Map;

/**
 * a Build Action that returns all the models for all the Gradle projects
 */
public class GetAndroidModelAction implements BuildAction<Map<String, AndroidProject>> {

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    @Override
    public Map<String, AndroidProject> execute(BuildController buildController) {

        long t1 = System.currentTimeMillis();
        GradleBuild gradleBuild = buildController.getBuildModel();
        DomainObjectSet<? extends BasicGradleProject> projects = gradleBuild.getProjects();

        final int projectCount = projects.size();
        Map<String, AndroidProject> modelMap = Maps.newHashMapWithExpectedSize(projectCount);

        List<BasicGradleProject> projectList = Lists.newArrayList(projects);
        List<Thread> threads = Lists.newArrayListWithCapacity(CPU_COUNT);
        List<ModelQuery> queries = Lists.newArrayListWithCapacity(CPU_COUNT);

        for (int i = 0 ; i < CPU_COUNT ; i++) {
            ModelQuery modelQuery = new ModelQuery(
                    projectList,
                    buildController);
            queries.add(modelQuery);
            Thread t = new Thread(modelQuery);
            threads.add(t);
            t.start();
        }

        for (int i = 0 ; i < CPU_COUNT ; i++) {
            try {
                threads.get(i).join();
                ModelQuery modelQuery = queries.get(i);
                modelMap.putAll(modelQuery.getModels());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        long t2 = System.currentTimeMillis();
        System.out.println("GetAndroidModelAction: " + (t2-t1) + "ms");

        return modelMap;
    }

    // index used by threads to get the new project to query.
    private volatile int currentIndex = 0;

    protected synchronized int getNextIndex() {
        return currentIndex++;
    }

    class ModelQuery implements Runnable {

        private final Map<String, AndroidProject> models;
        private final List<BasicGradleProject> projects;
        private final BuildController buildController;

        public ModelQuery(
                List<BasicGradleProject> projects,
                BuildController buildController) {
            this.projects = projects;
            this.buildController = buildController;

            models = Maps.newHashMapWithExpectedSize(projects.size() / CPU_COUNT);
        }

        public Map<String, AndroidProject> getModels() {
            return models;
        }

        @Override
        public void run() {
            final int count = projects.size();

            int index;
            while ((index = getNextIndex()) < count) {
                BasicGradleProject project = projects.get(index);

                AndroidProject model = buildController.findModel(project, AndroidProject.class);
                if (model != null) {
                    models.put(project.getPath(), model);
                }
            }
        }
    }
}
