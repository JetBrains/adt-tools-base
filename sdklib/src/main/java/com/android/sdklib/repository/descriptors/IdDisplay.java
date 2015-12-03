/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.sdklib.repository.descriptors;

import com.android.annotations.NonNull;

/**
 * Immutable structure that represents a tuple (id-string  + display-string.)
 */
public final class IdDisplay extends com.android.sdklib.repositoryv2.IdDisplay {

    private final String mId;
    private final String mDisplay;

    /**
     * Creates a new immutable tuple (id-string  + display-string.)
     *
     * @param id The non-null id string.
     * @param display The non-null display string.
     */
    public IdDisplay(@NonNull String id, @NonNull String display) {
        mId = id;
        mDisplay = display;
    }

    @Override
    @NonNull
    public String getId() {
        return mId;
    }

    @Override
    @NonNull
    public String getDisplay() {
        return mDisplay;
    }
}
