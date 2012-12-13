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



import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Map;
import java.util.TreeMap;

/**
 * Main class for command line command to convert the existing API XML/TXT files into diff-based
 * simple text files.
 *
 */
public class Main {

    /**
     * @param args
     */
    public static void main(String[] args) {
        if (args.length != 2) {
            printUsage();
        }

        AndroidJarReader reader = new AndroidJarReader(args[0]);
        Map<String, ApiClass> classes = reader.getClasses();
        createApiFile(new File(args[1]), classes);
    }

    private static void printUsage() {
        System.err.println("Generates a single API file from the content of an SDK.\n");
        System.err.println("Usage\n");
        System.err.println("\tApiCheck SDKFOLDER OUTFILE\n");
        System.exit(1);
    }

    /**
     * Creates the simplified diff-based API level.
     * @param outFolder the out folder.
     * @param classes
     */
    private static void createApiFile(File outFile, Map<String, ApiClass> classes) {

        PrintStream ps = null;
        try {
            ps = new PrintStream(outFile);
            ps.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
            ps.println("<api version=\"1\">");
            TreeMap<String, ApiClass> map = new TreeMap<String, ApiClass>(classes);
            for (ApiClass theClass : map.values()) {
                (theClass).print(ps);
            }
            ps.println("</api>");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (ps != null) {
                ps.close();
            }
        }
    }
}
