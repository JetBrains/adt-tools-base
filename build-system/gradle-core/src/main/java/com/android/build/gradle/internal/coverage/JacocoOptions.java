/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.internal.coverage;

import org.gradle.api.logging.Logging;

/**
 * DSL object for configuring JaCoCo settings.
 */
public class JacocoOptions {

    @SuppressWarnings("MethodMayBeStatic")
    @Deprecated
    public void setVersion(String version) {
        Logging.getLogger(JacocoOptions.class).warn(""
                + "It is no longer possible to set the Jacoco version in the "
                + "jacoco {} block.\n"
                + "To update the version of Jacoco without updating the android plugin,\n"
                + "add a buildscript dependency on a newer version, for example: "
                + "buildscript{"
                + "    dependencies {\n"
                + "        classpath\"org.jacoco:org.jacoco.core:0.7.4.201502262128\""
                + "    }"
                + "}");
    }


    /**
     * This will not affect the JaCoCo version used.
     *
     * @deprecated Use a build script dependency instead.
     */
    @SuppressWarnings("MethodMayBeStatic")
    @Deprecated
    public String getVersion() {
        throw new UnsupportedOperationException();
    }
}
