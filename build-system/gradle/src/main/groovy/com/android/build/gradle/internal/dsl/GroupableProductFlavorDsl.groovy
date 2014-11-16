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

package com.android.build.gradle.internal.dsl
import com.android.annotations.NonNull
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.api.GroupableProductFlavor
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.internal.reflect.Instantiator

/**
 * A version of {@link ProductFlavorDsl} that can receive a dimension name.
 */
public class GroupableProductFlavorDsl extends ProductFlavorDsl implements GroupableProductFlavor {

    /** Name of the dimension this product flavor belongs to. */
    String flavorDimension

    public GroupableProductFlavorDsl(
            @NonNull String name,
            @NonNull Project project,
            @NonNull Instantiator instantiator,
            @NonNull Logger logger) {
        super(name, project, instantiator, logger)
    }
}
