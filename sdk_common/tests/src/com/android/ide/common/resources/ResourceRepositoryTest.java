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
package com.android.ide.common.resources;

import static com.android.resources.ResourceType.ATTR;
import static com.android.resources.ResourceType.DIMEN;
import static com.android.resources.ResourceType.LAYOUT;
import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public class ResourceRepositoryTest extends TestCase {
    public void testParseResource() {
        assertNull(ResourceRepository.parseResource(""));
        assertNull(ResourceRepository.parseResource("not_a_resource"));

        assertEquals(LAYOUT, ResourceRepository.parseResource("@layout/foo").getFirst());
        assertEquals(DIMEN, ResourceRepository.parseResource("@dimen/foo").getFirst());
        assertEquals(DIMEN, ResourceRepository.parseResource("@android:dimen/foo").getFirst());
        assertEquals("foo", ResourceRepository.parseResource("@layout/foo").getSecond());
        assertEquals("foo", ResourceRepository.parseResource("@dimen/foo").getSecond());
        assertEquals("foo", ResourceRepository.parseResource("@android:dimen/foo").getSecond());

        assertEquals(ATTR, ResourceRepository.parseResource("?attr/foo").getFirst());
        assertEquals("foo", ResourceRepository.parseResource("?attr/foo").getSecond());

        assertEquals(ATTR, ResourceRepository.parseResource("?foo").getFirst());
        assertEquals("foo", ResourceRepository.parseResource("?foo").getSecond());

        assertEquals(ATTR, ResourceRepository.parseResource("?android:foo").getFirst());
        assertEquals("foo", ResourceRepository.parseResource("?android:foo").getSecond());
    }
}
