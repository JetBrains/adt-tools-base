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

package com.android.build.gradle.internal.incremental.fixture;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Environment;
import android.support.test.InstrumentationRegistry;
import android.support.v4.content.ContextCompat;

import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Collection;

import dalvik.system.DexClassLoader;

public class ClassEnhancement implements TestRule {

    private final File mResourceBase;

    private final File mBaseCompileOutputFolder;

    private Map<String, File> mCompileOutputFolders;

    private Map<String, ClassLoader> mEnhancedClassLoaders;

    private Map<String, List<String>> mEnhancedClasses;

    private Collection<String> mBaseClasses;

    private String currentPatchState = null;

    private final boolean tracing;

    public ClassEnhancement() {
        this(true);
    }

    public ClassEnhancement(boolean tracing) {
        this.tracing = tracing;
        mResourceBase = new File("incremental-test-classes-dex");
        mBaseCompileOutputFolder = null;
    }

    public void reset()
            throws ClassNotFoundException, InstantiationException, IllegalAccessException,
            NoSuchFieldException {
        applyPatch(null);
    }

    public void applyPatch(@Nullable String patch)
            throws ClassNotFoundException, NoSuchFieldException, InstantiationException,
            IllegalAccessException {

        // if requested level is null, always reset no matter what state we think we are in since
        // we share the same class loader among all ClassEnhancement instances.
        if (patch == null || !Objects.equal(patch, currentPatchState)) {
            Iterable<String> classNames = patch == null ? mBaseClasses
                    : mEnhancedClasses.get(patch);

            for (String changedClassName : classNames) {
                if (changedClassName.endsWith("$override")) {
                    changedClassName = changedClassName.substring(0, changedClassName.length() - 9);
                }
                patchClass(changedClassName, patch);
            }
            currentPatchState = patch;
        }
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {

                mCompileOutputFolders = getCompileFolders(mResourceBase);

                final ClassLoader mainClassLoader = this.getClass().getClassLoader();
                mEnhancedClassLoaders = setUpEnhancedClassLoaders(
                        mainClassLoader, mCompileOutputFolders, tracing);
                mEnhancedClasses = findEnhancedClasses(mCompileOutputFolders);
                ImmutableSet.Builder<String> baseClassesBuilder = ImmutableSet.builder();
                for (List<String> classNames: mEnhancedClasses.values()) {
                    baseClassesBuilder.addAll(classNames);
                }
                mBaseClasses = baseClassesBuilder.build();
                base.evaluate();

            }
        };
    }

    private static Map<String, File> getCompileFolders(File folder) throws IOException {
        Context context = InstrumentationRegistry.getContext();
        String[] names = context.getAssets().list(folder.getPath());
        ImmutableMap.Builder<String, File> builder = ImmutableMap.builder();

        for (String name : names) {
            builder.put(name, new File(folder, name));
        }

        return builder.build();
    }

    private static Map<String, ClassLoader> setUpEnhancedClassLoaders(
            final ClassLoader mainClassLoader,
            final Map<String, File> compileOutputFolders,
            final boolean tracing) {
        return Maps.transformEntries(compileOutputFolders,
                new Maps.EntryTransformer<String, File, ClassLoader>() {
                    @Override
                    public ClassLoader transformEntry(@Nullable String patch,
                            @Nullable File compileOutputFolder) {
                        Context context = InstrumentationRegistry.getContext();
                        File optimizedDir = new ContextCompat().getCodeCacheDir(context);

                        try {
                            InputStream is = context.getAssets()
                                    .open(compileOutputFolder.getPath() + "/classes.dex");
                            File output;
                            try {
                                File patchDir = new File(
                                        context.getDir("patches", Context.MODE_PRIVATE), patch);
                                patchDir.mkdir();
                                output = new File(patchDir, patch + ".dex");

                                Files.asByteSink(output).writeFrom(is);
                            } finally {
                                is.close();
                            }

                            return new DexClassLoader(output.getAbsolutePath(),
                                    optimizedDir.getAbsolutePath(),
                                    null,
                                    ClassEnhancement.class.getClassLoader());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
    }

    private static Map<String, List<String>> findEnhancedClasses(
            final Map<String, File> compileOutputFolders) {
        final Context context = InstrumentationRegistry.getContext();
        return Maps.transformEntries(compileOutputFolders,
                new Maps.EntryTransformer<String, File, List<String>>() {
                    @Override
                    public List<String> transformEntry(@Nullable String patch,
                            @Nullable File outputFolder) {

                        try {
                            InputStream classesIS = context.getAssets()
                                    .open(outputFolder.getPath() + "/classes.txt");
                            try {
                                Iterable<String> classPaths = Splitter.on('\n')
                                        .omitEmptyStrings().split(
                                                CharStreams.toString(
                                                        new InputStreamReader(classesIS,
                                                                Charsets.UTF_8)));
                                return Lists.newArrayList(Iterables
                                        .transform(classPaths, new Function<String, String>() {
                                            @Override
                                            public String apply(@Nullable String relativePath) {
                                                return relativePath
                                                        .substring(0, relativePath.length() - 6 /*.class */)
                                                        .replace('/', '.');
                                            }
                                        }));
                            } finally {
                                classesIS.close();
                            }
                        } catch (IOException e) {
                            throw new IllegalArgumentException(
                                    "Could not open patch classes.dex", e);
                        }
                    }
                });
    }


    private void patchClass(@NonNull String name, @Nullable String patch)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException,
            NoSuchFieldException {

        Class<?> originalEnhancedClass = getClass().getClassLoader().loadClass(name);
        if (originalEnhancedClass.isInterface()) {
            // we don't patch interfaces.
            return;
        }
        Field newImplementationField = originalEnhancedClass.getField("$change");
        // class might not be accessible from there
        newImplementationField.setAccessible(true);

        if (patch == null) {
            // Revert to original implementation.
            newImplementationField.set(null, null);
            return;
        }

        Object change = mEnhancedClassLoaders.get(patch)
                .loadClass(name + "$override").newInstance();

        newImplementationField.set(null, change);
    }
}