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

package com.android.tools.perflib.heap.hprof;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HprofStringBuilder {
    private int mTime;
    private Map<String, Integer> mStrings;
    private List<HprofString> mStringRecords;

    public HprofStringBuilder(int time) {
        mTime = time;
        mStrings = new HashMap<String, Integer>();
        mStringRecords = new ArrayList<HprofString>();
    }

    /**
     * Return an ID to use for the given string.
     */
    public int get(String string) {
        Integer id = mStrings.get(string);
        if (id == null) {
            id = mStrings.size()+1;
            mStrings.put(string, id);
            mStringRecords.add(new HprofString(mTime, id, string));
        }
        return id;
    }

    /**
     * Return the list of string definitions needed for all the strings used.
     */
    public List<HprofString> getStringRecords() {
        return mStringRecords;
    }
}
