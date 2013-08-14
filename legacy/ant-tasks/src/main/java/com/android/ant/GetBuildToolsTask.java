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

package com.android.ant;

import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.repository.FullRevision;
import com.android.utils.StdLogger;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

public class GetBuildToolsTask extends Task {

    private String mName;
    private boolean mVerbose = false;

    public void setName(String name) {
        mName = name;
    }

    public void setVerbose(boolean verbose) {
        mVerbose = verbose;
    }

    @Override
    public void execute() throws BuildException {
        Project antProject = getProject();

        SdkManager sdkManager = SdkManager.createManager(
                antProject.getProperty(ProjectProperties.PROPERTY_SDK),
                new StdLogger(mVerbose ?  StdLogger.Level.VERBOSE : StdLogger.Level.ERROR));

        if (sdkManager == null) {
            throw new BuildException("Unable to parse the SDK!");
        }

        BuildToolInfo buildToolInfo = null;

        String buildToolsVersion = antProject.getProperty(ProjectProperties.PROPERTY_BUILD_TOOLS);
        if (buildToolsVersion != null) {
            buildToolInfo = sdkManager.getBuildTool(FullRevision.parseRevision(buildToolsVersion));

            if (buildToolInfo == null) {
                throw new BuildException(
                        "Could not find Build Tools revision " + buildToolsVersion);
            }
        }

        if (buildToolInfo == null) {
            // get the latest one instead
            buildToolInfo = sdkManager.getLatestBuildTool();

            if (buildToolInfo == null) {
                throw new BuildException("SDK does not have any Build Tools installed.");
            }

            System.out.println("Using latest Build Tools: " + buildToolInfo.getRevision());
        }

        antProject.setProperty(mName, buildToolInfo.getLocation().getAbsolutePath());
    }
}
