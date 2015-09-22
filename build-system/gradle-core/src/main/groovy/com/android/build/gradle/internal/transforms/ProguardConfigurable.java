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

package com.android.build.gradle.internal.transforms;

import com.android.build.transform.api.Transform;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.List;

/**
 * Base class for transforms that consume ProGuard configuration files.
 */
public abstract class ProguardConfigurable extends Transform {
    protected final List<Supplier<List<File>>> configurationFiles =
            Lists.newArrayListWithExpectedSize(3);

    public void configurationFiles(Supplier<List<File>> configFiles) {
        configurationFiles.add(configFiles);
    }

    protected List<File> getAllConfigurationFiles() {
        List<File> files = Lists.newArrayList();
        for (Supplier<List<File>> supplier : configurationFiles) {
            files.addAll(supplier.get());
        }
        return files;
    }
}
