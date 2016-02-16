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

package com.android.builder.internal.packaging;

import com.android.annotations.NonNull;
import com.android.builder.internal.packaging.zip.AlignmentRule;
import com.android.builder.internal.packaging.zip.ZFile;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Factory for {@link ZFile}s that are specifically configured to be APKs, AARs, ...
 */
public class ZFiles {

    /**
     * PNGs are aligned at 4-byte boundaries and identified as files ending with {@code .png}.
     */
    private static final AlignmentRule PNG_RULE =
            new AlignmentRule(Pattern.compile(".*\\.png"), 4, false);

    /**
     * SOs are aligned at 4096-byte boundaries and identified as files ending with {@code .so}.
     */
    private static final AlignmentRule SO_RULE = new AlignmentRule(Pattern.compile(".*\\.so"),
            4096, false);

    /**
     * Creates a new zip file configured as an apk, based on a given file.
     *
     * @param f the file, if this path does not represent an existing path, will create a
     * {@link ZFile} based on an non-existing path (a zip will be created when
     * {@link ZFile#close()} is invoked).
     * @return the zip file
     * @throws IOException failed to create the zip file
     */
    @NonNull
    public static ZFile apk(@NonNull File f) throws IOException {
        ZFile zfile = new ZFile(f);
        zfile.getAlignmentRules().add(PNG_RULE);
        zfile.getAlignmentRules().add(SO_RULE);
        return zfile;
    }
}
