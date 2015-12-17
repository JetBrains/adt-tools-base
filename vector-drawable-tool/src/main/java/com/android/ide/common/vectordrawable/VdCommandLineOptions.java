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

package com.android.ide.common.vectordrawable;

import java.io.File;
import java.util.Arrays;

public class VdCommandLineOptions {
    // Support a command line tool to convert or show the VectorDrawable.
    // Below are the options or information for this tool.

    private static final String OPTION_CONVERT = "-c";

    private static final String OPTION_DISPLAY = "-d";

    private static final String OPTION_IN = "-in";

    private static final String OPTION_OUT = "-out";

    private static final String OPTION_FORCE_WIDTH_DP = "-widthDp";

    private static final String OPTION_FORCE_HEIGHT_DP = "-heightDp";

    private static final String OPTION_ADD_HEADER = "-addHeader";

    public static final String COMMAND_LINE_OPTION = "Converts SVG files to VectorDrawable XML files.\n"
            + "Displays VectorDrawables.\n"
            + "Usage: [-c] [-d] [-in <file or directory>] [-out <directory>] [-widthDp <size>] "
            + "[-heightDp <size>] [-addHeader]\n"
            + "Options:\n"
            + "  -in <file or directory>:  If -c is specified, Converts the given .svg file \n"
            + "                            to VectorDrawable XML, or if a directory is specified,\n"
            + "                            all .svg files in the given directory. Otherwise, if -d\n"
            + "                            is specified, displays the given VectorDrawable XML file\n"
            + "                            or all VectorDrawables in the given directory.\n"
            + "  -out <directory>          If specified, write converted files out to the given\n"
            + "                            directory, which must exist. If not specified the\n"
            + "                            converted files will be written to the directory\n"
            + "                            containing the input files.\n"
            + "  -c                        If present, SVG files are converted to VectorDrawable XML\n"
            + "                            and written out.\n"
            + "  -d                        Displays the given VectorDrawable(s), or if -c is\n"
            + "                            specified the results of the conversion.\n"
            + "  -widthDp <size>           Force the width to be <size> dp, <size> must be integer\n"
            + "  -heightDp <size>          Force the height to be <size> dp, <size> must be integer\n"
            + "  -addHeader                Add AOSP header to the top of the generated XML file\n"
            + "Examples:                   \n"
            + "  1) Convert SVG files from <directory> into XML files at the same directory"
            + " and visualize the XML file results:\n"
            + "  vd-tool -c -d -in <directory> \n"
            + "  2) Convert SVG file and visualize the XML file results:\n"
            + "  vd-tool -c -d -in file.svg \n"
            + "  3) Display VectorDrawable's XML files from <directory>:\n"
            + "  vd-tool -d -in <directory> \n"
            ;

    private boolean mConvertSvg;

    private File[] mInputFiles;

    private File mOutputDir;

    private boolean mDisplayXml;

    public int getForceWidth() {
        return mForceWidth;
    }

    public int getForceHeight() {
        return mForceHeight;
    }

    public boolean isAddHeader() {
        return mAddHeader;
    }

    private int mForceWidth = -1;

    private int mForceHeight = -1;

    private boolean mAddHeader = false;

    public boolean getDisplayXml() {
        return mDisplayXml;
    }

    public boolean getConvertSvg() {
        return mConvertSvg;
    }

    public File[] getInputFiles() {
        return mInputFiles;
    }

    public File getOutputDir() {
        return mOutputDir;
    }

    /**
     * Parse the command line options.
     *
     * @param args the incoming command line options
     * @return null if no critical error happens, otherwise the error message.
     */
    public String parse(String[] args) {
        File argIn = null;
        mOutputDir = null;
        mConvertSvg = false;
        mDisplayXml = false;

        // First parse the command line options.
        if (args != null && args.length > 0) {
            int index = 0;
            while (index < args.length) {
                String currentArg = args[index];
                if (OPTION_CONVERT.equalsIgnoreCase(currentArg)) {
                    System.out.println(OPTION_CONVERT + " parsed, so we will convert the SVG files");
                    mConvertSvg = true;
                } else if (OPTION_DISPLAY.equalsIgnoreCase(currentArg)) {
                    System.out.println(OPTION_DISPLAY + " parsed, so we will display the XML files");
                    mDisplayXml = true;
                } else if (OPTION_IN.equalsIgnoreCase(currentArg)) {
                    if ((index + 1) < args.length) {
                        argIn = new File(args[index + 1]);
                        System.out.println(OPTION_IN + " parsed " + argIn.getAbsolutePath());
                        index++;
                    }
                } else if (OPTION_OUT.equalsIgnoreCase(currentArg)) {
                    if ((index + 1) < args.length) {
                        mOutputDir = new File(args[index + 1]
                                .replaceFirst("^~", System.getProperty("user.home")));
                        System.out.println(OPTION_OUT + " parsed " + mOutputDir.getAbsolutePath());
                        index++;
                    }
                }  else if (OPTION_FORCE_WIDTH_DP.equalsIgnoreCase(currentArg)) {
                    if ((index + 1) < args.length) {
                        mForceWidth = Integer.parseInt(args[index + 1]);
                        System.out.println(OPTION_FORCE_WIDTH_DP + " parsed " + mForceWidth);
                        index++;
                    }
                }  else if (OPTION_FORCE_HEIGHT_DP.equalsIgnoreCase(currentArg)) {
                    if ((index + 1) < args.length) {
                        mForceHeight = Integer.parseInt(args[index + 1]);
                        System.out.println(OPTION_FORCE_HEIGHT_DP + " parsed " + mForceHeight);
                        index++;
                    }
                }  else if (OPTION_ADD_HEADER.equalsIgnoreCase(currentArg)) {
                    mAddHeader = true;
                    System.out.println(OPTION_ADD_HEADER + " parsed, add AOSP header to the XML file");
                } else {
                    return "ERROR: unrecognized option " + currentArg;
                }
                index++;
            }
        } else {
            return "ERROR: empty arguments";
        }

        if (!mConvertSvg && !mDisplayXml) {
            return "ERROR: either " + OPTION_CONVERT + " or " + OPTION_DISPLAY + " must be specified";
        }
        // Then we decide the input resources.
        mInputFiles = null;
        if (argIn != null) {
            if (argIn.isFile()) {
                mInputFiles = new File[1];
                mInputFiles[0] = argIn;
                if (mOutputDir == null && mConvertSvg) {
                    mOutputDir = argIn.getParentFile();
                }
            } else if (argIn.isDirectory()) {
                File parsedSVGDir = argIn;
                mInputFiles = parsedSVGDir.listFiles();
                // Sort the files by the name.
                Arrays.sort(mInputFiles);
                if (mOutputDir == null && mConvertSvg) {
                    mOutputDir = argIn;
                }
            }
        } else {
            return "ERROR: no input files argument";
        }

        if (mConvertSvg) {
            if (mOutputDir != null) {
                if (!mOutputDir.isDirectory()) {
                    return ("ERROR: Output directory " + mOutputDir.getAbsolutePath()
                            + " doesn't exist or isn't a valid directory");
                }
            } else {
                return "ERROR: no output directory specified";
            }
        }

        if (mInputFiles != null && mInputFiles.length == 0) {
            return "ERROR: There is no file to process in " + argIn.getName();
        }

        return null;
    }
}
