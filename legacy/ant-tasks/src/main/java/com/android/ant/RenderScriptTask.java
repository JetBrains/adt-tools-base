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
import com.google.common.collect.Sets;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.ExecTask;
import org.apache.tools.ant.types.Path;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Task to execute renderscript.
 * <p>
 * It expects 7 attributes:<br>
 * 'executable' ({@link Path} with a single path) for the location of the llvm executable<br>
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
public class RenderScriptTask extends MultiFilesTask {

    private final static Set<String> EXTENSIONS = Sets.newHashSetWithExpectedSize(2);
    static {
        EXTENSIONS.add(SdkConstants.EXT_RS);
        EXTENSIONS.add(SdkConstants.EXT_FS);
    }

    private String mExecutable;
    private Path mIncludePath;
    private String mGenFolder;
    private String mResFolder;
    private final List<Path> mPaths = new ArrayList<Path>();
    private int mTargetApi = 0;
    public enum OptLevel { O0, O1, O2, O3 };
    private OptLevel mOptLevel;
    private boolean mDebug = false;

    private class RenderScriptProcessor implements SourceProcessor {

        private final String mTargetApiStr;

        public RenderScriptProcessor(int targetApi) {
            // get the target api value. Must be 11+ or llvm-rs-cc complains.
            mTargetApiStr = Integer.toString(mTargetApi < 11 ? 11 : mTargetApi);
        }

        @Override
        public Set<String> getSourceFileExtensions() {
            return EXTENSIONS;
        }

        @Override
        public void process(String filePath, String sourceFolder, List<String> sourceFolders,
                Project taskProject) {
            File exe = new File(mExecutable);
            String execTaskName = exe.getName();

            ExecTask task = new ExecTask();
            task.setTaskName(execTaskName);
            task.setProject(taskProject);
            task.setOwningTarget(getOwningTarget());
            task.setExecutable(mExecutable);
            task.setFailonerror(true);

            for (String path : mIncludePath.list()) {
                File res = new File(path);
                if (res.isDirectory()) {
                    task.createArg().setValue("-I");
                    task.createArg().setValue(path);
                } else {
                    System.out.println(String.format(
                            "WARNING: RenderScript include directory '%s' does not exist!",
                            res.getAbsolutePath()));
                }

            }

            if (mDebug) {
                task.createArg().setValue("-g");
            }

            task.createArg().setValue("-O");
            task.createArg().setValue(Integer.toString(mOptLevel.ordinal()));

            task.createArg().setValue("-target-api");
            task.createArg().setValue(mTargetApiStr);

            task.createArg().setValue("-d");
            task.createArg().setValue(getDependencyFolder(filePath, sourceFolder));
            task.createArg().setValue("-MD");

            task.createArg().setValue("-p");
            task.createArg().setValue(mGenFolder);
            task.createArg().setValue("-o");
            task.createArg().setValue(mResFolder);
            task.createArg().setValue(filePath);

            // execute it.
            task.execute();
        }

        @Override
        public void displayMessage(DisplayType type, int count) {
            switch (type) {
                case FOUND:
                    System.out.println(String.format("Found %1$d RenderScript files.", count));
                    break;
                case COMPILING:
                    if (count > 0) {
                        System.out.println(String.format(
                                "Compiling %1$d RenderScript files with -target-api %2$d",
                                count, mTargetApi));
                        System.out.println(String.format("Optimization Level: %1$d", mOptLevel.ordinal()));
                    } else {
                        System.out.println("No RenderScript files to compile.");
                    }
                    break;
                case REMOVE_OUTPUT:
                    System.out.println(String.format("Found %1$d obsolete output files to remove.",
                            count));
                    break;
                case REMOVE_DEP:
                    System.out.println(
                            String.format("Found %1$d obsolete dependency files to remove.",
                                    count));
                    break;
            }
        }

        private String getDependencyFolder(String filePath, String sourceFolder) {
            String relative = filePath.substring(sourceFolder.length());
            if (relative.charAt(0) == '/') {
                relative = relative.substring(1);
            }

            return new File(mGenFolder, relative).getParent();
        }
    }


    /**
     * Sets the value of the "executable" attribute.
     * @param executable the value.
     */
    public void setExecutable(Path executable) {
        mExecutable = TaskHelper.checkSinglePath("executable", executable);
    }

    public void setIncludePathRefId(String refId) {
        Object path = getProject().getReference(refId);
        if (path instanceof Path) {
            mIncludePath = (Path) path;
        } else if (path != null) {
            throw new BuildException(refId + " is expected to reference a Path object.");
        }
    }

    public void setGenFolder(Path value) {
        mGenFolder = TaskHelper.checkSinglePath("genFolder", value);
    }

    public void setResFolder(Path value) {
        mResFolder = TaskHelper.checkSinglePath("resFolder", value);
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
        if (mExecutable == null) {
            throw new BuildException("RenderScriptTask's 'executable' is required.");
        }
        if (mIncludePath == null) {
            throw new BuildException("RenderScriptTask's 'includePath' is required.");
        }
        if (mGenFolder == null) {
            throw new BuildException("RenderScriptTask's 'genFolder' is required.");
        }
        if (mResFolder == null) {
            throw new BuildException("RenderScriptTask's 'resFolder' is required.");
        }
        if (mTargetApi == 0) {
            throw new BuildException("RenderScriptTask's 'targetApi' is required.");
        }

        processFiles(new RenderScriptProcessor(mTargetApi), mPaths, mGenFolder);
    }
}
