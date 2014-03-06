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

package com.android.sdklib.repository.local;

import com.android.annotations.NonNull;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.MajorRevision;

import java.io.File;
import java.util.Properties;

/**
 * Local system-image package, for a given platform's {@link AndroidVersion}
 * and given ABI.
 * The package itself has a major revision.
 * There should be only one for a given android platform version & ABI.
 */
public class LocalSysImgPkgInfo extends LocalAndroidVersionPkgInfo {

    @NonNull
    private final MajorRevision mRevision;
    @NonNull
    private final String mAbi;

    public LocalSysImgPkgInfo(@NonNull LocalSdk localSdk,
                              @NonNull File localDir,
                              @NonNull Properties sourceProps,
                              @NonNull AndroidVersion version,
                              @NonNull String abi,
                              @NonNull MajorRevision revision) {
        super(localSdk, localDir, sourceProps, version);
        mAbi = abi;
        mRevision = revision;

    }

    @Override
    public int getType() {
        return LocalSdk.PKG_SYS_IMAGES;
    }

    @Override
    public boolean hasMajorRevision() {
        return true;
    }

    @NonNull
    @Override
    public MajorRevision getMajorRevision() {
        return mRevision;
    }

    @Override
    public boolean hasPath() {
        return true;
    }

    /** The System-image path is its ABI. */
    @NonNull
    @Override
    public String getPath() {
        return getAbi();
    }

    @NonNull
    public String getAbi() {
        return mAbi;
    }

    // TODO create package on demand if needed. This might not be needed
    // since typically system-images are retrieved via IAndroidTarget.
}
