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

package com.android.build.gradle.internal.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.api.GroupableProductFlavor;

/**
 * Read-only version of the GroupableProductFlavor wrapping another GroupableProductFlavor.
 *
 * In the variant API, it is important that the objects returned by the variants
 * are read-only.
 *
 * However, even though the API is defined to use the base interfaces as return
 * type (which all contain only getters), the dynamics of Groovy makes it easy to
 * actually use the setters of the implementation classes.
 *
 * This wrapper ensures that the returned instance is actually just a strict implementation
 * of the base interface and is read-only.
 */
// TODO: Remove once GroupableProductFlavor interface is removed.
@Deprecated
public class ReadOnlyGroupableProductFlavor extends ReadOnlyProductFlavor implements
        GroupableProductFlavor {

    ReadOnlyGroupableProductFlavor(
            @NonNull GroupableProductFlavor productFlavor,
            @NonNull ReadOnlyObjectProvider readOnlyObjectProvider) {
        super(productFlavor, readOnlyObjectProvider);
    }

    @Deprecated
    public String getFlavorDimension() {
        return getDimension();
    }
}
