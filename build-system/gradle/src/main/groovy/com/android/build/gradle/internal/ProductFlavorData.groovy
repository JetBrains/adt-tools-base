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

import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.builder.DefaultProductFlavor
import org.gradle.api.Project
import org.gradle.api.Task

/**
 * Class containing a ProductFlavor and associated data (sourcesets)
 */
public class ProductFlavorData<T extends DefaultProductFlavor> {

    final T productFlavor

    final DefaultAndroidSourceSet sourceSet
    final DefaultAndroidSourceSet testSourceSet
    final ConfigurationProvider mainProvider
    final ConfigurationProvider testProvider

    Task assembleTask

    ProductFlavorData(T productFlavor,
                      DefaultAndroidSourceSet sourceSet, DefaultAndroidSourceSet testSourceSet,
                      Project project) {
        this.productFlavor = productFlavor
        this.sourceSet = sourceSet
        this.testSourceSet = testSourceSet
        mainProvider = new ConfigurationProviderImpl(project, sourceSet)
        testProvider = new ConfigurationProviderImpl(project, testSourceSet)
    }

    public static String getFlavoredName(ProductFlavorData[] flavorDataArray, boolean capitalized) {
        StringBuilder builder = new StringBuilder()
        for (ProductFlavorData data : flavorDataArray) {
            builder.append(capitalized ?
                data.productFlavor.name.capitalize() :
                data.productFlavor.name)
        }

        return builder.toString()
    }
}
