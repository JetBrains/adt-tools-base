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

import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.incremental.IncrementalChangeVisitor;
import com.android.tools.fd.runtime.AndroidInstantRuntime;
import com.android.utils.FileUtils;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.logging.Logger;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.TraceClassVisitor;

public class ClassEnhancement implements TestRule {

    @NonNull
    private final File mInstrumentedPatchesFolder;

    @NonNull
    private final File mBaseCompileOutputFolder;


    private Map<String, File> mInstrumentedPatchFolders;

    private Map<String, ClassLoader> mEnhancedClassLoaders;

    private String currentPatchState = null;

    private final boolean tracing;

    public ClassEnhancement() {
        this(true);
    }

    public ClassEnhancement(boolean tracing) {
        this.tracing = tracing;
        File classes = new File(ClassEnhancement.class.getResource("/").getFile()).getParentFile();
        File incrementalTestClasses = new File(classes, "incremental-test");
        mInstrumentedPatchesFolder = new File(incrementalTestClasses, "instrumentedPatches");
        mBaseCompileOutputFolder = new File(incrementalTestClasses, "base");
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

            final File outputFolder =
                    patch == null ? mBaseCompileOutputFolder : mInstrumentedPatchFolders.get(patch);
            Iterable<File> files =
                    Files.fileTreeTraverser()
                            .preOrderTraversal(outputFolder)
                            .filter(Files.isFile());
            Iterable<String> classNames = Iterables.transform(files, new Function<File, String>() {
                @Override
                public String apply(File file) {
                    String relativePath = FileUtils.relativePath(file, outputFolder);
                    return relativePath.substring(0, relativePath.length() - 6 /*.class */)
                            .replace(File.separatorChar, '.');
                }
            });

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
                AndroidInstantRuntime.setLogger(Logger.getLogger(description.getClassName()));
                mInstrumentedPatchFolders = getCompileFolders(mInstrumentedPatchesFolder);

                final URL[] classLoaderUrls = getClassLoaderUrls();
                final ClassLoader mainClassLoader = this.getClass().getClassLoader();

                mEnhancedClassLoaders = setUpEnhancedClassLoaders(
                        classLoaderUrls, mainClassLoader, mInstrumentedPatchFolders, tracing);

                base.evaluate();

            }
        };
    }

    private static Map<String, File> getCompileFolders(File folder) {
        File[] iterations = folder.listFiles();

        if (iterations == null) {
            throw new AssertionError("Resource base must be a directory.");
        }
        ImmutableMap.Builder<String, File> compileOutputFolders = ImmutableMap.builder();

        for (File f : iterations) {
            compileOutputFolders.put(f.getName(), f);
        }

        return compileOutputFolders.build();
    }

    private static Map<String, ClassLoader> setUpEnhancedClassLoaders(
            final URL[] classLoaderUrls,
            final ClassLoader mainClassLoader,
            final Map<String, File> instrumentedPatches,
            final boolean tracing) {
        return Maps.transformValues(instrumentedPatches, new Function<File, ClassLoader>() {
            @Override
            public ClassLoader apply(File instrumentedPatchFolder) {
                return new IncrementalChangeClassLoader(
                        classLoaderUrls, mainClassLoader, instrumentedPatchFolder, tracing);
            }
        });
    }


    private void patchClass(@NonNull String name, @Nullable String patch)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {

        Class<?> originalEnhancedClass = getClass().getClassLoader().loadClass(name);
        if (originalEnhancedClass.isInterface()) {
            // we don't patch interfaces.
            return;
        }
        Field newImplementationField = null;
        try {
            newImplementationField = originalEnhancedClass.getField("$change");
        } catch (NoSuchFieldException e) {
            // the original class does not contain the $change field which mean that InstantRun
            // was disabled for it, we should ignore and not try to patch it.
            return;
        }
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

    private static URL[] getClassLoaderUrls() {
        URL resource = ClassEnhancement.class.getClassLoader().getResource(
                "com/android/tools/fd/runtime/AndroidInstantRuntime.class");

        assertNotNull(resource);
        String runtimeURL = resource.toString().substring(0, resource.toString().length() -
                "com/android/tools/fd/runtime/AndroidInstantRuntime.class"
                        .length());

        resource = ClassEnhancement.class.getClassLoader().getResource(
                "org/objectweb/asm/Type.class");
        Preconditions.checkNotNull(resource);
        String asmURL = resource.toString().substring(0, resource.toString().length() -
                "org/objectweb/asm/Type.class".length());

        try {
            return new URL[]{new URL(runtimeURL), new URL(asmURL)};
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private static class IncrementalChangeClassLoader extends URLClassLoader {

        private final File mInstrumentedPatchFolder;
        private final boolean tracing;

        public IncrementalChangeClassLoader(
                URL[] urls, ClassLoader parent, File instrumentedPatchFolder, boolean tracing) {
            super(urls, parent);
            this.tracing = tracing;
            mInstrumentedPatchFolder = instrumentedPatchFolder;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {

            if (!name.endsWith("$override")) {
                return super.findClass(name);
            }

            File compiledFile =
                    new File(mInstrumentedPatchFolder, name.replace(".", "/") + ".class");

            if (!compiledFile.exists()) {
                return super.findClass(name);
            }

            byte[] instrumentedClassBytes;
            try {
                instrumentedClassBytes = Files.toByteArray(compiledFile);
            } catch (IOException e) {
                throw new ClassNotFoundException(Throwables.getStackTraceAsString(e));
            }

            return defineClass(name, instrumentedClassBytes, 0, instrumentedClassBytes.length);
        }
    }

    public Class loadPatchForClass(String patchName, Class originalClass)
            throws ClassNotFoundException {
        ClassLoader classLoader = mEnhancedClassLoaders.get(patchName);
        if (classLoader != null) {
            return classLoader.loadClass(originalClass.getName()
                    + IncrementalChangeVisitor.OVERRIDE_SUFFIX);
        }
        throw new IllegalArgumentException("Unknown patch name " + patchName);
    }

    @SuppressWarnings("unused") // Helpful for debugging.
    public static String traceClass(byte[] bytes) {
        ClassReader classReader = new ClassReader(bytes, 0, bytes.length);
        StringWriter sw = new StringWriter();
        TraceClassVisitor traceClassVisitor = new TraceClassVisitor(new PrintWriter(sw));
        classReader.accept(traceClassVisitor, 0);
        return sw.toString();
    }

    @SuppressWarnings("unused") // Helpful for debugging.
    public static String traceClass(ClassLoader classLoader, String classNameAsResource) throws IOException {
        InputStream inputStream = classLoader.getResourceAsStream(classNameAsResource);
        assertNotNull(inputStream);
        try {
            ClassReader classReader = new ClassReader(inputStream);
            StringWriter sw = new StringWriter();
            TraceClassVisitor traceClassVisitor = new TraceClassVisitor(new PrintWriter(sw));
            classReader.accept(traceClassVisitor, 0);
            return sw.toString();
        } finally {
            inputStream.close();
        }
    }

    /** Gets the file containing the base version of the class with the specified class name. */
    @NonNull
    public static File getBaseClassFile(@NonNull String className) {
        File classes = new File(ClassEnhancement.class.getResource("/").getFile()).getParentFile();
        File baseFolder = FileUtils.join(classes, "incremental-test", "base");

        return new File(baseFolder, className.replace('.', File.separatorChar) + ".class");
    }
}
