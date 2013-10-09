/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.sdklib.local;

import com.android.annotations.NonNull;
import com.android.sdklib.repository.FullRevision;

import java.io.File;
import java.util.Properties;

abstract class LocalFullRevisionPkgInfo extends LocalPkgInfo {

    @NonNull
    private final FullRevision mRevision;

    public LocalFullRevisionPkgInfo(@NonNull LocalSdk localSdk,
                                    @NonNull File localDir,
                                    @NonNull Properties sourceProps,
                                    @NonNull FullRevision revision) {
        super(localSdk, localDir, sourceProps);
        mRevision = revision;
    }

    @Override
    public boolean hasFullRevision() {
        return true;
    }

    @NonNull
    @Override
    public FullRevision getFullRevision() {
        return mRevision;
    }
}

