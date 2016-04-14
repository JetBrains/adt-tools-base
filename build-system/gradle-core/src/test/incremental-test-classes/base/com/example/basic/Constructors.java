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
            baseFinal = "base:" + a + b + c;
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
            baseFinal = "abstract_base:" + a + b + c;
        }

        protected AbstractBase() {
            baseFinal = "abstract base:";
        }

        public String getBaseFinal() {
            return baseFinal;
        }
    }

    public static class SubOfAbstract extends AbstractBase {
        private final String subFinal;

        public String value;

        public SubOfAbstract(int a, int b, int c, int d) {
            super();
            subFinal = ":sub_abstract";
            value = "SubOfAbstract(int, int, int, int)";
        }

        public String getSubFinal() {
            return subFinal;
        }
    }

    public static class Sub extends Base {

        private final String subFinal;

        public String value;

        public Sub(int a, int b, int c, int d) {
            super();
            subFinal = ":sub";
            value = "Sub(int, int, int, int)";
        }

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

        public Sub(String string, boolean condition) {
            super(1d, string, 2);
            subFinal = "subFinal";
            try {
                Utility.doSomething(condition);
            } catch(ArithmeticException e) {
                throw new IllegalArgumentException("iae " + e.getMessage());
            }
        }

        public Sub(String string, String exceptionMessage, boolean condition) {
            super(1d, string, 2);
            subFinal = "subFinal";
            try {
                Utility.doSomething(false);
            } catch(ArithmeticException e) {
                throw new IllegalArgumentException("iae " + e.getMessage());
            }
            try {
                Utility.doSomething(condition);
            } catch(ArithmeticException e) {
                throw new IllegalArgumentException(exceptionMessage + " " + e.getMessage());
            }
        }

        public Sub(String string, boolean condition, String exceptionMessage) {
            super(1d, string, 2);
            subFinal = "subFinal";
            try {
                try {
                    Utility.doSomething(condition);
                } catch (ArithmeticException e) {
                    throw new IllegalArgumentException("iae " + e.getMessage());
                }
            } catch(IllegalArgumentException e) {
                throw new RuntimeException(exceptionMessage + " " + e.getMessage());
            }
        }

        public Sub(List<String> params, boolean condition) {
            super(1d, params.get(0), 2);
            try {
                Utility.doSomething(condition);
            } catch(ArithmeticException e) {
                throw new IllegalArgumentException(params.get(1) + " " + e.getMessage());
            } finally {
                subFinal = "success";
            }
        }

        public Sub(boolean condition, List<String> params) {
            super(1d, params.get(0), 2);
            try {
                Utility.doSomething(false);
                subFinal = "success";
            } catch(ArithmeticException e) {
                throw new IllegalArgumentException(params.get(1) + " " + e.getMessage());
            } finally {
                if (condition) {
                    throw new RuntimeException(params.get(1));
                }
            }
        }

        public Sub() {
            super(10, "foo", 3);
            throw new IllegalArgumentException("pass me a string !");
        }

        public String getSubFinal() {
            return subFinal;
        }

        public static String callMeBefore(String s){
            return "[" + s + "]";
        }
    }

    private static class Utility {
        private static void doSomething(boolean raise) throws ArithmeticException {
            if (raise) {
                throw new ArithmeticException("overflow");
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
            value = "original";
        }
    }

    public static class PrivateConstructor {

        private String mString;

        private PrivateConstructor(String string) {
            mString = string;
        }

        public PrivateConstructor() {
            this("Base");
        }

        public String getString() {
            return mString;
        }
    }
}
