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

package com.android.build.gradle.model

import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.TestVariantData
import com.android.builder.core.BuilderConstants
import groovy.transform.CompileStatic
import org.gradle.api.Task
import org.gradle.model.Mutate
import org.gradle.model.RuleSource
import org.gradle.model.collection.CollectionBuilder
import org.gradle.platform.base.BinaryContainer

import static com.android.builder.core.VariantType.ANDROID_TEST

/**
 * Plugin for creating test tasks for AndroidBinary.
 */
@SuppressWarnings("GrMethodMayBeStatic")
@CompileStatic
class AndroidComponentModelTestPlugin extends RuleSource {

    @Mutate
    void createConnectedTestTasks(
            CollectionBuilder<Task> tasks,
            BinaryContainer binaries,
            TaskManager taskManager,
            AndroidComponentSpec spec) {
        VariantManager variantManager = (spec as DefaultAndroidComponentSpec).variantManager
        binaries.withType(AndroidBinary) { androidBinary->
            DefaultAndroidBinary binary = androidBinary as DefaultAndroidBinary

            // TODO: compare against testBuildType instead of BuilderConstants.DEBUG.
            if (binary.buildType.name != BuilderConstants.DEBUG) {
                return
            }

            // Create test tasks.
            BaseVariantData testedVariantData = binary.variantData

            assert testedVariantData != null,
                    "Internal error: tested variant must be created before test variant."

            TestVariantData testVariantData =
                    variantManager.createTestVariantData(testedVariantData, ANDROID_TEST)
            variantManager.getVariantDataList().add(testVariantData);
            variantManager.createTasksForVariantData(
                    new TaskCollectionBuilderAdaptor(tasks),
                    testVariantData)
        }
    }
}
