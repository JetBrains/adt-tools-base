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

import com.android.SdkConstants;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.util.DeweyDecimal;

import java.io.File;

/**
 * Checks the Ant environment to make sure Android builds
 * can run.
 *
 * No parameters are neeed.
 *
 */
public class CheckEnvTask extends Task {

    private final static String ANT_MIN_VERSION = "1.8.0";

    @Override
    public void execute() {

        Project antProject = getProject();

        // check the Ant version
        DeweyDecimal version = getAntVersion(antProject);
        DeweyDecimal atLeast = new DeweyDecimal(ANT_MIN_VERSION);
        if (atLeast.isGreaterThan(version)) {
            throw new BuildException(
                    "The Android Ant-based build system requires Ant " +
                    ANT_MIN_VERSION +
                    " or later. Current version is " +
                    version);
        }

        // get the SDK location
        File sdkDir = TaskHelper.getSdkLocation(antProject);

        // detect that the platform tools is there.
        File platformTools = new File(sdkDir, SdkConstants.FD_PLATFORM_TOOLS);
        if (platformTools.isDirectory() == false) {
            throw new BuildException(String.format(
                    "SDK Platform Tools component is missing. " +
                    "Please install it with the SDK Manager (%1$s%2$c%3$s)",
                    SdkConstants.FD_TOOLS,
                    File.separatorChar,
                    SdkConstants.androidCmdName()));
        }

        // display SDK Tools revision
        DeweyDecimal toolsRevison = TaskHelper.getToolsRevision(sdkDir);
        if (toolsRevison != null) {
            System.out.println("Android SDK Tools Revision " + toolsRevison);
            System.out.println("Installed at " + sdkDir.getAbsolutePath());
        }
    }

    /**
     * Returns the Ant version as a {@link DeweyDecimal} object.
     *
     * This is based on the implementation of
     * org.apache.tools.ant.taskdefs.condition.AntVersion.getVersion()
     *
     * @param antProject the current ant project.
     * @return the ant version.
     */
    private DeweyDecimal getAntVersion(Project antProject) {
        char[] versionString = antProject.getProperty("ant.version").toCharArray();
        StringBuilder sb = new StringBuilder();
        boolean foundFirstDigit = false;
        for (int i = 0; i < versionString.length; i++) {
            if (Character.isDigit(versionString[i])) {
                sb.append(versionString[i]);
                foundFirstDigit = true;
            }
            if (versionString[i] == '.' && foundFirstDigit) {
                sb.append(versionString[i]);
            }
            if (Character.isLetter(versionString[i]) && foundFirstDigit) {
                break;
            }
        }
        return new DeweyDecimal(sb.toString());
    }

}
