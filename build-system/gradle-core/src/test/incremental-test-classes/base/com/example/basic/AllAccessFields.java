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

/**
 * Class that have various field types with all possible access rights.
 */
public class AllAccessFields {

    int packagePrivateInt = 3;
    protected int protectedInt = 7;
    public int publicInt = 9;

    String packagePrivateString = "foo";
    protected String protectedString = "foobar";
    public String publicString = "blahblahblah";

    int[] packagePrivateIntArray = { 1, 2, 3};
    protected int[] protectedIntArray = {1, 2, 3, 4, 5};
    public int[] publicIntArray = {1, 3, 5, 7};

    String[] packagePrivateStringArray = { "foo" };
    protected String[] protectedStringArray = { "foo", "bar" };
    public String[] publicStringArray = { "blah", "blah", "blah" };
}
