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

package com.android.build.gradle.internal.pipeline;

import static org.junit.Assert.fail;

import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.DefaultContentType;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests for the {@link ExtendedContentType} and {@link DefaultContentType}
 */
public class ExtendedContentTypeTest {

    @Test
    public void testValueUniqueness() {
        Map<Integer, ContentType> allContentTypesValues = new HashMap<Integer, ContentType>();
        for (ContentType contentType : ExtendedContentType.getAllContentTypes()) {
            if (allContentTypesValues.containsKey(contentType.getValue())) {
                fail("Content types " + contentType.name() + " and "
                        + allContentTypesValues.get(contentType.getValue()).name()
                        + " have the same value : " + contentType.getValue());
            } else {
                allContentTypesValues.put(contentType.getValue(), contentType);
            }
        }
    }
}
