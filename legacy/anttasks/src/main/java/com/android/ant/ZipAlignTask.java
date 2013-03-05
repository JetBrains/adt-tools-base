/*
 * Copyright (C) 2011 The Android Open Source Project
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

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.ExecTask;
import org.apache.tools.ant.types.Path;

public class ZipAlignTask extends SingleInputOutputTask {

    private String mExecutable;
    private int mAlign = 4;
    private boolean mVerbose = false;

    /**
     * Sets the value of the "executable" attribute.
     * @param executable the value.
     */
    public void setExecutable(Path executable) {
        mExecutable = TaskHelper.checkSinglePath("executable", executable);
    }

    public void setAlign(int align) {
        mAlign = align;
    }

    public void setVerbose(boolean verbose) {
        mVerbose = verbose;
    }

    @Override
    public void createOutput() throws BuildException {
        if (mExecutable == null) {
            throw new BuildException("Missing attribute executable");
        }

        System.out.println("Running zip align on final apk...");
        doZipAlign();
    }

    private void doZipAlign() {
        ExecTask task = new ExecTask();
        task.setExecutable(mExecutable);
        task.setFailonerror(true);
        task.setProject(getProject());
        task.setOwningTarget(getOwningTarget());

        task.setTaskName("zip-align");

        // force overwrite of existing output file
        task.createArg().setValue("-f");

        // verbose flag
        if (mVerbose) {
            task.createArg().setValue("-v");
        }

        // align value
        task.createArg().setValue(Integer.toString(mAlign));

        // input
        task.createArg().setValue(getInput());

        // output
        task.createArg().setValue(getOutput());

        // execute
        task.execute();
    }
}
