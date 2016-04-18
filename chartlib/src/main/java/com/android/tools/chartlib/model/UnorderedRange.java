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

package com.android.tools.chartlib.model;

/**
 * An unordered range allows for fuzzy input but accurate output.
 * setStart and setEnd don't expect the 'correct' values but
 * getMin and getMax always return the 'correct' value.
 *
 * Values are stored in two slots and can be populated with no
 * impact on range validity.
 */
public class UnorderedRange {

    private double start;
    private double end;

    public UnorderedRange(double start){
        this.start = start;
        this.end = start;
    }

    public void set(double start, double end){
        this.start = start;
        this.end = end;
    }

    public void reset(double start){
        this.start = start;
        this.end = start;
    }

    public void setEnd(double end){
       this.end = end;
    }

    public double getMin(){
        if (start < end) {
            return start;
        } else {
            return end;
        }
    }

    public double getMax(){
        if (start > end) {
            return start;
        } else {
            return end;
        }
    }

    public double getLength(){
        return getMax() - getMin();
    }

    public boolean isEmpty() {
        return start == end;
    }

    public void add(double v) {
        this.start += v;
        this.end += v;
    }
}
