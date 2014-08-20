/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.apigenerator;

import com.android.utils.Pair;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Represents a class and its methods/fields.
 * This is used to write the simplified XML file containing all the public API.
 *
 */
public class ApiClass {

    private final String mName;
    private final int mSince;

    private final List<Pair<String, Integer>> mSuperClasses =
            new ArrayList<Pair<String, Integer>>();

    private final List<Pair<String, Integer>> mInterfaces = new ArrayList<Pair<String, Integer>>();

    private final Map<String, Integer> mFields = new HashMap<String, Integer>();
    private final Map<String, Integer> mMethods = new HashMap<String, Integer>();

    public ApiClass(String name, int since) {
        mName = name;
        mSince = since;
    }

    public String getName() {
        return mName;
    }

    int getSince() {
        return mSince;
    }

    public void addField(String name, int since) {
        Integer i = mFields.get(name);
        if (i == null || i.intValue() > since) {
            mFields.put(name, Integer.valueOf(since));
        }
    }

    public void addMethod(String name, int since) {
        Integer i = mMethods.get(name);
        if (i == null || i.intValue() > since) {
            mMethods.put(name, Integer.valueOf(since));
        }
    }

    public Map<String, Integer> getMethods() {
        return mMethods;
    }

    public void replaceMethods(Map<String, Integer> fixedMethods) {
        mMethods.clear();
        mMethods.putAll(fixedMethods);
    }

    public void addSuperClass(String superClass, int since) {
        addToArray(mSuperClasses, superClass, since);
    }

    public List<Pair<String, Integer>> getSuperClasses() {
        return mSuperClasses;
    }

    public void addInterface(String interfaceClass, int since) {
        addToArray(mInterfaces, interfaceClass, since);
    }

    public List<Pair<String, Integer>> getInterfaces() {
        return mInterfaces;
    }

    void addToArray(List<Pair<String, Integer>> list, String name, int value) {
        // check if we already have that name (at a lower level)
        for (Pair<String, Integer> pair : list) {
            if (name.equals(pair.getFirst()) && pair.getSecond() < value) {
                return;
            }
        }

        list.add(Pair.of(name, Integer.valueOf(value)));
    }

    public void print(PrintStream stream) {
        stream.print("\t<class name=\"");
        stream.print(mName);
        stream.print("\" since=\"");
        stream.print(mSince);
        stream.println("\">");

        print(mSuperClasses, "extends", stream);
        print(mInterfaces, "implements", stream);
        print(mMethods, "method", stream);
        print(mFields, "field", stream);

        stream.println("\t</class>");
    }

    private void print(List<Pair<String, Integer> > list, String name, PrintStream stream) {
        Collections.sort(list, new Comparator<Pair<String, Integer> >() {

            @Override
            public int compare(Pair<String, Integer> o1, Pair<String, Integer> o2) {
                return o1.getFirst().compareTo(o2.getFirst());
            }
        });

        for (Pair<String, Integer> pair : list) {
            if (mSince == pair.getSecond()) {
                stream.print("\t\t<");
                stream.print(name);
                stream.print(" name=\"");
                stream.print(encodeAttribute(pair.getFirst()));
                stream.println("\" />");
            } else {
                stream.print("\t\t<");
                stream.print(name);
                stream.print(" name=\"");
                stream.print(encodeAttribute(pair.getFirst()));
                stream.print("\" since=\"");
                stream.print(pair.getSecond());
                stream.println("\" />");
            }
        }
    }

    private void print(Map<String, Integer> map, String name, PrintStream stream) {
        TreeMap<String, Integer> map2 = new TreeMap<String, Integer>(map);

        for (Entry<String, Integer> entry : map2.entrySet()) {
            if (mSince == entry.getValue()) {
                stream.print("\t\t<");
                stream.print(name);
                stream.print(" name=\"");
                stream.print(encodeAttribute(entry.getKey()));
                stream.println("\" />");
            } else {
                stream.print("\t\t<");
                stream.print(name);
                stream.print(" name=\"");
                stream.print(encodeAttribute(entry.getKey()));
                stream.print("\" since=\"");
                stream.print(entry.getValue());
                stream.println("\" />");
            }
        }
    }

    private String encodeAttribute(String attribute) {
        StringBuilder sb = new StringBuilder();
        int n = attribute.length();
        // &, ", ' and < are illegal in attributes; see http://www.w3.org/TR/REC-xml/#NT-AttValue
        // (' legal in a " string and " is legal in a ' string but here we'll stay on the safe
        // side)
        for (int i = 0; i < n; i++) {
            char c = attribute.charAt(i);
            if (c == '"') {
                sb.append("&quot;"); //$NON-NLS-1$
            } else if (c == '<') {
                sb.append("&lt;"); //$NON-NLS-1$
            } else if (c == '\'') {
                sb.append("&apos;"); //$NON-NLS-1$
            } else if (c == '&') {
                sb.append("&amp;"); //$NON-NLS-1$
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return mName;
    }
}
