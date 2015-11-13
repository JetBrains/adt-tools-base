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

package com.android.sdklib.repositoryv2.meta;

import com.android.annotations.NonNull;
import com.android.repository.api.ElementFactory;
import com.android.repository.api.Repository;
import com.android.repository.impl.meta.TypeDetails;

/**
 * Parent class for {@code ObjectFactories} created by xjc from sdk-repository-XX.xsd, for
 * creating repository-schema-specific {@link TypeDetails} and associated classes.
 */
public abstract class RepoFactory extends ElementFactory<Repository> {

    /**
     * Create an instance of {@link DetailsTypes.PlatformToolDetailsType }
     */
    @NonNull
    public abstract DetailsTypes.PlatformToolDetailsType createPlatformToolDetailsType();

    /**
     * Create an instance of {@link DetailsTypes.BuildToolDetailsType }
     */
    @NonNull
    public abstract DetailsTypes.BuildToolDetailsType createBuildToolDetailsType();

    /**
     * Create an instance of {@link DetailsTypes.SourceDetailsType }
     */
    @NonNull
    public abstract DetailsTypes.SourceDetailsType createSourceDetailsType();

    /**
     * Create an instance of {@link DetailsTypes.PlatformDetailsType }
     */
    @NonNull
    public abstract DetailsTypes.PlatformDetailsType createPlatformDetailsType();

    /**
     * Create an instance of {@link DetailsTypes.DocDetailsType }
     */
    @NonNull
    public abstract DetailsTypes.DocDetailsType createDocDetailsType();

    /**
     * Create an instance of {@link DetailsTypes.NdkDetailsType }
     */
    @NonNull
    public abstract DetailsTypes.NdkDetailsType createNdkDetailsType();

    /**
     * Create an instance of {@link DetailsTypes.PlatformDetailsType.LayoutlibType }
     */
    @NonNull
    public abstract DetailsTypes.PlatformDetailsType.LayoutlibType createLayoutlibType();

    /**
     * Create an instance of {@link DetailsTypes.ToolDetailsType }
     */
    @NonNull
    public abstract DetailsTypes.ToolDetailsType createToolDetailsType();

}
