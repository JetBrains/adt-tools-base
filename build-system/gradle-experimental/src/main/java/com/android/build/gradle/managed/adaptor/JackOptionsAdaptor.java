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
import com.android.build.gradle.internal.dsl.CoreJackOptions;
import com.android.build.gradle.managed.JackOptions;
import com.android.build.gradle.managed.KeyValuePair;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * An adaptor to convert {@link JackOptions} to {@link CoreJackOptions}
 */
public class JackOptionsAdaptor implements CoreJackOptions {

    @NonNull
    private final JackOptions jackOptions;

    public JackOptionsAdaptor(@NonNull JackOptions jackOptions) {
        this.jackOptions = jackOptions;
    }

    @Override
    public Boolean isEnabled() {
        return jackOptions.getEnabled();
    }

    @Override
    public Boolean isJackInProcess() {
        return jackOptions.getJackInProcess();
    }

    @NonNull
    @Override
    public Map<String, String> getAdditionalParameters() {
        return jackOptions.getAdditionalParameters().values().stream()
                .collect(Collectors.toMap(
                        KeyValuePair::getName,
                        KeyValuePair::getValue));
    }
}
