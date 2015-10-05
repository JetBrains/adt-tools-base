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
package com.android.ddmlib.logcat;

import junit.framework.TestCase;

public class LogCatTimestampTest extends TestCase {

    public void testParseFromString() throws Exception {
        String time = "01-23 12:34:56.789";
        LogCatTimestamp parsed = LogCatTimestamp.fromString(time);
        assertEquals(parsed.toString(), time);
    }

    public void testTimestampComparisons() throws Exception {
        LogCatTimestamp lateDec = LogCatTimestamp.fromString("12-31 23:59:59.999");
        LogCatTimestamp earlyJan = LogCatTimestamp.fromString("01-01 00:00:00.123");
        LogCatTimestamp midYearMorning1 = LogCatTimestamp.fromString("06-15 06:00:30.000");
        LogCatTimestamp midYearMorning2 = LogCatTimestamp.fromString("06-15 06:01:20.777");
        LogCatTimestamp midYearMorning3 = LogCatTimestamp.fromString("06-15 06:01:30.666");
        LogCatTimestamp midYearMorning4 = LogCatTimestamp.fromString("06-15 06:01:30.888");
        LogCatTimestamp midYearNextDay = LogCatTimestamp.fromString("06-16 00:00:00.000");

        assertTrue(lateDec.isBefore(earlyJan));
        assertTrue(earlyJan.isBefore(midYearMorning1));
        assertTrue(midYearMorning1.isBefore(midYearMorning2));
        assertTrue(midYearMorning2.isBefore(midYearMorning3));
        assertTrue(midYearMorning3.isBefore(midYearMorning4));
        assertTrue(midYearMorning4.isBefore(midYearNextDay));

        assertFalse(midYearNextDay.isBefore(midYearMorning4));
        assertFalse(midYearMorning4.isBefore(midYearMorning3));
        assertFalse(midYearMorning3.isBefore(midYearMorning2));
        assertFalse(midYearMorning2.isBefore(midYearMorning1));
        assertFalse(midYearMorning1.isBefore(earlyJan));
        assertFalse(earlyJan.isBefore(lateDec));

        assertFalse(earlyJan.isBefore(earlyJan));
    }

    public void testTimestampMillisecondsTruncated() throws Exception {
        LogCatTimestamp msTimestamp = LogCatTimestamp.fromString("01-01 00:00:00.1234");
        assertEquals(msTimestamp.toString(), "01-01 00:00:00.123");
    }
}
