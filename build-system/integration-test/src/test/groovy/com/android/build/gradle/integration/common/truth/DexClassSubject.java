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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.utils.XmlHelper;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;

import org.w3c.dom.Node;

@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
public class DexClassSubject extends Subject<DexClassSubject, Node> {

    static class Factory extends SubjectFactory<DexClassSubject, Node> {
        @NonNull
        public static Factory get() {
            return new Factory();
        }

        private Factory() {}

        @Override
        public DexClassSubject getSubject(
                @NonNull FailureStrategy failureStrategy,
                @NonNull Node subject) {
            return new DexClassSubject(failureStrategy, subject);
        }
    }

    public DexClassSubject(
            @NonNull FailureStrategy failureStrategy,
            @Nullable Node subject) {
        super(failureStrategy, subject);
    }

    public void hasMethod(@NonNull String name) {
        failIfSubjectIsNull();
        if (getSubject() != null && !checkHasMethod(name)) {
            fail("does not contain method", name);
        }
    }

    public void hasMethods(@NonNull String ... names) {
        for (String name: names) {
            hasMethod(name);
        }
    }

    public void hasField(@NonNull String name) {
        failIfSubjectIsNull();
        if (getSubject() != null && !checkHasField(name)) {
            fail("does not contain field", name);
        }
    }

    public void doesNotHaveField(@NonNull String name) {
        failIfSubjectIsNull();
        if (getSubject() != null &&  checkHasField(name)) {
            fail("should not contain field", name);
        }
    }

    public void doesNotHaveMethod(@NonNull String name) {
        failIfSubjectIsNull();
        if (getSubject() != null &&  checkHasMethod(name)) {
            fail("should not contain method", name);
        }
    }

    private boolean checkHasMethod(@NonNull String name) {
        return XmlHelper.findChildWithTagAndAttrs(getSubject(), "method", "name", name) != null;
    }

    private boolean checkHasField(@NonNull String name) {
        return XmlHelper.findChildWithTagAndAttrs(getSubject(), "field", "name", name) != null;
    }

    private void failIfSubjectIsNull() {
        if (getSubject() == null) {
            fail("Cannot assert about the contents of a dex class that does not exist.");
        }
    }

    @Override
    protected String getDisplaySubject() {
        String subjectName = null;
        if (getSubject() != null) {
            subjectName = getSubject().getAttributes().getNamedItem("name").getTextContent();
        }
        if (internalCustomName() != null) {
            return internalCustomName() + " (<" + subjectName + ">)";
        } else {
            return "<" + subjectName + ">";
        }
    }
}
