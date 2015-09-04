/*
 * Copyright (C) 2008 Google Inc.
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

package com.android.tools.perflib.heap;

import com.android.tools.perflib.captures.DataBuffer;
import com.android.tools.perflib.captures.MemoryMappedFileBuffer;

import java.io.File;
import java.util.Map;
import java.util.Set;

public class Main {

    public static void main(String argv[]) {
        try {
            long start = System.nanoTime();
            DataBuffer buffer = new MemoryMappedFileBuffer(new File(argv[0]));
            Snapshot snapshot = Snapshot.createSnapshot(buffer);

            testClassesQuery(snapshot);
            testAllClassesQuery(snapshot);
            testFindInstancesOf(snapshot);
            testFindAllInstancesOf(snapshot);

            System.out.println("Memory stats: free=" + Runtime.getRuntime().freeMemory()
                    + " / total=" + Runtime.getRuntime().totalMemory());
            System.out.println("Time: " + (System.nanoTime() - start) / 1000000 + "ms");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void testClassesQuery(Snapshot snapshot) {
        String[] x = new String[]{
                "char[",
                "javax.",
                "org.xml.sax"
        };

        Map<String, Set<ClassObj>> someClasses = Queries.classes(snapshot, x);

        for (String thePackage : someClasses.keySet()) {
            System.out.println("------------------- " + thePackage);

            Set<ClassObj> classes = someClasses.get(thePackage);

            for (ClassObj theClass : classes) {
                System.out.println("     " + theClass.mClassName);
            }
        }
    }

    private static void testAllClassesQuery(Snapshot snapshot) {
        Map<String, Set<ClassObj>> allClasses = Queries.allClasses(snapshot);

        for (String thePackage : allClasses.keySet()) {
            System.out.println("------------------- " + thePackage);

            Set<ClassObj> classes = allClasses.get(thePackage);

            for (ClassObj theClass : classes) {
                System.out.println("     " + theClass.mClassName);
            }
        }
    }

    private static void testFindInstancesOf(Snapshot snapshot) {
        Instance[] instances = Queries.instancesOf(snapshot, "java.lang.String");

        System.out.println("There are " + instances.length + " Strings.");
    }

    private static void testFindAllInstancesOf(Snapshot snapshot) {
        Instance[] instances = Queries.allInstancesOf(snapshot,
                "android.graphics.drawable.Drawable");

        System.out.println("There are " + instances.length
                + " instances of Drawables and its subclasses.");
    }
}
