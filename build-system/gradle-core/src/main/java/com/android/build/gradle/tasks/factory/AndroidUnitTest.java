/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import org.gradle.api.file.FileTree;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.reporting.ConfigurableReport;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestTaskReports;

import java.io.File;
import java.util.List;
import java.util.function.Predicate;

/**
 * Patched version of {@link Test} that we need to use for local unit tests support.
 */
public class AndroidUnitTest extends Test {

    /**
     * Returns the test class files.
     *
     * <p>This is the special case we need to handle - if getCandidateClassFiles is called
     * too early, i.e. before the task is fully configured, return an empty FileTree. The
     * default is to create a FileTree using getTestClassesDir(), but that creates a
     * FileTree with a null root, which fails later on.
     *
     * @see ConfigAction#configureSources(AndroidUnitTest)
     */
    @Override
    public FileTree getCandidateClassFiles() {
        if (getTestClassesDir() == null) {
            return getProject().files().getAsFileTree();
        } else {
            return super.getCandidateClassFiles();
        }
    }

    /**
     * Configuration Action for a JavaCompile task.
     */
    public static class ConfigAction implements TaskConfigAction<AndroidUnitTest> {

        private static final Predicate<File> PLATFORM_JAR =
                file -> FN_FRAMEWORK_LIBRARY.equals(file.getName());

        private final VariantScope scope;

        public ConfigAction(@NonNull VariantScope scope) {
            this.scope = Preconditions.checkNotNull(scope);
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName(UNIT_TEST.getPrefix());
        }

        @NonNull
        @Override
        public Class<AndroidUnitTest> getType() {
            return AndroidUnitTest.class;
        }

        @Override
        public void execute(@NonNull AndroidUnitTest runTestsTask) {
            final TestVariantData variantData = (TestVariantData) scope.getVariantData();
            final BaseVariantData testedVariantData =
                    (BaseVariantData) variantData.getTestedVariantData();

            // we run by default in headless mode, so the forked JVM doesn't steal focus.
            runTestsTask.systemProperty("java.awt.headless", "true");

            runTestsTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
            runTestsTask.setDescription(
                    "Run unit tests for the "
                            + testedVariantData.getVariantConfiguration().getFullName()
                            + " build.");

            runTestsTask.setTestClassesDir(scope.getJavaOutputDir());

            configureSources(runTestsTask);

            ConventionMappingHelper.map(
                    runTestsTask,
                    "classpath",
                    () -> {
                        List<Object> classpaths = Lists.newArrayList();

                        // Get classpath values from tasks if the tasks are already created.
                        final AbstractCompile testCompileTask = variantData.javacTask;
                        if (testCompileTask != null) {
                            // When Jack is used for building the APK, the platform jar for javac
                            // may end up in the class path (as opposed to boot class path), so we
                            // need to remove it.
                            testCompileTask
                                    .getClasspath()
                                    .getFiles()
                                    .stream()
                                    .filter(PLATFORM_JAR.negate())
                                    .forEach(classpaths::add);

                            classpaths.add(testCompileTask.getOutputs().getFiles());
                        } else {
                            classpaths.add(testedVariantData.getScope().getJavaOutputs());
                            classpaths.add(scope.getJavaOutputs());
                        }

                        classpaths.add(variantData.getJavaResourcesForUnitTesting());
                        classpaths.add(testedVariantData.getJavaResourcesForUnitTesting());

                        scope.getGlobalScope()
                                .getAndroidBuilder()
                                .getBootClasspath(false)
                                .stream()
                                .filter(PLATFORM_JAR.negate())
                                .forEach(classpaths::add);

                        // Mockable JAR is last, to make sure you can shadow the classes with
                        // dependencies.
                        classpaths.add(scope.getGlobalScope().getMockableAndroidJarFile());

                        return scope.getGlobalScope().getProject().files(classpaths);
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
                            : new File(
                                    scope.getGlobalScope().getTestResultsFolder(),
                                    testedVariantData.getName()));

            ConfigurableReport htmlReport = testTaskReports.getHtml();
            htmlReport.setDestination(
                    htmlReport.getDestination() != null
                            ? new File(htmlReport.getDestination(), testedVariantData.getName())
                            : new File(
                                    scope.getGlobalScope().getTestReportFolder(),
                                    testedVariantData.getName()));

            scope.getGlobalScope()
                    .getExtension()
                    .getTestOptions()
                    .getUnitTests()
                    .applyConfiguration(runTestsTask);
        }

        /**
         * Sets task inputs. Normally this is done by JavaBasePlugin, but in our case this is too early
         * and candidate class files are not known yet. So we call this here, once we know the class
         * files.
         *
         * @see AndroidUnitTest#getCandidateClassFiles()
         */
        private static TaskInputs configureSources(@NonNull AndroidUnitTest runTestsTask) {
            return runTestsTask.getInputs().source(runTestsTask.getCandidateClassFiles());
        }
    }
}
