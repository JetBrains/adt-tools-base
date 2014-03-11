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

package com.android.sdklib.repository.descriptors;

import com.android.annotations.NonNull;
import com.android.sdklib.AndroidTargetHash;

/**
 * {@link IAddonDesc} returns extra information on add-on descriptors.
 * <p/>
 * This is used with
 * {@link PkgDesc#newAddon(com.android.sdklib.AndroidVersion, com.android.sdklib.repository.MajorRevision, IAddonDesc)}
 * when the vendor id or add-on target hash cannot be determined when building the descriptor
 * (typically because the info should be loaded from the add-ons source property file and is
 *  not known in the context of the package creation.)
 */
public interface IAddonDesc {

    /**
     * Returns an add-on target hash.
     * <p/>
     * Ideally the target hash should be constructed using
     * {@link AndroidTargetHash#getAddonHashString(String, String, com.android.sdklib.AndroidVersion)}
     * to ensure it has the correct format.
     *
     * @return A non-null add=on target hash.
     */
    @NonNull
    public String getTargetHash();

    /**
     * Returns the add-on vendor id string.
     * @return A non-null add-on vendor id.
     */
    @NonNull
    public String getVendorId();
}
