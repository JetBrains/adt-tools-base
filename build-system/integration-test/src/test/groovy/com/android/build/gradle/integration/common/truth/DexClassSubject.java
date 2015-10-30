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
import com.android.build.gradle.integration.common.utils.XmlHelper;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;

import org.w3c.dom.Node;

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


    public DexClassSubject(FailureStrategy failureStrategy,
            Node subject) {
        super(failureStrategy, subject);
        named(subject.getAttributes().getNamedItem("name").getTextContent());
    }

    public void hasMethod(String name) {
        if (!checkHasMethod(name)) {
            fail("does not contain method", name);
        }
    }

    public void hasField(String name) {
        if (!checkHasField(name)) {
            fail("does not contain field", name);
        }
    }

    public void doesNotHaveField(String name) {
        if (checkHasField(name)) {
            fail("should not contain field", name);
        }
    }

    public void doesNotHaveMethod(String name) {
        if (checkHasMethod(name)) {
            fail("should not contain method", name);
        }
    }

    private boolean checkHasMethod(String name) {
        return XmlHelper.findChildWithTagAndAttrs(getSubject(), "method", "name", name) != null;
    }

    private boolean checkHasField(String name) {
        return XmlHelper.findChildWithTagAndAttrs(getSubject(), "field", "name", name) != null;
    }
}
