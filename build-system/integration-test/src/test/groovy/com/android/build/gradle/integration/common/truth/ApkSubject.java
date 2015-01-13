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

package com.android.build.gradle.integration.common.truth;

import com.android.build.gradle.integration.common.utils.ApkHelper;
import com.android.ide.common.process.ProcessException;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;

import java.io.File;
import java.io.IOException;

/**
 * Truth support for apk files.
 */
public class ApkSubject extends Subject<ApkSubject, File> {

    public ApkSubject(FailureStrategy failureStrategy, File subject) {
        super(failureStrategy, subject);
    }

    public void containsClass(String className) throws IOException, ProcessException {
        File apk = getSubject();
        if (!ApkHelper.checkForClass(apk, className)) {
            failWithRawMessage("'%s' does not contain '%s'", apk.getName(), className);
        }
    }

    public void doesNotContainClass(String className) throws IOException, ProcessException {
        File apk = getSubject();
        if (ApkHelper.checkForClass(apk, className)) {
            failWithRawMessage("'%s' unexpectedly contain '%s'", apk.getName(), className);
        }
    }
}
