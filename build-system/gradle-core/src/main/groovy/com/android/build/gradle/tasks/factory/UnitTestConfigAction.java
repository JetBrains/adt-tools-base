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

package com.android.build.gradle.tasks.factory;

import static com.android.SdkConstants.FN_FRAMEWORK_LIBRARY;
import static com.android.builder.core.VariantType.UNIT_TEST;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.reporting.ConfigurableReport;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestTaskReports;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Configuration Action for a JavaCompile task.
 */
public class UnitTestConfigAction implements TaskConfigAction<Test> {

    final VariantScope scope;

    public UnitTestConfigAction(VariantScope scope) {
        this.scope = scope;
    }

    @NonNull
    @Override
    public String getName() {
        return scope.getTaskName(UNIT_TEST.getPrefix());
    }

    @NonNull
    @Override
    public Class<Test> getType() {
        return Test.class;
    }

    @Override
    public void execute(@NonNull Test runTestsTask) {
        final TestVariantData variantData = (TestVariantData)scope.getVariantData();
        final BaseVariantData testedVariantData =
                (BaseVariantData) variantData.getTestedVariantData();

        // we run by default in headless mode, so the forked JVM doesn't steal focus.
        runTestsTask.systemProperty("java.awt.headless", "true");

        runTestsTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
        runTestsTask.setDescription(
                "Run unit tests for the "
                        + testedVariantData.getVariantConfiguration().getFullName()
                        + " build.");

        fixTestTaskSources(runTestsTask);

        runTestsTask.setTestClassesDir(scope.getJavaOutputDir());

        ConventionMappingHelper.map(runTestsTask, "classpath",
                new Callable<ConfigurableFileCollection>() {
                    @Override
                    public ConfigurableFileCollection call() throws Exception {
                        Iterable<File> filteredBootClasspath = Iterables.filter(
                                scope.getGlobalScope().getAndroidBuilder().getBootClasspath(false),
                                new Predicate<File>() {
                                    @Override
                                    public boolean apply(@Nullable File file) {
                                        Preconditions.checkState(file != null);
                                        return !FN_FRAMEWORK_LIBRARY.equals(file.getName());
                                    }
                                });

                        List<Object> classpaths = Lists.newArrayList();

                        // Get classpath values from tasks if the tasks are already created.
                        final AbstractCompile testCompileTask = variantData.javacTask;
                        if (testCompileTask != null) {
                            classpaths.add(testCompileTask.getClasspath());
                            classpaths.add(testCompileTask.getOutputs().getFiles());
                        } else {
                            classpaths.add(testedVariantData.getScope().getJavaOuptuts());
                            classpaths.add(scope.getJavaOuptuts());
                        }

                        classpaths.add(variantData.getJavaResourcesForUnitTesting());
                        classpaths.add(testedVariantData.getJavaResourcesForUnitTesting());
                        classpaths.add(filteredBootClasspath);

                        // Mockable JAR is last, to make sure you can shadow the classes with
                        // dependencies.
                        classpaths.add(scope.getGlobalScope().getMockableAndroidJarFile());

                        return scope.getGlobalScope().getProject().files(classpaths);
                    }
                });

        // Put the variant name in the report path, so that different testing tasks don't
        // overwrite each other's reports.
        // For component model plugin, the report tasks are not yet configured.  We get a hardcoded
        // value matching Gradle's default.  This will eventually be replaced with the new Java
        // plugin.
        TestTaskReports testTaskReports = runTestsTask.getReports();
        ConfigurableReport xmlReport = testTaskReports.getJunitXml();
        xmlReport.setDestination(
                xmlReport.getDestination() != null
                        ? new File(xmlReport.getDestination(), testedVariantData.getName())
                        : new File(scope.getGlobalScope().getTestResultsFolder(),
                                testedVariantData.getName()));

        ConfigurableReport htmlReport = testTaskReports.getHtml();
        htmlReport.setDestination(
                htmlReport.getDestination() != null
                        ? new File(htmlReport.getDestination(), testedVariantData.getName())
                        : new File(scope.getGlobalScope().getTestReportFolder(),
                                testedVariantData.getName()));

        scope.getGlobalScope().getExtension().getTestOptions().getUnitTests().applyConfiguration(
                runTestsTask);

    }

    private static void fixTestTaskSources(@NonNull Test testTask) {
        // We are running in afterEvaluate, so the JavaBasePlugin has already added a
        // callback to add test classes to the list of source files of the newly created task.
        // The problem is that we haven't configured the test classes yet (JavaBasePlugin
        // assumes all Test tasks are fully configured at this point), so we have to remove the
        // "directory null" entry from source files and add the right value.
        //
        // This is an ugly hack, since we assume sourceFiles is an instance of
        // DefaultConfigurableFileCollection.
        ((DefaultConfigurableFileCollection) testTask.getInputs().getSourceFiles()).getFrom().clear();
    }

}
