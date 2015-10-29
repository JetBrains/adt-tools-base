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

package com.android.builder.internal.compiler;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.google.common.collect.Maps;

import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Map;

/**
 * Wrapper to access dx.jar through reflection.
 * <p/>Since there is no proper api to call the method in the dex library, this wrapper is going
 * to access it through reflection.
 */
public class DexWrapper {

    private static final String DEX_MAIN = "com.android.dx.command.dexer.Main";
    private static final String DEX_CONSOLE = "com.android.dx.command.DxConsole";
    private static final String DEX_ARGS = "com.android.dx.command.dexer.Main$Arguments";

    private static final String MAIN_RUN = "run";

    private Method mRunMethod;

    private Constructor<?> mArgConstructor;
    private Field mArgOutName;
    private Field mArgVerbose;
    private Field mArgJarOutput;
    private Field mArgFileNames;

    private Field mConsoleOut;
    private Field mConsoleErr;

    private static final Map<File, DexWrapper> DX_MAP = Maps.newHashMap();

    public static synchronized DexWrapper getWrapper(@NonNull File dxJarFile) {
        DexWrapper wrapper = DX_MAP.get(dxJarFile);
        if (wrapper == null) {
            wrapper = new DexWrapper();
            wrapper.loadDex(dxJarFile);

            DX_MAP.put(dxJarFile, wrapper);
        }

        return wrapper;
    }

    public static synchronized void clear() {
        for (DexWrapper wrapper : DX_MAP.values()) {
            wrapper.unload();
        }

        DX_MAP.clear();
        System.gc();
    }

    private DexWrapper() {

    }

    /**
     * Loads the dex library from a file path.
     *
     * The loaded library can be used via
     * {@link #run(File, Collection, boolean, PrintStream, PrintStream)}.
     *
     * @param dxJarFile the location of the dx.jar file.
     */
    private void loadDex(@NonNull File dxJarFile) {
        try {
            if (!dxJarFile.isFile()) {
                throw new RuntimeException("dx.jar not found at : " + dxJarFile);
            }
            URL url = dxJarFile.toURI().toURL();

            @SuppressWarnings("resource")
            URLClassLoader loader = new URLClassLoader(new URL[]{url},
                    DexWrapper.class.getClassLoader());

            // get the classes.
            Class<?> mainClass = loader.loadClass(DEX_MAIN);
            Class<?> consoleClass = loader.loadClass(DEX_CONSOLE);
            Class<?> argClass = loader.loadClass(DEX_ARGS);

            // now get the fields/methods we need
            mRunMethod = mainClass.getMethod(MAIN_RUN, argClass);

            mArgConstructor = argClass.getConstructor();
            mArgOutName = argClass.getField("outName");
            mArgJarOutput = argClass.getField("jarOutput");
            mArgFileNames = argClass.getField("fileNames");
            mArgVerbose = argClass.getField("verbose");

            mConsoleOut = consoleClass.getField("out");
            mConsoleErr = consoleClass.getField("err");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Removes any reference to the dex library.
     * <p/>
     * {@link #loadDex(File)} must be called on the wrapper
     * before {@link #run(File, Collection, boolean, PrintStream, PrintStream)} can be used again.
     */
    private void unload() {
        mRunMethod = null;
        mArgConstructor = null;
        mArgOutName = null;
        mArgJarOutput = null;
        mArgFileNames = null;
        mArgVerbose = null;
        mConsoleOut = null;
        mConsoleErr = null;
    }

    /**
     * Runs the dex command.
     * The wrapper must have been initialized via {@link #loadDex(File)} first.
     *
     * @param outputFile the output file.
     * @param inputFiles the input files.
     * @param verbose verbose mode.
     * @param outStream the stdout console
     * @param errStream the stderr console
     * @return the integer return code of com.android.dx.command.dexer.Main.run()
     */
    public synchronized int run(
            @NonNull File outputFile,
            @NonNull Collection<File> inputFiles,
                     boolean verbose,
            @NonNull PrintStream outStream,
            @NonNull PrintStream errStream) {

        checkNotNull(mRunMethod, "Wrapper was not properly loaded");
        checkNotNull(mArgConstructor, "Wrapper was not properly loaded");
        assert mArgOutName != null;
        assert mArgJarOutput != null;
        assert mArgFileNames != null;
        assert mArgVerbose != null;
        assert mConsoleOut != null;
        assert mConsoleErr != null;

        try {
            // set the stream
            mConsoleErr.set(null /* obj: static field */, errStream);
            mConsoleOut.set(null /* obj: static field */, outStream);

            // create the Arguments object.
            Object args = mArgConstructor.newInstance();

            if (outputFile.isDirectory()) {
                mArgOutName.set(args, new File(outputFile, "classes.dex").getPath());
                mArgJarOutput.set(args, false);
            } else {
                String outputFileAbsolutePath = outputFile.getAbsolutePath();
                mArgOutName.set(args, outputFileAbsolutePath);
                mArgJarOutput.set(args, outputFileAbsolutePath.endsWith(SdkConstants.DOT_JAR));
            }

            // inputs
            String[] inputPaths = new String[inputFiles.size()];
            int i = 0;
            for (File inputFile : inputFiles ) {
                inputPaths[i++] = inputFile.getAbsolutePath();
            }
            mArgFileNames.set(args, inputPaths);

            mArgVerbose.set(args, verbose);

            // call the run method
            Object res = mRunMethod.invoke(null /* obj: static method */, args);

            if (res instanceof Integer) {
                return (Integer) res;
            }

            return -1;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
