/*
 * Copyright (C) 2011 The Android Open Source Project
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

import java.io.File;
import java.util.Set;

public class InputPath {

    private final File mFile;
    /**
     * A set of extensions. Only files with an extension in this set will
     * be considered for a modification check. All deleted/created files will still be
     * checked.
     */
    private final Set<String> mTouchedExtensions;

    public InputPath(File file) {
        this(file, null);
    }

    public InputPath(File file, Set<String> extensionsToCheck) {
        if (file == null) {
            throw new RuntimeException("File in InputPath(File) can't be null");
        }
        mFile = file;
        mTouchedExtensions = extensionsToCheck;
    }

    public File getFile() {
        return mFile;
    }

    /**
     * Returns whether this input path (likely actually a folder) must check this files for
     * modification (all files are checked for add/delete).
     *
     * This is configured by constructing the {@link InputPath} with additional restriction
     * parameters such as specific extensions.
     * @param file the file to check
     * @return true if the file must be checked for modification.
     */
    public boolean checksForModification(File file) {
        if (ignores(file)) {
            return false;
        }

        if (mTouchedExtensions != null &&
                mTouchedExtensions.contains(getExtension(file)) == false) {
            return false;
        }

        return true;
    }

    /**
     * Returns whether the InputPath ignores a given file or folder. If it is ignored then
     * the file (or folder) is not checked for any event (modification/add/delete).
     * If it's a folder, then it and its content are completely ignored.
     * @param file the file or folder to check
     * @return true if the file or folder are ignored.
     */
    public boolean ignores(File file) {
        // always ignore hidden files/folders.
        return file.getName().startsWith(".");
    }

    /**
     *  Gets the extension (if present) on a file by looking at the filename
     *  @param file the file to get the extension from
     *  @return the extension if present, or the empty string if the filename doesn't have
     *          and extension.
     */
   protected static String getExtension(File file) {
       return getExtension(file.getName());
   }

   /**
    *  Gets the extension (if present) on a file by looking at the filename
    *  @param fileName the filename to get the extension from
    *  @return the extension if present, or the empty string if the filename doesn't have
    *          and extension.
    */
   protected static String getExtension(String fileName) {
       int index = fileName.lastIndexOf('.');
       if (index == -1) {
           return "";
       }
       // Don't include the leading '.' in the extension
       return fileName.substring(index + 1);
   }

}
