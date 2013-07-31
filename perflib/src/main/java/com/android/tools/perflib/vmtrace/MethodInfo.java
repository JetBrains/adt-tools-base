/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.perflib.vmtrace;

import java.util.Locale;

public class MethodInfo {
    public final long id;
    public final String className;
    public final String methodName;
    public final String signature;
    public final String srcPath;
    public final int srcLineNumber;

    public MethodInfo(long id, String className, String methodName, String signature, String srcPath,
            int srcLineNumber) {
        this.id = id;
        this.className = className;
        this.methodName = methodName;
        this.signature = signature;
        this.srcPath = srcPath;
        this.srcLineNumber = srcLineNumber;
    }

    public String getName() {
        return String.format(Locale.US, "%s.%s: %s", className, methodName, signature);
    }
}
