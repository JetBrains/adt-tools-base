/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.tasks;

import static com.android.SdkConstants.CURRENT_PLATFORM;
import static com.android.SdkConstants.PLATFORM_WINDOWS;

import com.android.annotations.NonNull;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.internal.dsl.CoreNdkOptions;
import com.android.build.gradle.internal.tasks.NdkTask;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.util.ReferenceHolder;
import com.android.sdklib.IAndroidTarget;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.gradle.api.tasks.util.PatternSet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@ParallelizableTask
public class NdkCompile extends NdkTask {

    private List<File> sourceFolders;
    private File generatedMakefile;

    private boolean debuggable;

    private File soFolder;

    private File objFolder;

    private File ndkDirectory;

    private boolean ndkRenderScriptMode;

    private boolean ndkCygwinMode;

    private boolean isForTesting;

    public List<File> getSourceFolders() {
        return sourceFolders;
    }

    public void setSourceFolders(List<File> sourceFolders) {
        this.sourceFolders = sourceFolders;
    }

    @OutputFile
    public File getGeneratedMakefile() {
        return generatedMakefile;
    }

    public void setGeneratedMakefile(File generatedMakefile) {
        this.generatedMakefile = generatedMakefile;
    }

    @Input
    public boolean isDebuggable() {
        return debuggable;
    }

    public void setDebuggable(boolean debuggable) {
        this.debuggable = debuggable;
    }

    @OutputDirectory
    public File getSoFolder() {
        return soFolder;
    }

    public void setSoFolder(File soFolder) {
        this.soFolder = soFolder;
    }

    @OutputDirectory
    public File getObjFolder() {
        return objFolder;
    }

    public void setObjFolder(File objFolder) {
        this.objFolder = objFolder;
    }

    @Optional
    @Input
    public File getNdkDirectory() {
        return ndkDirectory;
    }

    public void setNdkDirectory(File ndkDirectory) {
        this.ndkDirectory = ndkDirectory;
    }

    @Input
    public boolean isNdkRenderScriptMode() {
        return ndkRenderScriptMode;
    }

    public void setNdkRenderScriptMode(boolean ndkRenderScriptMode) {
        this.ndkRenderScriptMode = ndkRenderScriptMode;
    }

    @Input
    public boolean isNdkCygwinMode() {
        return ndkCygwinMode;
    }

    public void setNdkCygwinMode(boolean ndkCygwinMode) {
        this.ndkCygwinMode = ndkCygwinMode;
    }

    @Input
    public boolean isForTesting() {
        return isForTesting;
    }

    public void setForTesting(boolean forTesting) {
        isForTesting = forTesting;
    }

    @SkipWhenEmpty
    @InputFiles
    FileTree getSource() {
        FileTree src = null;
        List<File> sources = getSourceFolders();
        if (!sources.isEmpty()) {
            src = getProject().files(new ArrayList<Object>(sources)).getAsFileTree();
        }
        return src == null ? getProject().files().getAsFileTree() : src;
    }

    @TaskAction
    void taskAction(IncrementalTaskInputs inputs) throws IOException, ProcessException {
         if (!AndroidGradleOptions.useDeprecatedNdk(getProject())) {
             // Normally, we would catch the user when they try to configure the NDK, but NDK do
             // not need to be configured by default.  Throw this exception during task execution in
             // case we miss it.
             throw new RuntimeException(
                     "Error: NDK integration is deprecated in the current plugin.  Consider trying " +
                             "the new experimental plugin.  For details, see " +
                             "http://tools.android.com/tech-docs/new-build-system/gradle-experimental.  " +
                             "Set \"$USE_DEPRECATED_NDK=true\" in gradle.properties to " +
                             "continue using the current NDK integration.");
         }


        if (isNdkOptionUnset()) {
            getLogger().warn("Warning: Native C/C++ source code is found, but it seems that NDK " +
                    "option is not configured.  Note that if you have an Android.mk, it is not " +
                    "used for compilation.  The recommended workaround is to remove the default " +
                    "jni source code directory by adding: \n " +
                    "android {\n" +
                    "    sourceSets {\n" +
                    "        main {\n" +
                    "            jni.srcDirs = []\n" +
                    "        }\n" +
                    "    }\n" +
                    "}\n" +
                    "to build.gradle, manually compile the code with ndk-build, " +
                    "and then place the resulting shared object in src/main/jniLibs.");
        }

        FileTree sourceFileTree = getSource();
        Set<File> sourceFiles =
                sourceFileTree.matching(new PatternSet().exclude("**/*.h")).getFiles();
        File makefile = getGeneratedMakefile();

        if (sourceFiles.isEmpty()) {
            makefile.delete();
            FileUtils.cleanOutputDir(getSoFolder());
            FileUtils.cleanOutputDir(getObjFolder());
            return;
        }

        if (ndkDirectory == null || !ndkDirectory.isDirectory()) {
            throw new GradleException(
                    "NDK not configured.\n" +
                    "Download the NDK from http://developer.android.com/tools/sdk/ndk/." +
                    "Then add ndk.dir=path/to/ndk in local.properties.\n" +
                    "(On Windows, make sure you escape backslashes, e.g. C:\\\\ndk rather than C:\\ndk)");
        }

        final ReferenceHolder<Boolean> generateMakeFile = ReferenceHolder.of(false);

        if (!inputs.isIncremental()) {
            getLogger().info("Unable do incremental execution: full task run");
            generateMakeFile.setValue(true);
            FileUtils.cleanOutputDir(getSoFolder());
            FileUtils.cleanOutputDir(getObjFolder());
        } else {
            // look for added or removed files *only*

            inputs.outOfDate(new Action<InputFileDetails>() {
                @Override
                public void execute(InputFileDetails change) {
                    if (change.isAdded()) {
                        generateMakeFile.setValue(true);
                    }
                }
            });

            inputs.removed(new Action<InputFileDetails>() {
                @Override
                public void execute(InputFileDetails change) {
                    generateMakeFile.setValue(true);
                }
            });

        }

        if (generateMakeFile.getValue()) {
            writeMakefile(sourceFiles, makefile);
        }

        // now build
        runNdkBuild(ndkDirectory, makefile);
    }

    private void writeMakefile(@NonNull Set<File> sourceFiles, @NonNull File makefile)
            throws IOException {
        CoreNdkOptions ndk = getNdkConfig();
        Preconditions.checkNotNull(ndk, "Ndk config should be set");

        StringBuilder sb = new StringBuilder();

        sb.append("LOCAL_PATH := $(call my-dir)\n" +
                "include $(CLEAR_VARS)\n\n");

        String moduleName = ndk.getModuleName() != null ? ndk.getModuleName() : getProject().getName();
        if (isForTesting) {
            moduleName = moduleName + "_test";
        }

        sb.append("LOCAL_MODULE := ").append(moduleName).append('\n');

        if (ndk.getcFlags() != null) {
            sb.append("LOCAL_CFLAGS := ").append(ndk.getcFlags()).append('\n');
        }

        // To support debugging from Android Studio.
        sb.append("LOCAL_LDFLAGS := -Wl,--build-id\n");

        List<String> fullLdlibs = Lists.newArrayList();
        if (ndk.getLdLibs() != null) {
            fullLdlibs.addAll(ndk.getLdLibs());
        }
        if (isNdkRenderScriptMode()) {
            fullLdlibs.add("dl");
            fullLdlibs.add("log");
            fullLdlibs.add("jnigraphics");
            fullLdlibs.add("RScpp_static");
        }

        if (!fullLdlibs.isEmpty()) {
            sb.append("LOCAL_LDLIBS := \\\n");
            for (String lib : fullLdlibs) {
                sb.append("\t-l").append(lib).append(" \\\n");
            }
            sb.append('\n');
        }

        sb.append("LOCAL_SRC_FILES := \\\n");
        for (File sourceFile : sourceFiles) {
            sb.append('\t').append(sourceFile.getAbsolutePath()).append(" \\\n");
        }
        sb.append('\n');

        for (File sourceFolder : getSourceFolders()) {
            sb.append("LOCAL_C_INCLUDES += ").append(sourceFolder.getAbsolutePath()).append('\n');
        }

        if (isNdkRenderScriptMode()) {
            sb.append("LOCAL_LDFLAGS += -L$(call host-path,$(TARGET_C_INCLUDES)/../lib/rs)\n");

            sb.append("LOCAL_C_INCLUDES += $(TARGET_C_INCLUDES)/rs/cpp\n");
            sb.append("LOCAL_C_INCLUDES += $(TARGET_C_INCLUDES)/rs\n");
            sb.append("LOCAL_C_INCLUDES += $(TARGET_OBJS)/$(LOCAL_MODULE)\n");
        }

        sb.append("\ninclude $(BUILD_SHARED_LIBRARY)\n");

        Files.write(sb.toString(), makefile, Charsets.UTF_8);
    }

    private void runNdkBuild(@NonNull File ndkLocation, @NonNull File makefile)
            throws ProcessException {
        CoreNdkOptions ndk = getNdkConfig();

        ProcessInfoBuilder builder = new ProcessInfoBuilder();

        String exe = ndkLocation.getAbsolutePath() + File.separator + "ndk-build";
        if (CURRENT_PLATFORM == PLATFORM_WINDOWS && !ndkCygwinMode) {
            exe += ".cmd";
        }
        builder.setExecutable(exe);

        builder.addArgs(
                "NDK_PROJECT_PATH=null",
                "APP_BUILD_SCRIPT=" + makefile.getAbsolutePath());

        // target
        IAndroidTarget target = getBuilder().getTarget();
        if (!target.isPlatform()) {
            target = target.getParent();
        }
        builder.addArgs("APP_PLATFORM=" + target.hashString());

        // temp out
        builder.addArgs("NDK_OUT=" + getObjFolder().getAbsolutePath());

        // libs out
        builder.addArgs("NDK_LIBS_OUT=" + getSoFolder().getAbsolutePath());

        // debug builds
        if (isDebuggable()) {
            builder.addArgs("NDK_DEBUG=1");
        }

        if (ndk.getStl() != null) {
            builder.addArgs("APP_STL=" + ndk.getStl());
        }

        Set<String> abiFilters = ndk.getAbiFilters();
        if (abiFilters != null && !abiFilters.isEmpty()) {
            if (abiFilters.size() == 1) {
                builder.addArgs("APP_ABI=" + abiFilters.iterator().next());
            } else {
                Joiner joiner = Joiner.on(',').skipNulls();
                builder.addArgs("APP_ABI=" + joiner.join(abiFilters.iterator()));
            }
        } else {
            builder.addArgs("APP_ABI=all");
        }

        if (ndk.getJobs() != null) {
            builder.addArgs("-j" + ndk.getJobs());
        }

        ProcessOutputHandler handler = new LoggedProcessOutputHandler(getBuilder().getLogger());
        getBuilder().executeProcess(builder.createProcess(), handler)
                .rethrowFailure().assertNormalExitValue();
    }

    private boolean isNdkOptionUnset() {
        // If none of the NDK options are set, then it is likely that NDK is not configured.
        return (getModuleName() == null &&
                getcFlags() == null &&
                getLdLibs() == null &&
                getAbiFilters() == null &&
                getStl() == null);
    }
}
