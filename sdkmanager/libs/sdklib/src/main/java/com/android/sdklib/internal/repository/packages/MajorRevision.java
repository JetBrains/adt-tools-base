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

package com.android.sdklib.internal.repository.packages;

import com.android.annotations.NonNull;


/**
 * Package revision number composed of a <em>single</em> major revision.
 * <p/>
 * Contrary to a {@link FullRevision}, a {@link MajorRevision} does not
 * provide minor, micro and preview revision numbers -- these are all
 * set to zero.
 */
public class MajorRevision extends FullRevision {

    public MajorRevision(int major) {
        super(major, 0, 0);
    }

    @Override
    public String toString() {
        return super.toShortString();
    }

    /**
     * Parses a single-integer string and returns a new {@link MajorRevision} for it.
     *
     * @param revision A non-null revision to parse.
     * @return A new non-null {@link MajorRevision}.
     * @throws NumberFormatException if the parsing failed.
     */
    public static @NonNull MajorRevision parseRevision(@NonNull String revision)
            throws NumberFormatException {

        if (revision == null) {
            throw new NumberFormatException("revision is <null>"); //$NON-NLS-1$
        }

        return new MajorRevision(Integer.parseInt(revision.trim()));
    }
}
