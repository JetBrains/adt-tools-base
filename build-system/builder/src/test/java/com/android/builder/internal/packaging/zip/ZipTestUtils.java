/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.builder.internal.packaging.zip;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.testutils.TestUtils;
import com.google.common.io.Files;

import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

/**
 * Utility method for zip tests.
 */
class ZipTestUtils {

    /**
     * Obtains the file with a resource with the given name. This is a file that lays in
     * the packaging subdirectory of test resources.
     *
     * @param rsrcName the resource name inside packaging resource folder
     * @return the resource file, guaranteed to exist
     */
    @NonNull
    static File rsrcFile(@NonNull String rsrcName) {
        File packagingRoot = TestUtils.getRoot("packaging");
        String rsrcPath = packagingRoot.getAbsolutePath() + "/" + rsrcName;
        File rsrcFile = new File(rsrcPath);
        assertTrue(rsrcFile.isFile());
        return rsrcFile;
    }

    /**
     * Clones a resource to a temporary folder. Generally, resources do not need to be cloned to
     * be used. However, in code where there is danger of changing resource files and corrupting
     * the source directory, cloning should be done before accessing the resources.
     *
     * @param rsrcName the resource name
     * @param folder the temporary folder
     * @return the file that was created with the resource
     * @throws IOException failed to clone the resource
     */
    static File cloneRsrc(@NonNull String rsrcName, @NonNull TemporaryFolder folder)
            throws IOException {
        String cloneName;
        if (rsrcName.contains("/")) {
            cloneName = rsrcName.substring(rsrcName.lastIndexOf('/') + 1);
        } else {
            cloneName = rsrcName;
        }

        return cloneRsrc(rsrcName, folder, cloneName);
    }

    /**
     * Clones a resource to a temporary folder. Generally, resources do not need to be cloned to
     * be used. However, in code where there is danger of changing resource files and corrupting
     * the source directory, cloning should be done before accessing the resources.
     *
     * @param rsrcName the resource name
     * @param folder the temporary folder
     * @param cloneName the name of the cloned resource that will be created inside the temporary
     * folder
     * @return the file that was created with the resource
     * @throws IOException failed to clone the resource
     */
    static File cloneRsrc(@NonNull String rsrcName, @NonNull TemporaryFolder folder,
            @NonNull String cloneName) throws IOException {
        File result = new File(folder.getRoot(), cloneName);
        assertFalse(result.exists());

        Files.copy(rsrcFile(rsrcName), result);
        return result;
    }
}
