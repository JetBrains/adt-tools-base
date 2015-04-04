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
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.internal.reflect.Instantiator

import static com.android.build.gradle.internal.LoggingUtil.displayDeprecationWarning

/**
 * A version of {@link ProductFlavor} that can receive a dimension name.
 */
// TODO: Remove interface and implementors now ProductFlavor has dimension.
@Deprecated
public class GroupableProductFlavor
        extends ProductFlavor implements com.android.build.gradle.api.GroupableProductFlavor {

    public GroupableProductFlavor(
            @NonNull String name,
            @NonNull Project project,
            @NonNull Instantiator instantiator,
            @NonNull Logger logger) {
        super(name, project, instantiator, logger)
    }

    @Deprecated
    public void setFlavorDimension(String dimension) {
        displayDeprecationWarning(logger, project,
                "'flavorDimension' will be removed by Android Gradle Plugin 2.0, " +
                        "it has been replaced by 'dimension'.")
        setDimension(dimension);
    }

    /**
     * Name of the dimension this product flavor belongs to. Has been replaced by
     * <code>dimension</code>
     */
    @Deprecated
    public String getFlavorDimension() {
        displayDeprecationWarning(logger, project,
                "'flavorDimension' will be removed by Android Gradle Plugin 2.0, " +
                        "it has been replaced by 'dimension'.")
        return getDimension();
    }

}
