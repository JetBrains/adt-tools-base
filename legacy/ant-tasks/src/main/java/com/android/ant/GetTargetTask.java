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
import com.android.annotations.NonNull;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.IAndroidTarget.IOptionalLibrary;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.utils.ILogger;
import com.android.xml.AndroidManifest;
import com.android.xml.AndroidXPathFactory;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Path.PathElement;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;

/**
 * Task to resolve the target of the current Android project.
 *
 * Out params:
 * <code>bootClassPathOut</code>: The boot class path of the project.
 *
 * <code>androidJarFileOut</code>: the android.jar used by the project.
 *
 * <code>androidAidlFileOut</code>: the framework.aidl used by the project.
 *
 * <code>targetApiOut</code>: the build API level.
 *
 * <code>minSdkVersionOut</code>: the app's minSdkVersion.
 *
 */
public class GetTargetTask extends Task {

    private String mBootClassPathOut;
    private String mAndroidJarFileOut;
    private String mAndroidAidlFileOut;
    private String mTargetApiOut;
    private String mMinSdkVersionOut;

    public void setBootClassPathOut(String bootClassPathOut) {
        mBootClassPathOut = bootClassPathOut;
    }

    public void setAndroidJarFileOut(String androidJarFileOut) {
        mAndroidJarFileOut = androidJarFileOut;
    }

    public void setAndroidAidlFileOut(String androidAidlFileOut) {
        mAndroidAidlFileOut = androidAidlFileOut;
    }

    public void setTargetApiOut(String targetApiOut) {
        mTargetApiOut = targetApiOut;
    }

    public void setMinSdkVersionOut(String minSdkVersionOut) {
        mMinSdkVersionOut = minSdkVersionOut;
    }

    @Override
    public void execute() throws BuildException {
        if (mBootClassPathOut == null) {
            throw new BuildException("Missing attribute bootClassPathOut");
        }
        if (mAndroidJarFileOut == null) {
            throw new BuildException("Missing attribute androidJarFileOut");
        }
        if (mAndroidAidlFileOut == null) {
            throw new BuildException("Missing attribute androidAidlFileOut");
        }
        if (mTargetApiOut == null) {
            throw new BuildException("Missing attribute targetApiOut");
        }
        if (mMinSdkVersionOut == null) {
            throw new BuildException("Missing attribute mMinSdkVersionOut");
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

        antProject.setProperty(mTargetApiOut,
                Integer.toString(androidTarget.getVersion().getApiLevel()));

        // always check the manifest minSdkVersion.
        checkManifest(antProject, androidTarget.getVersion());

        // sets up the properties to find android.jar/framework.aidl/target tools
        String androidJar = androidTarget.getPath(IAndroidTarget.ANDROID_JAR);
        antProject.setProperty(mAndroidJarFileOut, androidJar);

        String androidAidl = androidTarget.getPath(IAndroidTarget.ANDROID_AIDL);
        antProject.setProperty(mAndroidAidlFileOut, androidAidl);

        // sets up the boot classpath

        // create the Path object
        Path bootclasspath = new Path(antProject);

        // create a PathElement for the framework jar
        PathElement element = bootclasspath.createPathElement();
        element.setPath(androidJar);

        // create PathElement for each optional library.
        IOptionalLibrary[] libraries = androidTarget.getOptionalLibraries();
        if (libraries != null) {
            HashSet<String> visitedJars = new HashSet<String>();
            for (IOptionalLibrary library : libraries) {
                String jarPath = library.getJarPath();
                if (visitedJars.contains(jarPath) == false) {
                    visitedJars.add(jarPath);

                    element = bootclasspath.createPathElement();
                    element.setPath(jarPath);
                }
            }
        }

        // sets the path in the project with a reference
        antProject.addReference(mBootClassPathOut, bootclasspath);
    }

    /**
     * Checks the manifest <code>minSdkVersion</code> attribute.
     * @param antProject the ant project
     * @param androidVersion the version of the platform the project is compiling against.
     */
    private void checkManifest(Project antProject, AndroidVersion androidVersion) {
        try {
            File manifest = new File(antProject.getBaseDir(), SdkConstants.FN_ANDROID_MANIFEST_XML);

            XPath xPath = AndroidXPathFactory.newXPath();

            // check the package name.
            String value = xPath.evaluate(
                    "/"  + AndroidManifest.NODE_MANIFEST +
                    "/@" + AndroidManifest.ATTRIBUTE_PACKAGE,
                    new InputSource(new FileInputStream(manifest)));
            if (value != null) { // aapt will complain if it's missing.
                // only need to check that the package has 2 segments
                if (value.indexOf('.') == -1) {
                    throw new BuildException(String.format(
                            "Application package '%1$s' must have a minimum of 2 segments.",
                            value));
                }
            }

            // check the minSdkVersion value
            value = xPath.evaluate(
                    "/"  + AndroidManifest.NODE_MANIFEST +
                    "/"  + AndroidManifest.NODE_USES_SDK +
                    "/@" + AndroidXPathFactory.DEFAULT_NS_PREFIX + ":" +
                            AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION,
                    new InputSource(new FileInputStream(manifest)));

            if (androidVersion.isPreview()) {
                // in preview mode, the content of the minSdkVersion must match exactly the
                // platform codename.
                String codeName = androidVersion.getCodename();
                if (codeName.equals(value) == false) {
                    throw new BuildException(String.format(
                            "For '%1$s' SDK Preview, attribute minSdkVersion in AndroidManifest.xml must be '%1$s' (current: %2$s)",
                            codeName, value));
                }

                // set the minSdkVersion to the previous API level (which is actually the value in
                // androidVersion.)
                antProject.setProperty(mMinSdkVersionOut,
                        Integer.toString(androidVersion.getApiLevel()));

            } else if (value.length() > 0) {
                // for normal platform, we'll only display warnings if the value is lower or higher
                // than the target api level.
                // First convert to an int.
                int minSdkValue = -1;
                try {
                    minSdkValue = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    // looks like it's not a number: error!
                    throw new BuildException(String.format(
                            "Attribute %1$s in AndroidManifest.xml must be an Integer!",
                            AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION));
                }

                // set the minSdkVersion to the value
                antProject.setProperty(mMinSdkVersionOut, value);

                int projectApiLevel = androidVersion.getApiLevel();
                if (minSdkValue > androidVersion.getApiLevel()) {
                    System.out.println(String.format(
                            "WARNING: Attribute %1$s in AndroidManifest.xml (%2$d) is higher than the project target API level (%3$d)",
                            AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION,
                            minSdkValue, projectApiLevel));
                }
            } else {
                // no minSdkVersion? display a warning
                System.out.println(
                        "WARNING: No minSdkVersion value set. Application will install on all Android versions.");

                // set the target api to 1
                antProject.setProperty(mMinSdkVersionOut, "1");
            }

        } catch (XPathExpressionException e) {
            throw new BuildException(e);
        } catch (FileNotFoundException e) {
            throw new BuildException(e);
        }
    }
}
