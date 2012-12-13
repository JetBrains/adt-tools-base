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



/**
 * Interface for packages that provide a {@link FullRevision},
 * which is a multi-part revision number (major.minor.micro) and an optional preview revision.
 * <p/>
 * This interface is a tag. It indicates that {@link Package#getRevision()} returns a
 * {@link FullRevision} instead of a limited {@link MajorRevision}. <br/>
 * The preview version number is available via {@link Package#getRevision()}.
 */
public interface IFullRevisionProvider {

    /**
     * Returns whether the give package represents the same item as the current package.
     * <p/>
     * Two packages are considered the same if they represent the same thing, except for the
     * revision number.
     * @param pkg the package to compare
     * @param ignorePreviews true if 2 packages should be considered the same even if one
     *    is a preview and the other one is not.
     * @return true if the item are the same.
     */
    public abstract boolean sameItemAs(Package pkg, boolean ignorePreviews);

}
