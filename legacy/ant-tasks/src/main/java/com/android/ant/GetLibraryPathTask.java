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
import com.android.annotations.Nullable;
import com.android.ant.DependencyHelper.AdvancedLibraryProcessor;
import com.android.ant.DependencyHelper.LibraryProcessor;
import com.android.sdklib.internal.project.IPropertySource;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Path.PathElement;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Task to get the list of Library Project paths for either the current project or any given
 * project.
 *
 */
public class GetLibraryPathTask extends Task {

    private String mProjectPath;
    private String mLibraryFolderPathOut;
    private String mLeaf;
    private boolean mVerbose = false;

    private static class LeafProcessor extends AdvancedLibraryProcessor {
        private final static Pattern PH = Pattern.compile("^\\@\\{(.*)\\}$");

        private Path mPath;
        private final String[] mLeafSegments;

        LeafProcessor(Project antProject, String leaf) {
            mPath = new Path(antProject);
            mLeafSegments = leaf.split("/");
        }

        @Override
        public void processLibrary(String libRootPath, IPropertySource properties) {
            StringBuilder sb = new StringBuilder(libRootPath);
            for (String segment : mLeafSegments) {
                sb.append('/');

                Matcher m = PH.matcher(segment);
                if (m.matches()) {
                    String value = properties.getProperty(m.group(1));
                    if (value == null) {
                        value = TaskHelper.getDefault(m.group(1));
                    }
                    if (value == null) {
                        throw new BuildException(
                                "Failed to resolve '" + m.group(1) + "' for project "
                                + libRootPath);
                    }
                    sb.append(value);
                } else {
                    sb.append(segment);
                }
            }

            PathElement element = mPath.createPathElement();
            element.setPath(sb.toString());
        }

        @NonNull public Path getPath() {
            return mPath;
        }
    }

    public void setProjectPath(String projectPath) {
        mProjectPath = projectPath;
    }

    public void setLibraryFolderPathOut(String libraryFolderPathOut) {
        mLibraryFolderPathOut = libraryFolderPathOut;
    }

    protected String getLibraryFolderPathOut() {
        return mLibraryFolderPathOut;
    }

    public void setLeaf(String leaf) {
        mLeaf = leaf;
    }

    /**
     * Sets the value of the "verbose" attribute.
     * @param verbose the value.
     */
    public void setVerbose(boolean verbose) {
        mVerbose = verbose;
    }

    protected boolean getVerbose() {
        return mVerbose;
    }

    @Override
    public void execute() throws BuildException {
        if (mLibraryFolderPathOut == null) {
            throw new BuildException("Missing attribute libraryFolderPathOut");
        }

        LibraryProcessor processor = null;

        if (mLeaf != null) {
            // we need a custom processor
            processor = new LeafProcessor(getProject(), mLeaf);
        }

        if (mProjectPath == null) {
            execute(processor);
        } else {
            DependencyHelper helper = new DependencyHelper(new File(mProjectPath), mVerbose);

            execute(helper, processor);
        }
    }

    /**
     * Executes the processor on the current project.
     * @param processor
     * @throws BuildException
     */
    protected void execute(@Nullable LibraryProcessor processor) throws BuildException {
        final Project antProject = getProject();

        DependencyHelper helper = new DependencyHelper(antProject.getBaseDir(),
                new IPropertySource() {
                    @Override
                    public String getProperty(String name) {
                        return antProject.getProperty(name);
                    }

                    @Override
                    public void debugPrint() {
                    }
                },
                mVerbose);

        execute(helper, processor);
    }

    /**
     * Executes the processor using a given DependencyHelper.
     * @param helper
     * @param processor
     * @throws BuildException
     */
    private void execute(@NonNull DependencyHelper helper, @Nullable LibraryProcessor processor)
            throws BuildException {

        final Project antProject = getProject();

        System.out.println("Library dependencies:");

        Path path = new Path(antProject);

        if (helper.getLibraryCount() > 0) {
            System.out.println("\n------------------\nOrdered libraries:");

            helper.processLibraries(processor);

            if (mLibraryFolderPathOut != null) {
                if (mLeaf == null) {
                    // Fill a Path object with all the libraries in reverse order.
                    // This is important so that compilation of libraries happens
                    // in the reverse order.
                    List<File> libraries = helper.getLibraries();

                    for (int i = libraries.size() - 1 ; i >= 0; i--) {
                        File library = libraries.get(i);
                        PathElement element = path.createPathElement();
                        element.setPath(library.getAbsolutePath());
                    }

                } else {
                    path = ((LeafProcessor) processor).getPath();
                }
            }
        } else {
            System.out.println("No Libraries");
        }

        antProject.addReference(mLibraryFolderPathOut, path);
    }
}
