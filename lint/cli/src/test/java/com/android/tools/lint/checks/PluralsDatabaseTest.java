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

package com.android.tools.lint.checks;

import static com.android.tools.lint.checks.PluralsDatabase.Quantity;

import junit.framework.TestCase;

import java.util.EnumSet;

public class PluralsDatabaseTest extends TestCase {
    public void testGetRelevant() {
        PluralsDatabase db = PluralsDatabase.get();
        assertNull(db.getRelevant("unknown"));
        EnumSet<Quantity> relevant = db.getRelevant("en");
        assertNotNull(relevant);
        assertEquals(1, relevant.size());
        assertSame(Quantity.one, relevant.iterator().next());

        relevant = db.getRelevant("cs");
        assertNotNull(relevant);
        assertEquals(EnumSet.of(Quantity.few, Quantity.one), relevant);
    }

    public void testHasMultiValue() {
        PluralsDatabase db = PluralsDatabase.get();

        assertFalse(db.hasMultipleValuesForQuantity("en", Quantity.one));
        assertFalse(db.hasMultipleValuesForQuantity("en", Quantity.two));
        assertFalse(db.hasMultipleValuesForQuantity("en", Quantity.few));
        assertFalse(db.hasMultipleValuesForQuantity("en", Quantity.many));

        assertTrue(db.hasMultipleValuesForQuantity("br", Quantity.two));
        assertTrue(db.hasMultipleValuesForQuantity("mk", Quantity.one));
        assertTrue(db.hasMultipleValuesForQuantity("lv", Quantity.zero));
    }
}