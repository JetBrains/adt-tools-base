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

package com.android.sdklib.repositoryv2.sources;

import com.android.repository.api.RepositorySource;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;

import javax.xml.bind.annotation.XmlTransient;

/**
 * Container for marker interfaces for the different {@link RepositorySource} types that can be
 * included in the site list retrieved by {@link AndroidSdkHandler#mAddonsListSourceProvider}.
 */
public final class RemoteSiteType {

    private RemoteSiteType() {}

    @XmlTransient public interface AddonSiteType extends RepositorySource {}
    @XmlTransient public interface SysImgSiteType extends RepositorySource {}
}
