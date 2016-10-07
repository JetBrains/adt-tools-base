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

    public static final SubjectFactory<DexClassSubject, Node> FACTORY
            = new SubjectFactory<DexClassSubject, Node>() {
        @Override
        public DexClassSubject getSubject(
                @NonNull FailureStrategy failureStrategy,
                @Nullable Node subject) {
            return new DexClassSubject(failureStrategy, subject);
        }
    };

    private DexClassSubject(
            @NonNull FailureStrategy failureStrategy,
            @Nullable Node subject) {
        super(failureStrategy, subject);
    }

    public void hasMethod(@NonNull String name) {
        if (assertSubjectIsNonNull() && !checkHasMethod(name)) {
            fail("contains method", name);
        }
    }

    public void hasMethods(@NonNull String... names) {
        if (assertSubjectIsNonNull()) {
            for (String name : names) {
                hasMethod(name);
            }
        }
    }

    public void hasField(@NonNull String name) {
        if (assertSubjectIsNonNull() && !checkHasField(name)) {
            fail("contains field", name);
        }
    }

    public void doesNotHaveField(@NonNull String name) {
        if (assertSubjectIsNonNull() && checkHasField(name)) {
            fail("does not contain field", name);
        }
    }

    public void doesNotHaveMethod(@NonNull String name) {
        if (assertSubjectIsNonNull() && checkHasMethod(name)) {
            fail("does not contain method", name);
        }
    }

    /**
     * Should not be called when the subject is null.
     */
    private boolean checkHasMethod(@NonNull String name) {
        return XmlHelper.findChildWithTagAndAttrs(getSubject(), "method", "name", name) != null;
    }

    /**
     * Should not be called when the subject is null.
     */
    private boolean checkHasField(@NonNull String name) {
        return XmlHelper.findChildWithTagAndAttrs(getSubject(), "field", "name", name) != null;
    }

    private boolean assertSubjectIsNonNull() {
        if (getSubject() == null) {
            fail("Cannot assert about the contents of a dex class that does not exist.");
            return false;
        }
        return true;
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
