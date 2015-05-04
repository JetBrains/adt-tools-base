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

package com.android.build.gradle.model;

import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.dsl.GroupableProductFlavor;
import com.android.build.gradle.managed.BuildType;
import com.android.build.gradle.managed.SigningConfig;
import com.android.build.gradle.ndk.NdkExtension;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.model.Managed;
import org.gradle.model.Unmanaged;
import org.gradle.model.collection.ManagedSet;

/**
 * Component model for all Android plugin.
 */
@Managed
public interface AndroidModel {
    ManagedSet<BuildType> getBuildTypes();

    @Unmanaged
    NamedDomainObjectContainer<GroupableProductFlavor> getProductFlavors();

    void setProductFlavors(NamedDomainObjectContainer<GroupableProductFlavor> productFlavors);

    ManagedSet<SigningConfig> getSigningConfigs();

    @Unmanaged
    AndroidComponentModelSourceSet getSources();

    void setSources(AndroidComponentModelSourceSet sources);

    @Unmanaged
    NdkExtension getNdk();

    void setNdk(NdkExtension ndk);

    @Unmanaged
    BaseExtension getConfig();

    void setConfig(BaseExtension config);
}
