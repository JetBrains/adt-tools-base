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
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Main class for command line command to convert the existing API XML/TXT files into diff-based
 * simple text files.
 */
public class Main {
    public static void main(String[] args) {

        boolean error = false;
        int minApi = 1;
        int currentApi = -1;
        String currentCodenName = null;
        File currentJar = null;
        List<String> patterns = new ArrayList<String>();
        String outPath = null;

        for (int i = 0; i < args.length && !error; i++) {
            String arg = args[i];

            if (arg.equals("--pattern")) {
                i++;
                if (i < args.length) {
                    patterns.add(args[i]);
                } else {
                    System.err.println("Missing argument after " + arg);
                    error = true;
                }
            } else if (arg.equals("--current-version")) {
                i++;
                if (i < args.length) {
                    currentApi = Integer.parseInt(args[i]);
                    if (currentApi <= 22) {
                        System.err.println("Suspicious currentApi=" + currentApi + ", expected at least 23");
                        error = true;
                    }
                } else {
                    System.err.println("Missing number >= 1 after " + arg);
                    error = true;
                }
            } else if (arg.equals("--current-codename")) {
                i++;
                if (i < args.length) {
                    currentCodenName = args[i];
                } else {
                    System.err.println("Missing codename after " + arg);
                    error = true;
                }
            } else if (arg.equals("--current-jar")) {
                i++;
                if (i < args.length) {
                    if (currentJar != null) {
                        System.err.println("--current-jar should only be specified once");
                        error = true;
                    }
                    String path = args[i];
                    currentJar = new File(path);
                } else {
                    System.err.println("Missing argument after " + arg);
                    error = true;
                }
            } else if (arg.equals("--min-api")) {
                i++;
                if (i < args.length) {
                    minApi = Integer.parseInt(args[i]);
                } else {
                    System.err.println("Missing number >= 1 after " + arg);
                    error = true;
                }
            } else if (arg.length() >= 2 && arg.substring(0, 2).equals("--")) {
                System.err.println("Unknown argument: " + arg);
                error = true;

            } else if (outPath == null) {
                outPath = arg;

            } else if (new File(arg).isDirectory()) {
                String pattern = arg;
                if (!pattern.endsWith(File.separator)) {
                    pattern += File.separator;
                }
                pattern += "platforms" + File.separator + "android-%" + File.separator + "android.jar";
                patterns.add(pattern);

            } else {
                System.err.println("Unknown argument: " + arg);
                error = true;
            }
        }

        if (!error && outPath == null) {
            System.err.println("Missing out file path");
            error = true;
        }

        if (!error && patterns.isEmpty()) {
            System.err.println("Missing SdkFolder or --pattern.");
            error = true;
        }

        if (currentJar != null && currentApi == -1 || currentJar == null && currentApi != -1) {
            System.err.println("You must specify both --current-jar and --current-version (or neither one)");
            error = true;
        }

        // The SDK version number
        if (currentCodenName != null && !"REL".equals(currentCodenName)) {
            currentApi++;
        }

        if (error) {
            printUsage();
            System.exit(1);
        }

        AndroidJarReader reader = new AndroidJarReader(patterns, minApi, currentJar, currentApi);
        Map<String, ApiClass> classes = reader.getClasses();
        if (!createApiFile(new File(outPath), classes)) {
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.err.println("\nGenerates a single API file from the content of an SDK.");
        System.err.println("Usage:");
        System.err.println("\tApiCheck [--min-api=1] OutFile [SdkFolder | --pattern sdk/%/android.jar]+");
        System.err.println("Options:");
        System.err.println("--min-api <int> : The first API level to consider (>=1).");
        System.err.println("--pattern <pattern>: Path pattern to find per-API android.jar files, where\n" +
                           "            '%' is replaced by the API level.");
        System.err.println("--current-jar <path>: Path pattern to find the current android.jar");
        System.err.println("--current-version <int>: The API level for the current API");
        System.err.println("--current-codename <name>: REL, if a release, or codename for previews");
        System.err.println("SdkFolder: if given, this adds the pattern\n" +
                           "           '$SdkFolder/platforms/android-%/android.jar'");
        System.err.println("If multiple --pattern are specified, they are tried in the order given.\n");
    }

    /**
     * Creates the simplified diff-based API level.
     * @param outFile the output file
     * @param classes the classes to write
     */
    private static boolean createApiFile(File outFile, Map<String, ApiClass> classes) {

        PrintStream ps = null;
        try {
            File parentFile = outFile.getParentFile();
            if (!parentFile.exists()) {
                boolean ok = parentFile.mkdirs();
                if (!ok) {
                    System.err.println("Could not create directory " + parentFile);
                    return false;
                }
            }
            ps = new PrintStream(outFile, "UTF-8");
            ps.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
            ps.println("<api version=\"2\">");
            TreeMap<String, ApiClass> map = new TreeMap<String, ApiClass>(classes);
            for (ApiClass theClass : map.values()) {
                (theClass).print(ps);
            }
            ps.println("</api>");
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (ps != null) {
                ps.close();
            }
        }

        return true;
    }
}
