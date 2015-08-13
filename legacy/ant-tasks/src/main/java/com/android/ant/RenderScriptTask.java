/*
 * Copyright (C) 2010, 2012 The Android Open Source Project
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

package com.android.ant;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.build.ManualRenderScriptChecker;
import com.android.sdklib.build.RenderScriptProcessor;
import com.android.sdklib.repository.FullRevision;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.ExecTask;
import org.apache.tools.ant.types.Environment;
import org.apache.tools.ant.types.Path;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Task to execute renderscript.
 * <p>
 * It expects 7 attributes:<br>
 * 'buildToolsRoot' ({@link Path} with a single path) for the location of the build tools<br>
 * 'framework' ({@link Path} with 1 or more paths) for the include paths.<br>
 * 'genFolder' ({@link Path} with a single path) for the location of the gen folder.<br>
 * 'resFolder' ({@link Path} with a single path) for the location of the res folder.<br>
 * 'targetApi' for the -target-api value.<br>
 * 'optLevel' for the -O optimization level.<br>
 * 'debug' for -g renderscript debugging.<br>
 * <p>
 * It also expects one or more inner elements called "source" which are identical to {@link Path}
 * elements for where to find .rs files.
 */
public class RenderScriptTask extends BuildTypedTask {

    private static final Set<String> EXTENSIONS = Sets.newHashSetWithExpectedSize(2);
    static {
        EXTENSIONS.add(SdkConstants.EXT_RS);
        EXTENSIONS.add(SdkConstants.EXT_FS);
    }


    private String mBuildToolsRoot;
    private String mGenFolder;
    private String mResFolder;
    private String mRsObjFolder;
    private String mLibsFolder;
    private String mBinFolder;
    private final List<Path> mPaths = new ArrayList<Path>();
    private int mTargetApi = 0;
    private boolean mSupportMode = false;

    public enum OptLevel { O0, O1, O2, O3 };

    private OptLevel mOptLevel;
    private boolean mDebug = false;

    /**
     * Sets the value of the "buildToolsRoot" attribute.
     * @param buildToolsRoot the value.
     */
    public void setBuildToolsRoot(Path buildToolsRoot) {
        mBuildToolsRoot = TaskHelper.checkSinglePath("buildToolsRoot", buildToolsRoot);
    }

    public void setGenFolder(Path value) {
        mGenFolder = TaskHelper.checkSinglePath("genFolder", value);
    }

    public void setResFolder(Path value) {
        mResFolder = TaskHelper.checkSinglePath("resFolder", value);
    }

    public void setRsObjFolder(Path value) {
        mRsObjFolder = TaskHelper.checkSinglePath("rsObjFolder", value);
    }

    public void setLibsFolder(Path value) {
        mLibsFolder = TaskHelper.checkSinglePath("libsFolder", value);
    }

    public void setTargetApi(String targetApi) {
        try {
            mTargetApi = Integer.parseInt(targetApi);
            if (mTargetApi <= 0) {
                throw new BuildException("targetApi attribute value must be >= 1");
            }
        } catch (NumberFormatException e) {
            throw new BuildException("targetApi attribute value must be an integer", e);
        }
    }

    public void setOptLevel(OptLevel optLevel) {
        mOptLevel = optLevel;
    }

    public void setSupportMode(boolean supportMode) {
        mSupportMode = supportMode;
    }

    public void setBinFolder(Path binFolder) {
        mBinFolder = TaskHelper.checkSinglePath("binFolder", binFolder);
    }

    /** Sets the current build type. value is a boolean, true for debug build, false for release */
    @Override
    public void setBuildType(String buildType) {
        super.setBuildType(buildType);
        mDebug = Boolean.valueOf(buildType);
    }

    public Path createSource() {
        Path p = new Path(getProject());
        mPaths.add(p);
        return p;
    }

    @Override
    public void execute() throws BuildException {
        if (mBuildToolsRoot == null) {
            throw new BuildException("RenderScriptTask's 'buildToolsRoot' is required.");
        }
        if (mGenFolder == null) {
            throw new BuildException("RenderScriptTask's 'genFolder' is required.");
        }
        if (mResFolder == null) {
            throw new BuildException("RenderScriptTask's 'resFolder' is required.");
        }
        if (mRsObjFolder == null) {
            throw new BuildException("RenderScriptTask's 'rsObjFolder' is required.");
        }
        if (mLibsFolder == null) {
            throw new BuildException("RenderScriptTask's 'libsFolder' is required.");
        }
        if (mTargetApi == 0) {
            throw new BuildException("RenderScriptTask's 'targetApi' is required.");
        }
        if (mBinFolder == null) {
            throw new BuildException("RenderScriptTask's 'binFolder' is required.");
        }

        // convert the Path to List<File>
        List<File> sourceFolders = Lists.newArrayList();
        for (Path path : mPaths) {
            String[] values = path.list();
            if (values != null) {
                for (String p : values) {
                    sourceFolders.add(new File(p));
                }
            }
        }

        try {
            File binFile = new File(mBinFolder);

            ManualRenderScriptChecker checker = new ManualRenderScriptChecker(
                    sourceFolders, binFile);

            if (checker.mustCompile() || isNewBuild() || hasBuildTypeChanged()) {

                checker.cleanDependencies();

                List<File> emptyFileList = Collections.emptyList();

                RenderScriptProcessor processor = new RenderScriptProcessor(
                        checker.getInputFiles(),
                        emptyFileList,
                        binFile,
                        new File(mGenFolder),
                        new File(mResFolder),
                        new File(mRsObjFolder),
                        new File(mLibsFolder),
                        new BuildToolInfo(new FullRevision(0), new File(mBuildToolsRoot)),
                        mTargetApi,
                        mDebug,
                        mOptLevel.ordinal(),
                        mSupportMode);

                // clean old files first
                processor.cleanOldOutput(checker.getOldOutputs());

                // do the compilation(s).
                processor.build(new RenderScriptProcessor.CommandLineLauncher() {
                    @Override
                    public void launch(
                            @NonNull File executable,
                            @NonNull List<String> arguments,
                            @NonNull Map<String, String> envVariableMap)
                            throws IOException, InterruptedException {

                        ExecTask task = new ExecTask();
                        task.setTaskName(executable.getName());
                        task.setProject(getProject());
                        task.setOwningTarget(getOwningTarget());
                        task.setExecutable(executable.getAbsolutePath());
                        task.setFailonerror(true);

                        // create the env var for the dynamic libraries
                        for (Map.Entry<String, String> entry : envVariableMap.entrySet()) {
                            Environment.Variable var = new Environment.Variable();
                            var.setKey(entry.getKey());
                            var.setValue(entry.getValue());
                            task.addEnv(var);
                        }

                        for (String arg : arguments) {
                            task.createArg().setValue(arg);
                        }

                        System.out.println(String.format(
                                "COMMAND: %s %s",
                                executable.getAbsolutePath(), Joiner.on(' ').join(arguments)));

                        task.execute();
                    }
                });
            }
        } catch (IOException e) {
            throw new BuildException(e);
        } catch (InterruptedException e) {
            throw new BuildException(e);
        }
    }
}
