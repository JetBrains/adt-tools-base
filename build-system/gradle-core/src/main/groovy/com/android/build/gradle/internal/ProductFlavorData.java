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

package com.android.build.gradle.internal
import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.build.gradle.internal.dsl.CoreProductFlavor
import com.android.builder.core.BuilderConstants
import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.Task
/**
 * Class containing a ProductFlavor and associated data (sourcesets)
 */
@CompileStatic
public class ProductFlavorData<T extends CoreProductFlavor> extends VariantDimensionData {
    final T productFlavor
    final Task assembleTask

    ProductFlavorData(
            @NonNull T productFlavor,
            @NonNull DefaultAndroidSourceSet sourceSet,
            @Nullable DefaultAndroidSourceSet androidTestSourceSet,
            @Nullable DefaultAndroidSourceSet unitTestSourceSet,
            @NonNull Project project) {
        super(sourceSet, androidTestSourceSet, unitTestSourceSet, project)

        this.productFlavor = productFlavor

        if (!BuilderConstants.MAIN.equals(sourceSet.name)) {
            assembleTask = project.tasks.create("assemble${sourceSet.name.capitalize()}")
            assembleTask.description = "Assembles all ${sourceSet.name.capitalize()} builds."
            assembleTask.setGroup("Build")
        } else {
            assembleTask = null
        }
    }
}
