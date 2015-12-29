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

package com.android.tools.lint.detector.api;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import junit.framework.TestCase;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;

public class CategoryTest extends TestCase {
    public void testCompare() throws Exception {
        List<Category> categories = Lists.newArrayList();
        for (Field field : Category.class.getDeclaredFields()) {
            if (field.getType() == Category.class &&
                    (field.getModifiers() & Modifier.STATIC) != 0) {
                field.setAccessible(true);
                Object o = field.get(null);
                if (o instanceof Category) {
                    categories.add((Category) o);
                }
            }
        }

        Collections.sort(categories);

        assertEquals(""
                + "Lint\n"
                + "Correctness\n"
                + "Correctness:Messages\n"
                + "Security\n"
                + "Performance\n"
                + "Usability:Typography\n"
                + "Usability:Icons\n"
                + "Usability\n"
                + "Accessibility\n"
                + "Internationalization\n"
                + "Internationalization:Bidirectional Text",
                Joiner.on("\n").join(categories));
    }

    public void testGetName() {
        assertEquals("Messages", Category.MESSAGES.getName());
    }

    public void testGetFullName() {
        assertEquals("Correctness:Messages", Category.MESSAGES.getFullName());
    }

    public void testEquals() {
        assertEquals(Category.MESSAGES, Category.MESSAGES);
        assertEquals(Category.create("Correctness", 100), Category.create("Correctness", 100));
        assertFalse(Category.MESSAGES.equals(Category.CORRECTNESS));
        assertFalse(Category.create("Correctness", 100).equals(Category.create("Correct", 100)));
    }
}