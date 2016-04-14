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

import java.util.List;

public class Constructors {

    public String value;
    public Constructors(String value) {
        this.value = value;
    }

    public static class Base {

        private final String baseFinal;

        public Base(double a, String b, int c) {
            baseFinal = a + b + c + ":patched_base";
        }

        protected Base() {
            baseFinal = "base:";
        }

        public String getBaseFinal() {
            return baseFinal;
        }
    }

    abstract static class AbstractBase {
        private final String baseFinal;

        public AbstractBase(double a, String b, int c) {
            baseFinal = "patched_abstract_base:" + a + b + c;
        }

        protected AbstractBase() {
            baseFinal = "patched_abstract base:";
        }

        public String getBaseFinal() {
            return baseFinal;
        }
    }

    public static class SubOfAbstract extends AbstractBase {
        private final String subFinal;

        public String value;

        public SubOfAbstract(int a, int b, int c, int d) {
            super(c, "" + b + c, c + d);
            subFinal = ":patched_sub_abstract";
            value = "patched_SubOfAbstract(int, int, int, int)";
        }

        public String getSubFinal() {
            return subFinal;
        }
    }

    public static class Sub extends Base {

        private final String subFinal;

        public String value;

        public Sub(int a, int b, int c, int d) {
            super(c = 3, "" + b + c, c + d);
            subFinal = ":patched_sub";
            value = "patched_Sub(int, int, int, int)" + c;
        }

        public Sub(double a, String b, int c) {
            super(a, callMeBefore(b), c);
            subFinal =  a + b + c + ":patched_sub";
            value = "patched_Sub(double, String, int)";
        }

        public Sub(long l, float f) {
            this(f, callMeBefore(String.valueOf(l + 1) + "*"), 0);
            value = "patched_Sub(long, float)";
        }

        public Sub(boolean b) {
            this(b ? 1.0 : 0.0, b ? "true" : "false", b ? 1 : 0);
            value = "Sub(boolean)";
        }

        public Sub(int x, int y, int z) {
            this((x = 9) + 0.1, "" + x + y, z);
            value = "Sub(" + x + ", " + y + ", " + z + ")";
        }

        public Sub(String string, boolean condition) {
            super(10d, string, 20);
            subFinal = "updated sub";
            try {
                Utility.doSomething(!condition);
            } catch(ArithmeticException e) {
                throw new IllegalArgumentException("updated iae " + e.getMessage());
            }
        }

        public Sub(String string, String exceptionMessage, boolean condition) {
            super(11d, string, 20);
            subFinal = "updated sub";
            try {
                Utility.doSomething(false);
            } catch(ArithmeticException e) {
                throw new IllegalArgumentException("iae " + e.getMessage());
            }
            try {
                Utility.doSomething(!condition);
            } catch(ArithmeticException e) {
                throw new IllegalArgumentException(exceptionMessage + " " + e.getMessage());
            }
        }

        public Sub(String string, boolean condition, String exceptionMessage) {
            super(1d, string, 2);
            subFinal = "subFinal";
            try {
                try {
                    Utility.doSomething(!condition);
                } catch (ArithmeticException e) {
                    throw new IllegalArgumentException("updated iae " + e.getMessage());
                }
            } catch(IllegalArgumentException e) {
                throw new RuntimeException(e.getMessage() + " " + exceptionMessage);
            }
        }

        public Sub() {
            super(100d, "updated sub", 30);
            throw new IllegalArgumentException("pass me an updated string !");
        }

        public Sub(List<String> params, boolean condition) {
            super(101d, params.get(0), 33);
            try {
                Utility.doSomething(!condition);
            } catch(ArithmeticException e) {
                throw new RuntimeException(e.getMessage() + " " + params.get(1));
            } finally {
                subFinal = "updated subFinal";
            }
        }

        public Sub(boolean condition, List<String> params) {
            super(1d, params.get(0), 2);
            try {
                Utility.doSomething(false);
                subFinal = "updated success";
            } catch(ArithmeticException e) {
                throw new IllegalArgumentException(e.getMessage() + " updated " + params.get(1));
            } finally {
                if (condition) {
                    throw new RuntimeException("updated " + params.get(1));
                }
            }
        }

        public String getSubFinal() {
            return subFinal;
        }

        public static String callMeBefore(String s){
            return "(" + s + ")";
        }
    }


    private static class Utility {
        private static void doSomething(boolean raise) throws ArithmeticException {
            if (raise) {
                throw new ArithmeticException("underflow");
            }
        }
    }

    public class DupInvokeSpecialBase {
        public DupInvokeSpecialBase(DupInvokeSpecialBase a) { }
    }

    public class DupInvokeSpecialSub extends DupInvokeSpecialBase {
        public String value;
        public DupInvokeSpecialSub() {
            super(new DupInvokeSpecialBase(null));
            value = "patched";
        }
    }

    public static class PrivateConstructor {

        private String mString;

        private PrivateConstructor(String string) {
            mString = string;
        }

        public PrivateConstructor() {
            // Call to private constructor via dispatching this
            this("Patched");
        }

        public String getString() {
            return mString;
        }
    }
}
