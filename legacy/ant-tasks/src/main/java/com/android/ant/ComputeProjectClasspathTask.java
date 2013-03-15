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
import com.android.ant.DependencyHelper.JarProcessor;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Path.PathElement;

import java.io.File;
import java.util.List;

public class ComputeProjectClasspathTask extends Task {

    private String mProjectLocation;
    private String mProjectClassPathOut;

    public void setProjectLocation(String projectLocation) {
        mProjectLocation = projectLocation;
    }

    public void setProjectClassPathOut(String projectClassPathOut) {
        mProjectClassPathOut = projectClassPathOut;
    }

    @Override
    public void execute() throws BuildException {
        if (mProjectLocation == null) {
            throw new BuildException("Missing attribute projectLocation");
        }
        if (mProjectClassPathOut == null) {
            throw new BuildException("Missing attribute projectClassPathOut");
        }

        DependencyHelper helper = new DependencyHelper(new File(mProjectLocation),
                false /*verbose*/);

        JarProcessor processor = new JarProcessor();

        helper.processLibraries(processor);
        List<File> jars = processor.getJars();

        // add the project's own content of libs/*.jar
        File libsFolder = new File(mProjectLocation, SdkConstants.FD_NATIVE_LIBS);
        File[] jarFiles = libsFolder.listFiles(processor.getFilter());
        if (jarFiles != null) {
            for (File jarFile : jarFiles) {
                jars.add(jarFile);
            }
        }

        jars = helper.sanitizePaths(jars);

        Project antProject = getProject();

        System.out.println("Resolved classpath:");

        // create a path with all the jars and the project's output as well.
        Path path = new Path(antProject);
        for (File jar : jars) {
            PathElement element = path.createPathElement();
            String p = jar.getAbsolutePath();
            element.setPath(p);
            System.out.println(p);
        }

        File bin = new File(mProjectLocation,
                helper.getOutDir() + File.separator + "classes");
        PathElement element = path.createPathElement();
        String p = bin.getAbsolutePath();
        element.setPath(p);
        System.out.println(p);

        antProject.addReference(mProjectClassPathOut, path);
    }
}
