/*
 * Copyright (C) 2010 The Android Open Source Project
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
import com.android.annotations.Nullable;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.internal.project.ProjectProperties.PropertyType;
import com.android.sdklib.internal.project.ProjectPropertiesWorkingCopy;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.PkgProps;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.util.DeweyDecimal;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

final class TaskHelper {

    private static Map<String, String> DEFAULT_ATTR_VALUES = new HashMap<String, String>();
    static {
        DEFAULT_ATTR_VALUES.put("source.dir", SdkConstants.FD_SOURCES);
        DEFAULT_ATTR_VALUES.put("out.dir", SdkConstants.FD_OUTPUT);
    }

    static String getDefault(String name) {
        return DEFAULT_ATTR_VALUES.get(name);
    }

    static File getSdkLocation(Project antProject) {
        // get the SDK location
        String sdkOsPath = antProject.getProperty(ProjectProperties.PROPERTY_SDK);

        // check if it's valid and exists
        if (sdkOsPath == null || sdkOsPath.length() == 0) {
            throw new BuildException("SDK Location is not set.");
        }

        File sdk = new File(sdkOsPath);
        if (sdk.isDirectory() == false) {
            throw new BuildException(String.format("SDK Location '%s' is not valid.", sdkOsPath));
        }

        return sdk;
    }

    /**
     * Returns the revision of the tools for a given SDK.
     * @param sdkFile the {@link File} for the root folder of the SDK
     * @return the tools revision or -1 if not found.
     */
    @Nullable
    static DeweyDecimal getToolsRevision(File sdkFile) {
        Properties p = new Properties();
        try{
            // tools folder must exist, or this custom task wouldn't run!
            File toolsFolder= new File(sdkFile, SdkConstants.FD_TOOLS);
            File sourceProp = new File(toolsFolder, SdkConstants.FN_SOURCE_PROP);

            FileInputStream fis = null;
            try {
                fis = new FileInputStream(sourceProp);
                p.load(fis);
            } finally {
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (IOException ignore) {
                    }
                }
            }

            String value = p.getProperty(PkgProps.PKG_REVISION);
            if (value != null) {
                FullRevision rev = FullRevision.parseRevision(value);
                return new DeweyDecimal(rev.toIntArray(false /*includePreview*/));
            }
        } catch (NumberFormatException e) {
            // couldn't parse the version number.
        } catch (FileNotFoundException e) {
            // couldn't find the file.
        } catch (IOException e) {
            // couldn't find the file.
        }

        return null;
    }

    static String checkSinglePath(String attribute, Path path) {
        String[] paths = path.list();
        if (paths.length != 1) {
            throw new BuildException(String.format(
                    "Value for '%1$s' is not valid. It must resolve to a single path", attribute));
        }

        return paths[0];
    }

    /**
     * Returns the ProjectProperties for a given project path.
     * This loads and merges all the .properties files in the same way that Ant does it.
     *
     * Note that this does not return all the Ant properties but only the one customized by the
     * project's own build.xml file.
     *
     * If the project has no .properties files, this returns an empty {@link ProjectProperties}
     * with type {@link PropertyType#PROJECT}.
     *
     * @param projectPath the path to the project root folder.
     * @return a ProjectProperties.
     */
    @NonNull
    static ProjectProperties getProperties(@NonNull String projectPath) {
        // the import order is local, ant, project so we need to respect this.
        PropertyType[] types = PropertyType.getOrderedTypes();

        // make a working copy of the first non null props and then merge the rest into it.
        ProjectProperties properties = null;
        for (int i = 0 ; i < types.length ; i++) {
            properties = ProjectProperties.load(projectPath, types[i]);

            if (properties != null) {
                ProjectPropertiesWorkingCopy workingCopy = properties.makeWorkingCopy();
                for (int k = i + 1 ; k < types.length ; k++) {
                    workingCopy.merge(types[k]);
                }

                // revert back to a read-only version
                properties = workingCopy.makeReadOnlyCopy();

                return properties;
            }
        }

        // return an empty object with type PropertyType.PROJECT (doesn't actually matter).
        return ProjectProperties.createEmpty(projectPath, PropertyType.PROJECT);
    }
}
