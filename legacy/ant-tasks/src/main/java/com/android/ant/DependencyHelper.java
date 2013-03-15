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
import com.android.annotations.Nullable;
import com.android.io.FolderWrapper;
import com.android.sdklib.build.JarListSanitizer;
import com.android.sdklib.build.JarListSanitizer.DifferentLibException;
import com.android.sdklib.build.JarListSanitizer.Sha1Exception;
import com.android.sdklib.internal.project.IPropertySource;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.internal.project.ProjectProperties.PropertyType;

import org.apache.tools.ant.BuildException;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Helper class to manage dependency for projects.
 *
 */
public class DependencyHelper {

    private final boolean mVerbose;
    private final File mProjectFolder;
    private final IPropertySource mProperties;
    private final List<File> mLibraries = new ArrayList<File>();

    /**
     * A Library Processor. Used in {@link DependencyHelper#processLibraries(LibraryProcessor)}
     *
     */
    protected interface LibraryProcessor {
        void processLibrary(String libRootPath);
    }

    /**
     * Advanced version of the {@link LibraryProcessor} that provides the library properties
     * to the processor.
     */
    public static abstract class AdvancedLibraryProcessor implements LibraryProcessor {

        public abstract void processLibrary(String libRootPath, IPropertySource properties);

        @Override
        public final void processLibrary(String libRootPath) {
            ProjectProperties properties = TaskHelper.getProperties(libRootPath);

            processLibrary(libRootPath, properties);
        }
    }

    /**
     * Implementation of {@link AdvancedLibraryProcessor} that builds a list of sanitized list
     * of 3rd party jar files from all the Library Projects.
     */
    public static class JarProcessor extends AdvancedLibraryProcessor {

        private final List<File> mJars = new ArrayList<File>();

        private final FilenameFilter mFilter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.toLowerCase(Locale.US).endsWith(".jar");
            }
        };

        public List<File> getJars() {
            return mJars;
        }

        public FilenameFilter getFilter() {
            return mFilter;
        }

        @Override
        public void processLibrary(String libRootPath, IPropertySource properties) {
            // get the library output
            // FIXME: support renamed folder.
            mJars.add(new File(libRootPath + "/" + SdkConstants.FD_OUTPUT +
                    "/" + SdkConstants.FN_CLASSES_JAR));

            // Get the 3rd party jar files.
            // FIXME: support renamed folder.
            File libsFolder = new File(libRootPath, SdkConstants.FD_NATIVE_LIBS);
            File[] jarFiles = libsFolder.listFiles(mFilter);
            if (jarFiles != null) {
                for (File jarFile : jarFiles) {
                    mJars.add(jarFile);
                }
            }
        }
    }


    public static List<File> sanitizePaths(File projectFolder, IPropertySource properties,
            List<File> paths) {
        // first get the non-files.
        List<File> results = new ArrayList<File>();
        for (int i = 0 ; i < paths.size() ;) {
            File f = paths.get(i);
            // TEMP WORKAROUND: ignore classes.jar as all the output of libraries are
            // called the same (in Ant) but are not actually the same jar file.
            // TODO: Be aware of library output vs. regular jar dependency.
            if (f.isFile() && f.getName().equals(SdkConstants.FN_CLASSES_JAR) == false) {
                i++;
            } else {
                results.add(f);
                paths.remove(i);
            }
        }


        File outputFile = new File(projectFolder, getOutDir(properties));
        JarListSanitizer sanitizer = new JarListSanitizer(outputFile);

        try {
            results.addAll(sanitizer.sanitize(paths));
        } catch (DifferentLibException e) {
            String[] details = e.getDetails();
            for (String s : details) {
                System.err.println(s);
            }
            throw new BuildException(e.getMessage(), e);
        } catch (Sha1Exception e) {
            throw new BuildException(
                    "Failed to compute sha1 for " + e.getJarFile().getAbsolutePath(), e);
        }

        return results;
    }

    /**
     *
     * @param projectFolder the project root folder.
     */
    public DependencyHelper(File projectFolder, boolean verbose) {
        mProjectFolder = projectFolder;
        mVerbose = verbose;

        mProperties = TaskHelper.getProperties(projectFolder.getAbsolutePath());

        init(projectFolder);
    }

    /**
     *
     * @param projectFolder the project root folder.
     * @param source an {@link IPropertySource} that can provide the project properties values.
     */
    public DependencyHelper(File projectFolder, IPropertySource properties, boolean verbose) {
        mProjectFolder = projectFolder;
        mProperties = properties;
        mVerbose = verbose;

        init(projectFolder);
    }

    private void init(File projectFolder) {
        // get the top level list of library dependencies.
        List<File> topLevelLibraries = getDirectDependencies(projectFolder, mProperties);

        // process the libraries in case they depend on other libraries.
        resolveFullLibraryDependencies(topLevelLibraries, mLibraries);
    }

    public List<File> getLibraries() {
        return mLibraries;
    }

    public int getLibraryCount() {
        return mLibraries.size();
    }

    public String getProperty(String name) {
        return mProperties.getProperty(name);
    }

    public void processLibraries(@Nullable LibraryProcessor processor) {
        // use that same order to process the libraries.
        for (File library : mLibraries) {
            // get the root path.
            String libRootPath = library.getAbsolutePath();
            if (mVerbose) {
                System.out.println(libRootPath);
            }

            if (processor != null) {
                processor.processLibrary(libRootPath);
            }
        }
    }

    public List<File> sanitizePaths(List<File> paths) {
        return sanitizePaths(mProjectFolder, mProperties, paths);
    }

    public String getOutDir() {
        return getOutDir(mProperties);
    }


    /**
     * Returns the top level library dependencies of a given <var>source</var> representing a
     * project properties.
     * @param baseFolder the base folder of the project (to resolve relative paths)
     * @param properties a source of project properties.
     */
    private List<File> getDirectDependencies(File baseFolder, IPropertySource properties) {
        ArrayList<File> libraries = new ArrayList<File>();

        // first build the list. they are ordered highest priority first.
        int index = 1;
        while (true) {
            String propName = ProjectProperties.PROPERTY_LIB_REF + Integer.toString(index++);
            String rootPath = properties.getProperty(propName);

            if (rootPath == null) {
                break;
            }

            try {
                File library = new File(baseFolder, rootPath).getCanonicalFile();

                // check for validity
                File projectProp = new File(library, PropertyType.PROJECT.getFilename());
                if (projectProp.isFile() == false) {
                    // error!
                    throw new BuildException(String.format(
                            "%1$s resolve to a path with no %2$s file for project %3$s", rootPath,
                            PropertyType.PROJECT.getFilename(), baseFolder.getAbsolutePath()));
                }

                if (libraries.contains(library) == false) {
                    if (mVerbose) {
                        System.out.println(String.format("%1$s: %2$s => %3$s",
                                baseFolder.getAbsolutePath(), rootPath, library.getAbsolutePath()));
                    }

                    libraries.add(library);
                }
            } catch (IOException e) {
                throw new BuildException("Failed to resolve library path: " + rootPath, e);
            }
        }

        return libraries;
    }

    /**
     * Resolves a given list of libraries, finds out if they depend on other libraries, and
     * returns a full list of all the direct and indirect dependencies in the proper order (first
     * is higher priority when calling aapt).
     * @param inLibraries the libraries to resolve
     * @param outLibraries where to store all the libraries.
     */
    private void resolveFullLibraryDependencies(List<File> inLibraries, List<File> outLibraries) {
        // loop in the inverse order to resolve dependencies on the libraries, so that if a library
        // is required by two higher level libraries it can be inserted in the correct place
        for (int i = inLibraries.size() - 1  ; i >= 0 ; i--) {
            File library = inLibraries.get(i);

            // get the default.property file for it
            final ProjectProperties projectProp = ProjectProperties.load(
                    new FolderWrapper(library), PropertyType.PROJECT);

            // get its libraries
            List<File> dependencies = getDirectDependencies(library, projectProp);

            // resolve the dependencies for those libraries
            resolveFullLibraryDependencies(dependencies, outLibraries);

            // and add the current one (if needed) in front (higher priority)
            if (outLibraries.contains(library) == false) {
                outLibraries.add(0, library);
            }
        }
    }

    private static String getOutDir(IPropertySource properties) {
        String bin = properties.getProperty("out.dir");
        if (bin == null) {
            return SdkConstants.FD_OUTPUT;
        }

        return bin;
    }

}
