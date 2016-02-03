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

package com.android.tools.profiler;

import com.android.annotations.NonNull;
import com.android.utils.FileUtils;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.io.File;

/**
 * A gradle plugin which, when applied, instruments the target Android app with support code for
 * helping profile it.
 */
public class ProfilerPlugin implements Plugin<Project> {

  private static final String PROPERTY_ENABLED = "android.profiler.enabled";
  private static final String PROPERTY_SUPPORT_PATH = "android.profiler.lib";

  @NonNull
  private static File toOutputFile(File outputDir, File inputDir, File inputFile) {
    return new File(outputDir, FileUtils.relativePath(inputFile, inputDir));
  }

  @Override
  public void apply(final Project project) {

    // TODO: This code was disabled to fix a build error and will be reinstated in a followup CL

    //Map<String, ?> properties = project.getProperties();
    //Boolean isEnabled = Boolean.parseBoolean((String)properties.get(PROPERTY_ENABLED));
    //String supportJarPath = (String)properties.get(PROPERTY_SUPPORT_PATH);
    //
    //File supportJar = (supportJarPath != null) ? new File(supportJarPath) : null;
    //if (supportJar != null && supportJar.exists()) {
    //  project.getDependencies().add("compile", project.files(supportJar));
    //}
    //else {
    //  isEnabled = false;
    //}
    //
    //final Boolean shouldInstrument = isEnabled;
    //
    //// TODO: The following line won't work for the experimental plugin. For that we may need to
    //// register a rule that will get executed at the right time. Investigate this before
    //// shipping the plugin.
    //Object android = project.getExtensions().getByName("android");
    //try {
    //  Method method = android.getClass()
    //    .getMethod("registerTransform", Transform.class, Object[].class);
    //  method.invoke(android, new Transform() {
    //    @NonNull
    //    @Override
    //    public String getName() {
    //      return "studioprofiler";
    //    }
    //
    //    @NonNull
    //    @Override
    //    public Set<QualifiedContent.ContentType> getInputTypes() {
    //      return ImmutableSet.<QualifiedContent.ContentType>of(
    //        QualifiedContent.DefaultContentType.CLASSES);
    //    }
    //
    //    @NonNull
    //    @Override
    //    public Set<QualifiedContent.Scope> getScopes() {
    //      return ImmutableSet.of(QualifiedContent.Scope.PROJECT);
    //    }
    //
    //    @Override
    //    public boolean isIncremental() {
    //      return true;
    //    }
    //
    //    @Override
    //    public void transform(@NonNull TransformInvocation invocation)
    //      throws TransformException, InterruptedException, IOException {
    //
    //      assert invocation.getOutputProvider() != null;
    //      File outputDir = invocation.getOutputProvider().getContentLocation(
    //        "main", getOutputTypes(), getScopes(), Format.DIRECTORY);
    //      FileUtils.mkdirs(outputDir);
    //
    //      for (TransformInput ti : invocation.getInputs()) {
    //        Preconditions.checkState(ti.getJarInputs().isEmpty());
    //        for (DirectoryInput di : ti.getDirectoryInputs()) {
    //          File inputDir = di.getFile();
    //          if (invocation.isIncremental()) {
    //            for (Map.Entry<File, Status> entry : di.getChangedFiles()
    //              .entrySet()) {
    //              File inputFile = entry.getKey();
    //              File outputFile = toOutputFile(outputDir, inputDir, inputFile);
    //              switch (entry.getValue()) {
    //                case ADDED:
    //                case CHANGED:
    //                  instrumentFile(inputFile, outputFile);
    //                  break;
    //                case REMOVED:
    //                  FileUtils.delete(outputFile);
    //                  break;
    //              }
    //            }
    //          }
    //          else {
    //            for (File inputFile : FileUtils.getAllFiles(inputDir)) {
    //              File outputFile = toOutputFile(outputDir, inputDir, inputFile);
    //              instrumentFile(inputFile, outputFile);
    //            }
    //          }
    //        }
    //      }
    //    }
    //
    //    private void instrumentFile(File inputFile, File outputFile)
    //      throws IOException {
    //      Files.createParentDirs(outputFile);
    //
    //      // TODO: This MainActivity check is temporary and here as a proof of concept that
    //      // instrumenting an Android app works. Later, we'll replace this with logic that
    //      // inserts a profiler support library as a dependency into the user's app as
    //      // well as instrument it.
    //      if (shouldInstrument) {
    //        ApplicationInstrumentor.instrument(inputFile, outputFile);
    //      }
    //      else {
    //        Files.copy(inputFile, outputFile);
    //      }
    //    }
    //  }, new Object[]{});
    //}
    //catch (NoSuchMethodException e) {
    //  e.printStackTrace();
    //}
    //catch (InvocationTargetException e) {
    //  e.printStackTrace();
    //}
    //catch (IllegalAccessException e) {
    //  e.printStackTrace();
    //}
  }
}
