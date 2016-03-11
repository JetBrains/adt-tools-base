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
import com.android.build.api.transform.*;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * A gradle plugin which, when applied, instruments the target Android app with support code for
 * helping profile it.
 */
public class ProfilerPlugin implements Plugin<Project> {
  private static final String PROPERTY_PROPERTIES_FILE = "android.profiler.properties";
  private static final String PROPERTY_ENABLED = "android.profiler.enabled";
  private static final String PROPERTY_GAPID_ENABLED = "android.profiler.gapid.enabled";
  private static final String PROPERTY_GAPID_TRACER_AAR = "android.profiler.gapid.tracer_aar";

  @NonNull
  private static File toOutputFile(File outputDir, File inputDir, File inputFile) {
    return new File(outputDir, FileUtils.relativePath(inputFile, inputDir));
  }

  static class ExtractSupportLibJarTask extends DefaultTask {

    private File outputFile;


    // This empty constructor is needed because Gradle will look at the declared constructors
    // to create objects dynamically. Yeap.
    public ExtractSupportLibJarTask() {
    }

    @OutputFile
    public File getOutputFile() {
      return outputFile;
    }

    public void setOutputFile(File file) {
      this.outputFile = file;
    }

    @TaskAction
    public void extract() throws IOException {
      InputStream jar = ProfilerPlugin.class.getResourceAsStream("/profilers-support-lib.jar");
      if (jar == null) {
        throw new RuntimeException("Couldn't find profiler support library");
      }
      FileOutputStream fileOutputStream = new FileOutputStream(getOutputFile());
      try {
        ByteStreams.copy(jar, fileOutputStream);
      }
      finally {
        try {
          jar.close();
        }
        finally {
          fileOutputStream.close();
        }
      }
    }
  }

  @Override
  public void apply(final Project project) {
    Properties properties = getProperties(project);
    boolean enabled = Boolean.parseBoolean(properties.getProperty(PROPERTY_ENABLED, "false"));
    if (enabled) {
      addProfilersLib(project);
      applyGapidOptions(project, properties);
    }
    // instrumentApp with enabled == false will undo any previous instrumentation.
    instrumentApp(project, enabled);
  }

  private void addProfilersLib(Project project) {
    String path = "build/profilers-gen/profilers-support-lib.jar";
    ConfigurableFileCollection files = project.files(path);
    ExtractSupportLibJarTask task = project.getTasks()
      .create("unpackProfilersLib", ExtractSupportLibJarTask.class);
    task.setOutputFile(project.file(path));
    files.builtBy(task);
    project.getDependencies().add("compile", files);
  }

  private void applyGapidOptions(Project project, Properties properties) {
    if (Boolean.parseBoolean(properties.getProperty(PROPERTY_GAPID_ENABLED, "false"))) {
      String aarFileName = properties.getProperty(PROPERTY_GAPID_TRACER_AAR);
      final File aarFile = (aarFileName == null) ? null : new File(aarFileName);
      if (aarFile != null && aarFile.exists()) {
        RepositoryHandler repositories = project.getRepositories();
        repositories.add(repositories.flatDir(new HashMap<String, Object>() {{
          put("name", "gfxtracer");
          put("dirs", Arrays.asList(aarFile.getParentFile().getAbsolutePath()));
        }}));
        final String baseName = Files.getNameWithoutExtension(aarFileName), extension = Files.getFileExtension(aarFileName);
        project.getDependencies().add("compile", new HashMap<String, String>() {{
          put("name", baseName);
          put("ext", extension);
        }});
      }
    }
  }

  private void instrumentApp(Project project, final boolean enabled) {
    // TODO: The following line won't work for the experimental plugin. For that we may need to
    // register a rule that will get executed at the right time. Investigate this before
    // shipping the plugin.
    Object android = project.getExtensions().getByName("android");
    try {
      Method method = android.getClass()
        .getMethod("registerTransform", Transform.class, Object[].class);
      method.invoke(android, new Transform() {
        @NonNull
        @Override
        public String getName() {
          return "studioprofiler";
        }

        @NonNull
        @Override
        public Set<QualifiedContent.ContentType> getInputTypes() {
          return ImmutableSet.<QualifiedContent.ContentType>of(
            QualifiedContent.DefaultContentType.CLASSES);
        }

        @NonNull
        @Override
        public Set<QualifiedContent.Scope> getScopes() {
          return ImmutableSet.of(QualifiedContent.Scope.PROJECT);
        }

        @Override
        public boolean isIncremental() {
          return true;
        }

        @Override
        public void transform(@NonNull TransformInvocation invocation) throws InterruptedException, IOException {
          assert invocation.getOutputProvider() != null;
          File outputDir = invocation.getOutputProvider().getContentLocation(
            "main", getOutputTypes(), getScopes(), Format.DIRECTORY);
          FileUtils.mkdirs(outputDir);

          for (TransformInput ti : invocation.getInputs()) {
            Preconditions.checkState(ti.getJarInputs().isEmpty());
            for (DirectoryInput di : ti.getDirectoryInputs()) {
              File inputDir = di.getFile();
              if (invocation.isIncremental()) {
                for (Map.Entry<File, Status> entry : di.getChangedFiles()
                  .entrySet()) {
                  File inputFile = entry.getKey();
                  File outputFile = toOutputFile(outputDir, inputDir, inputFile);
                  switch (entry.getValue()) {
                    case ADDED:
                    case CHANGED:
                      instrumentFile(inputFile, outputFile);
                      break;
                    case REMOVED:
                      FileUtils.delete(outputFile);
                      break;
                  }
                }
              }
              else {
                for (File inputFile : FileUtils.getAllFiles(inputDir)) {
                  File outputFile = toOutputFile(outputDir, inputDir, inputFile);
                  instrumentFile(inputFile, outputFile);
                }
              }
            }
          }
        }

        private void instrumentFile(File inputFile, File outputFile) throws IOException {
          Files.createParentDirs(outputFile);
          if (enabled) {
            ApplicationInstrumentor.instrument(inputFile, outputFile);
          }
          else {
            Files.copy(inputFile, outputFile);
          }
        }
      }, new Object[]{});
    }
    catch (NoSuchMethodException e) {
      e.printStackTrace();
    }
    catch (IllegalAccessException e) {
      e.printStackTrace();
    }
    catch (InvocationTargetException e) {
      e.printStackTrace();
    }
  }

  private Properties getProperties(Project project) {
    Map<String, ?> projectProperties = project.getProperties();
    Properties defaults = new Properties();
    for (Map.Entry<String, ?> e : projectProperties.entrySet()) {
      // Properties extends HashTable, which does not support null values.
      if (e.getValue() != null) {
        defaults.put(e.getKey(), e.getValue());
      }
    }
    Properties result = new Properties(defaults);

    Object propertiesFile = projectProperties.get(PROPERTY_PROPERTIES_FILE);
    if (propertiesFile != null) {
      Reader reader = null;
      try {
        reader = new InputStreamReader(new FileInputStream(String.valueOf(propertiesFile)), Charsets.UTF_8);
        result.load(reader);
      }
      catch (IOException e) {
        // Ignored.
        e.printStackTrace();
      }
      finally {
        if (reader != null) {
          Closeables.closeQuietly(reader);
        }
      }
    }

    return result;
  }
}
