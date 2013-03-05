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

import com.android.sdklib.internal.build.BuildConfigGenerator;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.types.Path;

import java.io.File;
import java.io.IOException;

public class BuildConfigTask extends BuildTypedTask {

    private String mGenFolder;
    private String mAppPackage;

    public void setGenFolder(Path path) {
        mGenFolder = TaskHelper.checkSinglePath("genFolder", path);
    }

    public void setPackage(String appPackage) {
        mAppPackage = appPackage;
    }


    @Override
    public void execute() throws BuildException {
        if (mGenFolder == null) {
            throw new BuildException("Missing attribute genFolder");
        }
        if (mAppPackage == null) {
            throw new BuildException("Missing attribute package");
        }

        BuildConfigGenerator generator = new BuildConfigGenerator(
                mGenFolder, mAppPackage,
                Boolean.parseBoolean(getBuildType()));

        // first check if the file is missing.
        File buildConfigFile = generator.getBuildConfigFile();
        boolean missingFile = buildConfigFile.exists() == false;

        if (missingFile || hasBuildTypeChanged()) {
            if (isNewBuild()) {
                System.out.println("Generating BuildConfig class.");
            } else if (missingFile) {
                System.out.println("BuildConfig class missing: Generating new BuildConfig class.");
            } else {
                System.out.println("Build type changed: Generating new BuildConfig class.");
            }

            try {
                generator.generate();
            } catch (IOException e) {
                throw new BuildException("Failed to create BuildConfig class", e);
            }
        } else {
            System.out.println("No need to generate new BuildConfig.");
        }
    }
}
