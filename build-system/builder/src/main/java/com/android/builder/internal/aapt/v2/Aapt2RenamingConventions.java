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

package com.android.builder.internal.aapt.v2;

import com.android.annotations.NonNull;
import com.android.builder.internal.aapt.AaptException;
import com.google.common.io.Files;

import java.io.File;

/**
 * Class containing the file renaming rules for {@code aapt2}.
 */
final class Aapt2RenamingConventions {

    private Aapt2RenamingConventions() {}

    /**
     * Obtains the renaming for compilation for the given file.
     *
     * @param f the file
     * @return the new file's name (this will take the file's path into consideration)
     * @throws AaptException cannot analyze file path
     */
    public static String compilationRename(@NonNull File f) throws AaptException {
        String fileName = f.getName();

        File fileParent = f.getParentFile();
        if (fileParent == null) {
            throw new AaptException("Could not get parent of file '" + f.getAbsolutePath() + "'");
        }

        String parentName = fileParent.getName();

        /*
         * Split fileName into fileName and ext. If fileName does not have an extension, make ext
         * empty.
         */
        String ext = Files.getFileExtension(fileName);
        if (!ext.isEmpty()) {
            ext = "." + ext;
        }

        fileName = Files.getNameWithoutExtension(fileName);

        /*
         * values/strings.xml becomes values_strings.arsc.flat and not values_strings.xml.flat.
         */
        if (parentName.equals("values") && fileName.equals("strings") && ext.equals(".xml")) {
            ext = ".arsc";
        }


        return parentName + "_" + fileName + ext + ".flat";
    }
}
