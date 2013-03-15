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
import com.android.io.FileWrapper;
import com.android.sdklib.internal.project.IPropertySource;
import com.android.xml.AndroidManifest;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Path.PathElement;

import java.io.File;
import java.util.List;

/**
 * Computes the dependency of the current project.
 *
 * Out params:
 * <code>libraryResFolderPathOut</code>: the Path object containing the res folder for all the
 * library projects in the order needed by aapt.
 *
 * <code>libraryPackagesOut</code>: a simple property containing ;-separated package name from
 * the library projects.
 *
 * <code>jarLibraryPathOut</code>: the Path object containing all the 3rd party jar files.
 *
 * <code>libraryNativeFolderPathOut</code>: the Path with all the native folder for the library
 * projects.
 *
 *
 * In params:
 * <code>targetApi</code>: the compilation target api.
 * <code>verbose</code>: whether the build is verbose.
 *
 */
public class ComputeDependencyTask extends GetLibraryPathTask {

    private String mLibraryManifestFilePathOut;
    private String mLibraryResFolderPathOut;
    private String mLibraryPackagesOut;
    private String mJarLibraryPathOut;
    private String mLibraryNativeFolderPathOut;
    private String mLibraryBinAidlFolderPathOut;
    private String mLibraryRFilePathOut;
    private int mTargetApi = -1;
    private boolean mVerbose = false;

    public void setLibraryManifestFilePathOut(String libraryManifestFilePathOut) {
        mLibraryManifestFilePathOut = libraryManifestFilePathOut;
    }

    public void setLibraryResFolderPathOut(String libraryResFolderPathOut) {
        mLibraryResFolderPathOut = libraryResFolderPathOut;
    }

    public void setLibraryPackagesOut(String libraryPackagesOut) {
        mLibraryPackagesOut = libraryPackagesOut;
    }

    public void setJarLibraryPathOut(String jarLibraryPathOut) {
        mJarLibraryPathOut = jarLibraryPathOut;
    }

    public void setLibraryBinAidlFolderPathOut(String libraryBinAidlFolderPathOut) {
        mLibraryBinAidlFolderPathOut = libraryBinAidlFolderPathOut;
    }

    public void setLibraryRFilePathOut(String libraryRFilePathOut) {
        mLibraryRFilePathOut = libraryRFilePathOut;
    }

    public void setLibraryNativeFolderPathOut(String libraryNativeFolderPathOut) {
        mLibraryNativeFolderPathOut = libraryNativeFolderPathOut;
    }

    public void setTargetApi(int targetApi) {
        mTargetApi = targetApi;
    }

    @Override
    public void execute() throws BuildException {
        if (mLibraryManifestFilePathOut == null) {
            throw new BuildException("Missing attribute libraryManifestFilePathOut");
        }
        if (mLibraryResFolderPathOut == null) {
            throw new BuildException("Missing attribute libraryResFolderPathOut");
        }
        if (mLibraryPackagesOut == null) {
            throw new BuildException("Missing attribute libraryPackagesOut");
        }
        if (mJarLibraryPathOut == null) {
            throw new BuildException("Missing attribute jarLibraryPathOut");
        }
        if (mLibraryNativeFolderPathOut == null) {
            throw new BuildException("Missing attribute libraryNativeFolderPathOut");
        }
        if (mLibraryBinAidlFolderPathOut == null) {
            throw new BuildException("Missing attribute libraryBinFolderPathOut");
        }
        if (mLibraryRFilePathOut == null) {
            throw new BuildException("Missing attribute libraryRFilePathOut");
        }
        if (mTargetApi == -1) {
            throw new BuildException("Missing attribute targetApi");
        }

        final Project antProject = getProject();

        // get the SDK location
        File sdkDir = TaskHelper.getSdkLocation(antProject);

        // prepare several paths for future tasks
        final Path manifestFilePath = new Path(antProject);
        final Path resFolderPath = new Path(antProject);
        final Path nativeFolderPath = new Path(antProject);
        final Path binAidlFolderPath = new Path(antProject);
        final Path rFilePath = new Path(antProject);
        final StringBuilder packageStrBuilder = new StringBuilder();

        // custom jar processor doing a bit more than just collecting the jar files
        JarProcessor processor = new JarProcessor() {
            @Override
            public void processLibrary(String libRootPath, IPropertySource properties) {
                // let the super class handle the jar files
                super.processLibrary(libRootPath, properties);

                // get the AndroidManifest.xml path.
                // FIXME: support renamed location.
                PathElement element = manifestFilePath.createPathElement();
                element.setPath(libRootPath + '/' + SdkConstants.FN_ANDROID_MANIFEST_XML);

                // get the res path. $PROJECT/res as well as the crunch cache.
                // FIXME: support renamed folders.
                element = resFolderPath.createPathElement();
                element.setPath(libRootPath + '/' + SdkConstants.FD_OUTPUT +
                        '/' + SdkConstants.FD_RES);
                element = resFolderPath.createPathElement();
                element.setPath(libRootPath + '/' + SdkConstants.FD_RESOURCES);

                // get the folder for the native libraries. Always $PROJECT/libs
                // FIXME: support renamed folder and/or move libs to bin/libs/
                element = nativeFolderPath.createPathElement();
                element.setPath(libRootPath + '/' + SdkConstants.FD_NATIVE_LIBS);

                // get the bin/aidl folder. $PROJECT/bin/aidl for now
                // FIXME: support renamed folder.
                element = binAidlFolderPath.createPathElement();
                element.setPath(libRootPath + '/' + SdkConstants.FD_OUTPUT +
                        '/' + SdkConstants.FD_AIDL);

                // get the package from the manifest.
                FileWrapper manifest = new FileWrapper(libRootPath,
                        SdkConstants.FN_ANDROID_MANIFEST_XML);

                try {
                    String value = AndroidManifest.getPackage(manifest);
                    if (value != null) { // aapt will complain if it's missing.
                        packageStrBuilder.append(';');
                        packageStrBuilder.append(value);

                        // get the text R file. $PROJECT/bin/R.txt for now
                        // This must be in sync with the package list.
                        // FIXME: support renamed folder.
                        element = rFilePath.createPathElement();
                        element.setPath(libRootPath + "/" + SdkConstants.FD_OUTPUT +
                                "/" + "R.txt");

                    }
                } catch (Exception e) {
                    throw new BuildException(e);
                }
            }
        };

        // list of all the jars that are on the classpath. This will receive the
        // project's libs/*.jar files, the Library Projects output and their own libs/*.jar
        List<File> jars = processor.getJars();


        // in case clean has been called before a build type target, the list of
        // libraries has already been computed so we don't need to compute it again.
        Path libraryFolderPath = (Path) antProject.getReference(getLibraryFolderPathOut());
        if (libraryFolderPath == null) {
            execute(processor);
        } else {
            // this contains the list of library folder in reverse order (compilation order).
            // We need to process it in the normal order (res order).
            System.out.println("Ordered libraries:");

            String[] libraries = libraryFolderPath.list();
            for (int i = libraries.length - 1 ; i >= 0 ; i--) {
                String libRootPath = libraries[i];
                System.out.println(libRootPath);

                processor.processLibrary(libRootPath);
            }
        }

        boolean hasLibraries = jars.size() > 0;

        if (mTargetApi <= 15) {
            System.out.println("\n------------------");
            System.out.println("API<=15: Adding annotations.jar to the classpath.");

            jars.add(new File(sdkDir, SdkConstants.FD_TOOLS +
                    '/' + SdkConstants.FD_SUPPORT +
                    '/' + SdkConstants.FN_ANNOTATIONS_JAR));

        }

        // even with no libraries, always setup these so that various tasks in Ant don't complain
        // (the task themselves can handle a ref to an empty Path)
        antProject.addReference(mLibraryNativeFolderPathOut, nativeFolderPath);
        antProject.addReference(mLibraryManifestFilePathOut, manifestFilePath);
        antProject.addReference(mLibraryBinAidlFolderPathOut, binAidlFolderPath);

        // the rest is done only if there's a library.
        if (hasLibraries) {
            antProject.addReference(mLibraryResFolderPathOut, resFolderPath);
            antProject.setProperty(mLibraryPackagesOut, packageStrBuilder.toString());
            antProject.addReference(mLibraryRFilePathOut, rFilePath);
        }

        File projectFolder = antProject.getBaseDir();

        // add the project's own content of libs/*.jar
        File libsFolder = new File(projectFolder, SdkConstants.FD_NATIVE_LIBS);
        File[] jarFiles = libsFolder.listFiles(processor.getFilter());
        if (jarFiles != null) {
            for (File jarFile : jarFiles) {
                jars.add(jarFile);
            }
        }

        // now sanitize the path to remove dups
        jars = DependencyHelper.sanitizePaths(projectFolder, new IPropertySource() {
            @Override
            public String getProperty(String name) {
                return antProject.getProperty(name);
            }

            @Override
            public void debugPrint() {
            }
        }, jars);

        // and create a Path object for them
        Path jarsPath = new Path(antProject);
        if (mVerbose) {
            System.out.println("\n------------------\nSanitized jar list:");
        }
        for (File f : jars) {
            if (mVerbose) {
                System.out.println("- " + f.getAbsolutePath());
            }
            PathElement element = jarsPath.createPathElement();
            element.setPath(f.getAbsolutePath());
        }
        antProject.addReference(mJarLibraryPathOut, jarsPath);

        if (mVerbose) {
            System.out.println();
        }
    }
}
