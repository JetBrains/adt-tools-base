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
import org.apache.tools.ant.Task;

/**
 * Task building an emma filter to remove all build-only classes.
 *
 * Currently ignore:
 * app.package.R
 * app.package.R$*
 * app.package.Manifest
 * app.package.BuildConfig
 *
 */
public class GetEmmaFilterTask extends Task {

    private static final String[] FILTER_CLASSES = new String[] {
        "R", "R$*", "Manifest", "BuildConfig"
    };

    private String mAppPackage;
    private String mLibraryPackagesRefId;
    private String mFilterOut;


    public void setAppPackage(String appPackage) {
        mAppPackage = appPackage;
    }

    public void setLibraryPackagesRefId(String libraryPackagesRefId) {
        mLibraryPackagesRefId = libraryPackagesRefId;
    }

    public void setFilterOut(String filterOut) {
        mFilterOut = filterOut;
    }

    @Override
    public void execute() throws BuildException {
        if (mAppPackage == null) {
            throw new BuildException("Missing attribute appPackage");
        }
        if (mLibraryPackagesRefId == null) {
            throw new BuildException("Missing attribute libraryPackagesRefId");
        }
        if (mFilterOut == null) {
            throw new BuildException("Missing attribute filterOut");
        }

        StringBuilder sb = new StringBuilder();

        String libraryPackagesValue = getProject().getProperty(mLibraryPackagesRefId);

        if (libraryPackagesValue != null && libraryPackagesValue.length() > 0) {
            // split the app packages.
            String[] libPackages = libraryPackagesValue.split(";");

            for (String libPackage : libPackages) {
                if (libPackage.length() > 0) {
                    for (String filterClass : FILTER_CLASSES) {
                        sb.append(libPackage).append('.').append(filterClass).append(',');
                    }
                }
            }
        }

        // add the app package:
        final int count = FILTER_CLASSES.length;
        for (int i = 0 ; i < count ; i++) {
            sb.append(mAppPackage).append('.').append(FILTER_CLASSES[i]);
            if (i < count - 1) {
                sb.append(',');
            }
        }

        getProject().setProperty(mFilterOut, sb.toString());
    }
}
