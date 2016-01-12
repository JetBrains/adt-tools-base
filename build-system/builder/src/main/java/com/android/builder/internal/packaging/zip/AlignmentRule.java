/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.google.common.base.Preconditions;

import java.util.regex.Pattern;

/**
 * An alignment rule defines how should some files be aligned in a zip file. A rule is defined
 * by two properties: a pattern and an alignment value.
 * <p>
 * The pattern is applied to the file name and defines which files this rule applies to. Note that
 * the pattern is <em>not</em> applied to the <em>path</em>, only to the file name.
 * The value defines the alignment of data. So,
 * for example, an alignment of {@code 1024} means that the data needs to start in a byte {@code b}
 * such that {@code b % 1024 == 0}.
 */
public class AlignmentRule {

    /**
     * File name pattern.
     */
    @NonNull
    private Pattern mPattern;

    /**
     * Alignment value.
     */
    private int mAlignment;

    /**
     * Creates a new alignment rule.
     *
     * @param pattern the pattern to apply to file names to decide whether the rules applies or not
     * to a file; this will be checked using {@code matches()}, not {@code find()}
     * @param alignment the alignment value, must be non-negative
     */
    public AlignmentRule(@NonNull Pattern pattern, int alignment) {
        Preconditions.checkArgument(alignment > 0, "alignment (%s) <= 0", alignment);

        mPattern = pattern;
        mAlignment = alignment;
    }

    /**
     * Obtains the pattern used to match files.
     *
     * @return the pattern
     */
    @NonNull
    public Pattern getPattern() {
        return mPattern;
    }

    /**
     * Obtains the alignment value for the file.
     *
     * @return the alignment
     */
    public int getAlignment() {
        return mAlignment;
    }
}
