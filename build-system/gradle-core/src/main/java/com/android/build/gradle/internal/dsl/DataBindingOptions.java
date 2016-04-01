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

package com.android.build.gradle.internal.dsl;

/**
 * DSL object for configuring databinding options.
 */
public class DataBindingOptions implements com.android.builder.model.DataBindingOptions {
    private String version;
    private boolean enabled = false;
    private boolean addDefaultAdapters = true;

    /**
     * The version of data binding to use.
     */
    @Override
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Whether to enable data binding.
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Whether to add the default data binding adapters.
     */
    @Override
    public boolean getAddDefaultAdapters() {
        return addDefaultAdapters;
    }

    public void setAddDefaultAdapters(boolean addDefaultAdapters) {
        this.addDefaultAdapters = addDefaultAdapters;
    }
}
