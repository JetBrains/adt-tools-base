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

package com.example.basic;

public class Constructors {

    public String value;
    public Constructors(String value) {
        this.value = value;
    }

    public static class Base {

        private final String baseFinal;

        public Base(double a, String b, int c) {
            baseFinal = "base:" + a + b + c;
        }

        public String getBaseFinal() {
            return baseFinal;
        }
    }

    public static class Sub extends Base {

        private final String subFinal;

        public String value;

        public Sub(double a, String b, int c) {
            super(a, callMeBefore(b), c);
            subFinal = "sub:" + a + b + c;
            value = "Sub(double, String, int)";
        }

        public Sub(long l, float f) {
            this(f, callMeBefore(String.valueOf(l)), 0);
            value = "Sub(long, float)";
        }

        public Sub(boolean b) {
            this(b ? 1.0 : 0.0, b ? "true" : "false", b ? 1 : 0);
            value = "Sub(boolean)";
        }

        public Sub(int x, int y, int z) {
            this((x = 2) + 0.1, "" + x + y, z);
            value = "Sub(" + x + ", " + y + ", " + z + ")";
        }

        public String getSubFinal() {
            return subFinal;
        }

        public static String callMeBefore(String s){
            return "[" + s + "]";
        }
    }

    public class DupInvokeSpecialBase {
        public DupInvokeSpecialBase(DupInvokeSpecialBase a) { }
    }

    public class DupInvokeSpecialSub extends DupInvokeSpecialBase {
        public String value;
        public DupInvokeSpecialSub() {
            super(new DupInvokeSpecialBase(null));
            value = "original";
        }
    }
}
