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

import org.apache.tools.ant.Task;

/**
 * Base class for tasks that should exec when the build type change.
 */
public abstract class BuildTypedTask extends Task {

    private String mPreviousBuildType;
    private String mBuildType;

    /** Sets the current build type */
    public void setBuildType(String buildType) {
        mBuildType = buildType;
    }

    /** Sets the previous build type */
    public void setPreviousBuildType(String previousBuildType) {
        mPreviousBuildType = previousBuildType;
    }

    protected String getBuildType() {
        return mBuildType;
    }

    /**
     * Returns if it is a new build. If the build type is not input
     * from the XML, this always returns true.
     * A build type is defined by having an empty previousBuildType.
     */
    protected boolean isNewBuild() {
        return mBuildType == null || mPreviousBuildType.length() == 0;
    }

    /**
     * Returns true if the build type changed.
     */
    protected boolean hasBuildTypeChanged() {
        // no build type? return false as the feature is simply not used
        if (mBuildType == null && mPreviousBuildType == null) {
            return false;
        }

        return mBuildType.equals(mPreviousBuildType) == false;
    }
}
