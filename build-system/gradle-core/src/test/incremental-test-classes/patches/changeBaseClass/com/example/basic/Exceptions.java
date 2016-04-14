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

public class Exceptions {
    public static class MyException extends Exception {
        public String message;
        public MyException(String message) {
            this.message = message;
        }
    }

    public Exceptions() {
    }

    public void throwsNamed() throws MyException {
        throw new MyException("patched");
    }

    protected void protectedThrowsNamed() throws MyException {
        throw new MyException("protected_p");
    }

    static void staticThrowsNamed() throws MyException {
        throw new MyException("static_p");
    }

    Exceptions(String unused) throws MyException {
        throw new MyException("ctr_p");
    }

    public String catchesNamed() {
        String ret = "";
        try {
            ret += "before_p";
            throwsNamed();
        } catch (MyException e) {
            ret += ":caught_p[" + e.message + "]";
        } finally {
            ret += ":finally_p";
        }
        return ret;
    }

    public String catchesHiddenNamed() {
        String ret = "caught_p: ";
        try {
            protectedThrowsNamed();
        } catch (MyException e) {
            ret += ";" + e.message;
        }
        try {
            staticThrowsNamed();
        } catch (MyException e) {
            ret += ";" + e.message;
        }
        try {
            new Exceptions("");
        } catch (MyException e) {
            ret += ";" + e.message;
        }
        return ret;
    }


    public void throwsRuntime() {
        throw new RuntimeException("patched");
    }

    public String catchesRuntime() {
        String ret = "";
        try {
            ret += "before_p";
            throwsRuntime();
        } catch (RuntimeException e) {
            ret += ":caught_p[" + e.getMessage() + "]";
        } finally {
            ret += ":finally_p";
        }
        return ret;
    }
}
