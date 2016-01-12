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
import com.google.common.base.Verify;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * Alignment rules maintains a list of {@link AlignmentRule} and allows checking the alignment for
 * a file. Rules in the list are kept in order and the first rule to apply to a file will be the
 * one used.
 */
public class AlignmentRules {

    /**
     * Default alignment to return if no rule matches a file.
     */
    private static final int DEFAULT_ALIGNMENT = 1;

    /**
     * The alignment rules.
     */
    @NonNull
    private List<AlignmentRule> mRules;

    /**
     * Creates a new empty set of rules.
     */
    public AlignmentRules() {
        mRules = Lists.newArrayList();
    }

    /**
     * Adds a new alignment rule to the end of the list.
     *
     * @param rule the rule to add
     */
    public void add(@NonNull AlignmentRule rule) {
        mRules.add(rule);
    }

    /**
     * Finds the alignment of a file with a certain path.
     *
     * @param path the path
     * @return the alignment or {@code 1} if there are no rules for this file, never returns less
     * than {@code 1}
     */
    public int alignment(@NonNull String path) {
        /*
         * Remove the trailing separator, if there is one.
         */
        if (path.endsWith(Character.toString(ZFile.SEPARATOR))) {
            path = path.substring(0, path.length() - 1);
        }

        /*
         * The zip specification guarantees that there are no absolute paths in the zip. This means
         * that any separator, if it exists, cannot be the first character. (See section 4.4.17.)
         */
        int lastSlashIdx = path.lastIndexOf(ZFile.SEPARATOR);
        Verify.verify(lastSlashIdx != 0);
        if (lastSlashIdx > 0) {
            path = path.substring(lastSlashIdx + 1);
        }

        /*
         * Now path, does not contain any separator and is the file name. Check if any rule applies
         * or return the default value if no rule does.
         */
        Verify.verify(path.indexOf(ZFile.SEPARATOR) == -1);
        for (AlignmentRule rule : mRules) {
            if (rule.getPattern().matcher(path).matches()) {
                int alignment = rule.getAlignment();
                Verify.verify(alignment >= 1);
                return alignment;
            }
        }

        return DEFAULT_ALIGNMENT;
    }
}
