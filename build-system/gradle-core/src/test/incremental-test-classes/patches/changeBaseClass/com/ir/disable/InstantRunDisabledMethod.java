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

package com.ir.disable;

import com.android.tools.ir.api.DisableInstantRun;

/**
 * Class that selectively disables some of its method
 */
public class InstantRunDisabledMethod {

    public static String alterableStaticMethod() {
        return "alterable static updated";
    }

    @DisableInstantRun
    public static String nonAlterableStaticMethod() {
        return "non alterable static updated";
    }

    final String stringField;

    public InstantRunDisabledMethod(String alterable) {
        stringField = "modified " + alterable;
    }

    public String getStringField() {
        return stringField;
    }

    @DisableInstantRun
    public InstantRunDisabledMethod() {
        stringField = "non alterable ctor updated";
    }

    public final String finalAlterableMethod() {
        return "final alterable updated";
    }

    @DisableInstantRun
    public final String finalNonAlterableMethod() {
        return "final non alterable updated";
    }

    public String alterableMethod() {
        return "alterable updated";
    }

    @DisableInstantRun
    public String nonAlterableMethod() {
        return "non alterable updated";
    }
}
