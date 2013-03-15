/*
 * Copyright (C) 2012 The Android Open Source Project
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

/**
 * Custom task to execute lint
 */
public class LintExecTask extends ExecTask {

    private String mExecutable;
    private String mHtml;
    private String mXml;
    private Path mSourcePath;
    private Path mClassPath;

    /**
     * Sets the value of the "executable" attribute.
     * @param executable the value.
     */
    public void setExecutable(Path executable) {
        mExecutable = TaskHelper.checkSinglePath("executable", executable);
    }

    /** Sets the path where Java source code should be found */
    public void setSrc(Path path) {
        mSourcePath = path;
    }

    /** Sets the path where class files should be found */
    public void setClasspath(Path path) {
        mClassPath = path;
    }

    /**
     * Sets the value of the "html" attribute: a path to a file or directory name
     * where the HTML report should be written.
     *
     * @param html path to the html report
     */
    public void setHtml(Path html) {
        mHtml = TaskHelper.checkSinglePath("html", html);
    }

    /**
     * Sets the value of the "xml" attribute: a path to a file or directory name
     * where the XML report should be written.
     *
     * @param xml path to the xml report
     */
    public void setXml(Path xml) {
        mXml = TaskHelper.checkSinglePath("xml", xml);
    }

    @Override
    public void execute() throws BuildException {

        ExecTask task = new ExecTask();
        task.setProject(getProject());
        task.setOwningTarget(getOwningTarget());
        task.setExecutable(mExecutable);
        task.setTaskName("lint");
        task.setFailonerror(true);

        task.createArg().setValue("--text");
        task.createArg().setValue("stdout");

        if (mHtml != null) {
            task.createArg().setValue("--html");
            task.createArg().setValue(mHtml);
        }

        if (mXml != null) {
            task.createArg().setValue("--xml");
            task.createArg().setValue(mXml);
        }

        if (mSourcePath != null) {
            task.createArg().setValue("--sources");
            task.createArg().setValue(mSourcePath.toString());
        }

        if (mClassPath != null) {
            task.createArg().setValue("--classpath");
            task.createArg().setValue(mClassPath.toString());
        }

        task.createArg().setValue(getProject().getBaseDir().getAbsolutePath());
        task.execute();
    }
}
