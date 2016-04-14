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

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * Class with all types of access methods.
 */
public abstract class AllAccessMethods {

    private String privateMethod(double a, String b, int c) {
        return "private_method:" + a + b + c;
    }

    protected String protectedMethod(double a, String b, int c) {
        return "protected_method:" + a + b + c;
    }

    String packagePrivateMethod(double a, String b, int c) {
        return "package_private_method:" + a + b + c;
    }

    public String publicStringMethod(double a, String b, int c) {
        return "public_method:" + a + b + c;
    }

    public int publicIntMethod(int a) {
        return a*2;
    }

    public long publicLongMethod(long a) {
        return a*2;
    }

    public char publicCharMethod(char a) {
        return 'a';
    }

    public double publicDoubleMethod(double a) {
        return a*2;
    }

    public float publicFloatMethod(float a) {
        return a*2;
    }

    public boolean publicBooleanMethod(boolean a) {
        return !a;
    }

    public void voidMethod() {
    }

    public int[] publicIntArrayMethod(int[] a) {
        return new int[] {a[0], 2, 3};
    }

    public long[] publicLongArrayMethod(long[] a) {
        return new long[] {a[0], 4, 5};
    }

    public boolean[] publicBooleanArrayMethod(boolean[] a) {
        return new boolean[] {!a[0], true, false};
    }

    public char[] publicCharArrayMethod(char[] a) {
        return new char[] {a[0], 'b', 'c'};
    }


    public double[] publicDoubleArrayMethod(double[] a) {
        return new double[] {a[0], 6d, 7d};
    }

    public float[] publicFloatArrayMethod(float[] a) {
        return new float[] {a[0], 8f, 9f};
    }

    public String[] publicStringArrayMethod(String[] strings) {
        return new String[] {strings[0], "a", "b"};
    }

    private String privateMethodDispath(double a, String b, int c) {
        return privateMethod(a, b, c);
    }

    protected String protectedMethodDispatch(double a, String b, int c) {
        return protectedMethod(a, b, c);
    }

    String packagePrivateMethodDispatch(double a, String b, int c) {
        return packagePrivateMethod(a, b, c);
    }

    public String publicMethodDispatch(double a, String b, int c) {
        return publicStringMethod(a, b, c);
    }

    abstract public String abstractMethod(double a, String b, int c);

    // methods on the super class to check the overriden methods are invoked property from the
    // super class methods.
    private String doNotOverridePrivateMethodDispath(double a, String b, int c) {
        return privateMethod(a, b, c);
    }

    protected String doNotOverrideProtectedMethodDispatch(double a, String b, int c) {
        return protectedMethod(a, b, c);
    }

    String doNotOverridePackagePrivateMethodDispatch(double a, String b, int c) {
        return packagePrivateMethod(a, b, c);
    }

    public String doNotOverridePublicMethodDispatch(double a, String b, int c) {
        return publicStringMethod(a, b, c);
    }

    public List<String> invokeAll(double a, String b, int c) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        builder.add(privateMethod(a, b, c));
        builder.add(protectedMethod(a, b, c));
        builder.add(packagePrivateMethod(a, b, c));
        builder.add(publicStringMethod(a, b, c));
        builder.add(abstractMethod(a, b, c));
        return builder.build();
    }

    public List<String> invokeAllDispatches(double a, String b, int c) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        builder.add(privateMethodDispath(a, b, c));
        builder.add(protectedMethodDispatch(a, b, c));
        builder.add(packagePrivateMethodDispatch(a, b, c));
        builder.add(publicMethodDispatch(a, b, c));
        return builder.build();
    }

    public List<String> invokeAllDoNotOverrideDispatches(double a, String b, int c) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        builder.add(doNotOverridePrivateMethodDispath(a, b, c));
        builder.add(doNotOverrideProtectedMethodDispatch(a, b, c));
        builder.add(doNotOverridePackagePrivateMethodDispatch(a, b, c));
        builder.add(doNotOverridePublicMethodDispatch(a, b, c));
        return builder.build();
    }
}
