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
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.xml.AndroidManifest;
import com.android.xml.AndroidXPathFactory;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;

/**
 * Task to query the type of the current project.
 *
 * Out params:
 *
 * <code>projectTypeOut</code>: String value containing the type of the project. Possible values
 * are 'app', 'library', 'test', 'test-app'
 *
 */
public class GetTypeTask extends Task {

    private String mProjectTypeOut;

    public void setProjectTypeOut(String projectTypeOut) {
        mProjectTypeOut = projectTypeOut;
    }

    @Override
    public void execute() throws BuildException {
        if (mProjectTypeOut == null) {
            throw new BuildException("Missing attribute projectTypeOut");
        }

        Project antProject = getProject();

        String libraryProp = antProject.getProperty(ProjectProperties.PROPERTY_LIBRARY);
        if (libraryProp != null) {
            if (Boolean.valueOf(libraryProp).booleanValue()) {
                System.out.println("Project Type: Android Library");

                antProject.setProperty(mProjectTypeOut, "library");
                return;
            }
        }

        if (antProject.getProperty(ProjectProperties.PROPERTY_TESTED_PROJECT) != null) {
            System.out.println("Project Type: Test Application");

            antProject.setProperty(mProjectTypeOut, "test");
            return;
        }

        // we also need to check if the Manifest doesn't have some instrumentation which
        // means the app is a self-contained test project.
        try {
            File manifest = new File(antProject.getBaseDir(), SdkConstants.FN_ANDROID_MANIFEST_XML);
            XPath xPath = AndroidXPathFactory.newXPath();

            // check the present of /manifest/instrumentation/
            String value = xPath.evaluate(
                    "/"  + AndroidManifest.NODE_MANIFEST +
                    "/" + AndroidManifest.NODE_INSTRUMENTATION +
                    "/@" + AndroidXPathFactory.DEFAULT_NS_PREFIX +
                    ":" + AndroidManifest.ATTRIBUTE_TARGET_PACKAGE,
                    new InputSource(new FileInputStream(manifest)));

            if (value != null && value.length() > 0) {
                System.out.println("Project Type: Self-Tested Application");

                antProject.setProperty(mProjectTypeOut, "test-app");
                return;
            }
        } catch (XPathExpressionException e) {
            throw new BuildException(e);
        } catch (FileNotFoundException e) {
            throw new BuildException(e);
        }

        // default case
        System.out.println("Project Type: Application");

        antProject.setProperty(mProjectTypeOut, "app");
    }
}
