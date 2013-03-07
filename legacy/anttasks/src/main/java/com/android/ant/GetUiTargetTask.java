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

import com.android.annotations.NonNull;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.IAndroidTarget.IOptionalLibrary;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.utils.ILogger;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Path.PathElement;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Task to resolve the target of the current Android uiautomator project.
 *
 * Out params:
 * <code>compileClassPathOut</code>: The compile class path for the project.
 */
public class GetUiTargetTask extends Task {

    private String mCompileClassPathOut;

    public void setCompileClassPathOut(String compileClassPathOut) {
        mCompileClassPathOut = compileClassPathOut;
    }

    @Override
    public void execute() throws BuildException {
        if (mCompileClassPathOut == null) {
            throw new BuildException("Missing attribute compileClassPathOut");
        }

        Project antProject = getProject();

        // get the SDK location
        File sdkDir = TaskHelper.getSdkLocation(antProject);

        // get the target property value
        String targetHashString = antProject.getProperty(ProjectProperties.PROPERTY_TARGET);

        if (targetHashString == null) {
            throw new BuildException("Android Target is not set.");
        }

        // load up the sdk targets.
        final ArrayList<String> messages = new ArrayList<String>();
        SdkManager manager = SdkManager.createManager(sdkDir.getPath(), new ILogger() {
            @Override
            public void error(Throwable t, String errorFormat, Object... args) {
                if (errorFormat != null) {
                    messages.add(String.format("Error: " + errorFormat, args));
                }
                if (t != null) {
                    messages.add("Error: " + t.getMessage());
                }
            }

            @Override
            public void info(@NonNull String msgFormat, Object... args) {
                messages.add(String.format(msgFormat, args));
            }

            @Override
            public void verbose(@NonNull String msgFormat, Object... args) {
                info(msgFormat, args);
            }

            @Override
            public void warning(@NonNull String warningFormat, Object... args) {
                messages.add(String.format("Warning: " + warningFormat, args));
            }
        });

        if (manager == null) {
            // since we failed to parse the SDK, lets display the parsing output.
            for (String msg : messages) {
                System.out.println(msg);
            }
            throw new BuildException("Failed to parse SDK content.");
        }

        // resolve it
        IAndroidTarget androidTarget = manager.getTargetFromHashString(targetHashString);

        if (androidTarget == null) {
            throw new BuildException(String.format(
                    "Unable to resolve project target '%s'", targetHashString));
        }

        // display the project info
        System.out.println(    "Project Target:   " + androidTarget.getName());
        if (androidTarget.isPlatform() == false) {
            System.out.println("Vendor:           " + androidTarget.getVendor());
            System.out.println("Platform Version: " + androidTarget.getVersionName());
        }
        System.out.println(    "API level:        " + androidTarget.getVersion().getApiString());

        if (androidTarget.getVersion().getApiLevel() < 16) {
            throw new BuildException("UI Automator requires API 16");
        }

        // sets up the properties to find android.jar/framework.aidl/target tools
        String androidJar = androidTarget.getPath(IAndroidTarget.ANDROID_JAR);
        String uiAutomatorJar = androidTarget.getPath(IAndroidTarget.UI_AUTOMATOR_JAR);

        // sets up the boot classpath

        // create the Path object
        Path compileclasspath = new Path(antProject);

        // create a PathElement for the framework jars
        PathElement element = compileclasspath.createPathElement();
        element.setPath(androidJar);

        element = compileclasspath.createPathElement();
        element.setPath(uiAutomatorJar);

        // create PathElement for each optional library.
        IOptionalLibrary[] libraries = androidTarget.getOptionalLibraries();
        if (libraries != null) {
            HashSet<String> visitedJars = new HashSet<String>();
            for (IOptionalLibrary library : libraries) {
                String jarPath = library.getJarPath();
                if (visitedJars.contains(jarPath) == false) {
                    visitedJars.add(jarPath);

                    element = compileclasspath.createPathElement();
                    element.setPath(jarPath);
                }
            }
        }

        // sets the path in the project with a reference
        antProject.addReference(mCompileClassPathOut, compileclasspath);
    }
}
