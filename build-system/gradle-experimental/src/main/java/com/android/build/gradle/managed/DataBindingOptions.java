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

package com.android.build.gradle.managed;

import org.gradle.model.Managed;

@Managed
public interface DataBindingOptions {

    /**
     * The version of data binding to use.
     */
    String getVersion();
    void setVersion(String version);

    /**
     * Whether to enable data binding.
     */
    Boolean getEnabled();
    void setEnabled(Boolean enabled);

    /**
     * Whether to add the default data binding adapters.
     */
    Boolean getAddDefaultAdapters();
    void setAddDefaultAdapters(Boolean addDefaultAdapters);
}
