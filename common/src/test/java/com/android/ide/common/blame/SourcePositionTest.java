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

package com.android.ide.common.blame;

import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Test;

public class SourcePositionTest {

    @Test
    public void testComparisonOffset() {
        SourcePosition pos1 = new SourcePosition(-1, -1, 5, -1, -1, 20);
        SourcePosition posInner = new SourcePosition(-1, -1, 6, -1, -1, 8);
        SourcePosition posOverlap = new SourcePosition(-1, -1, 18, -1, -1, 29);

        doComparisons(pos1, posInner, posOverlap);
    }


    @Test
    public void testComparisonLineColumn() {
        SourcePosition pos1 = new SourcePosition(1, 8, -1, 7, 1, -1);
        SourcePosition posInner = new SourcePosition(2, 0, -1, 5, 9, -1);
        SourcePosition posOverlap = new SourcePosition(6, 23, -1, 10, 200, -1);

        doComparisons(pos1, posInner, posOverlap);
    }

    /**
     * Three positions as follows:
     *
     *  <pre>
     *  |-------pos1--------|
     *      |--posInner-|
     *                    |-----posOverlap----|
     *  --------------------------------------------->
     *                position in file
     *  </pre>
     */
    private static void doComparisons(
            SourcePosition pos1,
            SourcePosition posInner,
            SourcePosition posOverlap) {

        assertTrue(posInner.compareStart(posInner) == 0);
        assertTrue(posInner.compareEnd(posInner) == 0);

        assertTrue(pos1.compareStart(posInner) < 0);
        assertTrue(posInner.compareStart(pos1) > 0);
        assertTrue(pos1.compareEnd(posInner) > 0);
        assertTrue(posInner.compareEnd(pos1) < 0);


        assertTrue(posInner.compareStart(posOverlap) < 0);
        assertTrue(posOverlap.compareStart(posInner) > 0);
        assertTrue(posInner.compareEnd(posOverlap) < 0);
        assertTrue(posOverlap.compareEnd(posInner) > 0);

        assertTrue(pos1.compareStart(posOverlap) < 0);
        assertTrue(posOverlap.compareStart(pos1) > 0);
        assertTrue(pos1.compareEnd(posOverlap) < 0);
        assertTrue(posOverlap.compareEnd(pos1) > 0);


    }
}
