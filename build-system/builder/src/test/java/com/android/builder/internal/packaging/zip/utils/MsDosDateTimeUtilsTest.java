/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.builder.internal.packaging.zip.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Assert;
import org.junit.Test;

import java.util.Calendar;

public class MsDosDateTimeUtilsTest {
    @Test
    public void packDate() throws Exception {
        Calendar c = Calendar.getInstance();

        c.set(Calendar.YEAR, 2016);
        c.set(Calendar.MONTH, 0);
        c.set(Calendar.DAY_OF_MONTH, 5);

        long time = c.getTime().getTime();

        int packed = MsDosDateTimeUtils.packDate(time);

        // Year = 2016 - 1980 = 36 (0000 0000 0[010 0100])
        // Month = 1 (0000 0000 0000 [0001])
        // Day = 5 (0000 0000 000[0 0101])
        // Packs as 010 0100 | 0001 | 00101
        // Or       0100 1000 0010 0101
        // In hex      4    8    2    5

        int expectedDateBits = 0x4825;
        assertEquals(expectedDateBits, packed);
    }

    @Test
    public void packTime() throws Exception {
        Calendar c = Calendar.getInstance();

        c.set(Calendar.HOUR_OF_DAY, 8);
        c.set(Calendar.MINUTE, 45);
        c.set(Calendar.SECOND, 20);

        long time = c.getTime().getTime();

        int packed = MsDosDateTimeUtils.packTime(time);

        // Hour = 8 (0000 0000 000[0 1000])
        // Minute = 45 (0000 0000 00[10 1101])
        // Second = 20 / 2 = 10 (0000 0000 000[0 1010])
        // Pack as 0 1000 | 10 1101 | 0 1010
        // Or      0100 0101 1010 1010
        // In hex     4    5    A    A

        int expectedTimeBits = 0x45AA;
        assertEquals(expectedTimeBits, packed);
    }
}
