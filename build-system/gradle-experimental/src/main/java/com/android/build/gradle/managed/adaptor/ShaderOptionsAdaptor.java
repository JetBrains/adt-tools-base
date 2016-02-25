/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.managed.adaptor;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.dsl.CoreShaderOptions;
import com.android.build.gradle.managed.ScopedShaderOptions;
import com.android.build.gradle.managed.ShaderOptions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import org.gradle.model.ModelMap;

import java.util.List;

/**
 * An adaptor to convert {@link ShaderOptions} to {@link CoreShaderOptions}
 */
public class ShaderOptionsAdaptor implements CoreShaderOptions {

    private ShaderOptions shaderOptions;

    public ShaderOptionsAdaptor(@NonNull ShaderOptions shaderOptions) {
        this.shaderOptions = shaderOptions;
    }

    @Override
    public List<String> getGlslcArgs() {
        return shaderOptions.getGlslcArgs();
    }

    @Override
    public ListMultimap<String, String> getScopedGlslcArgs() {
        ModelMap<ScopedShaderOptions> scopedArgsMap = shaderOptions.getScopedArgs();

        ListMultimap<String, String> map = ArrayListMultimap.create();

        for (String key : scopedArgsMap.keySet()) {
            ScopedShaderOptions scopedArgs = scopedArgsMap.get(key);
            map.putAll(key, scopedArgs.getGlslcArgs());
        }

        return map;
    }
}
