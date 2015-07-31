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

package com.android.build.gradle.internal.incremental;

import static org.junit.Assert.assertNotNull;

import com.google.common.collect.ImmutableMap;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;

/**
 * Created by jedo on 7/23/15.
 */
public class ClassEnhancementTest {

    @Test
    public void traceSimpleMethod() throws IOException {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(
                "com/android/build/gradle/internal/incremental/SimpleMethodDispatchControl.class");
        assertNotNull(inputStream);
        try {
            ClassReader classReader = new ClassReader(inputStream);
            StringWriter sw = new StringWriter();
            TraceClassVisitor traceClassVisitor = new TraceClassVisitor(new PrintWriter(sw));
            classReader.accept(traceClassVisitor, 0);
            System.out.println(sw.toString());
        } finally {
            inputStream.close();
        }
    }


    @Test
    public void perpareForIncrementalSupportTest() throws IOException, ClassNotFoundException, IllegalAccessException,
            InstantiationException, NoSuchMethodException, InvocationTargetException,
            NoSuchFieldException {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(
                "com/android/build/gradle/internal/incremental/SimpleMethodDispatch.class");
        inputStream.mark(0);
        assertNotNull(inputStream);
        try {
            ClassReader classReader = new ClassReader(inputStream);
            ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_FRAMES);
            IncrementalSupportVisitor visitor = new IncrementalSupportVisitor(classWriter);
            classReader.accept(visitor, 0);

            ClassLoader cl = prepareClassLoader(
                    this.getClass().getClassLoader().getParent(),
                    ImmutableMap.<String, byte[]>builder()
                    .put("com.android.build.gradle.internal.incremental.SimpleMethodDispatch",
                            classWriter.toByteArray())
                    .build());

            Class<?> originalImplementation = cl.loadClass(
                    "com.android.build.gradle.internal.incremental.SimpleMethodDispatch");

            traceClass(classWriter.toByteArray());

            Object nonEnhancedInstance = originalImplementation.newInstance();

            // now generate the modified bits.
            inputStream.reset();
            classReader = new ClassReader(inputStream);
            classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_FRAMES);
            IncrementalChangeVisitor incrementalChangeVisitor = new IncrementalChangeVisitor(classWriter);
            classReader.accept(incrementalChangeVisitor, 0);

            ClassLoader secondaryClassLoader = prepareClassLoader(
                    cl,
                    ImmutableMap.<String, byte[]>builder()
                    .put("com.android.build.gradle.internal.incremental.SimpleMethodDispatchISSupport",
                            classWriter.toByteArray())
                    .build());

            traceClass(classWriter.toByteArray());

            Class<?> enhancedClass = secondaryClassLoader.loadClass(
                    "com.android.build.gradle.internal.incremental.SimpleMethodDispatchISSupport");

            addPatchedClass(cl,
                    "com/android/build/gradle/internal/incremental/SimpleMethodDispatch",
                    enhancedClass);

            traceClass(classWriter.toByteArray());
            Method getStringValue = nonEnhancedInstance.getClass()
                    .getMethod("getIntValue", Integer.TYPE);
            System.out.println(getStringValue.invoke(nonEnhancedInstance, 143));

        } finally {
            inputStream.close();
        }
    }

    private ClassLoader prepareClassLoader(ClassLoader parentClassLoader, Map<String, byte[]> classDefinitions)
            throws MalformedURLException {
        URL resource = getClass().getClassLoader().getResource(
                "com/android/build/gradle/internal/incremental/IncrementalSupportRuntime.class");

        assertNotNull(resource);
        String runtimeURL = resource.toString().substring(0, resource.toString().length() -
                "com/android/build/gradle/internal/incremental/IncrementalSupportRuntime.class"
                        .length());

        resource = getClass().getClassLoader().getResource(
                "org/objectweb/asm/Type.class");
        String asmURL = resource.toString().substring(0, resource.toString().length() -
                "org/objectweb/asm/Type.class".length());

        URL[] urls = new URL[2];
        urls[0] = new URL(runtimeURL);
        urls[1] = new URL(asmURL);

        return new ByteClassLoader(urls,
                parentClassLoader,
                classDefinitions);
    }

    private void addPatchedClass(ClassLoader loadingLoader, String name, Class patchedClass)
            throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException,
            NoSuchMethodException, InvocationTargetException {

        Class<?> supportRuntimeClass = loadingLoader.loadClass(
                "com.android.build.gradle.internal.incremental.IncrementalSupportRuntime");
        Object supportRuntime = supportRuntimeClass.getField("INSTANCE").get(null);
        Method addPatchedClass = supportRuntimeClass
                .getMethod("addPatchedClass", String.class, Class.class);
        assertNotNull(addPatchedClass);
        addPatchedClass.invoke(supportRuntime, name, patchedClass);
    }


    private void traceClass(byte[] bytes) {
        ClassReader classReader = new ClassReader(bytes, 0, bytes.length);
        StringWriter sw = new StringWriter();
        TraceClassVisitor traceClassVisitor = new TraceClassVisitor(new PrintWriter(sw));
        classReader.accept(traceClassVisitor, 0);
        System.out.println(sw.toString());
    }

    private void traceClass(ClassLoader classLoader, String classNameAsResource) throws IOException {
        InputStream inputStream = classLoader.getResourceAsStream(classNameAsResource);
        assertNotNull(inputStream);
        try {
            ClassReader classReader = new ClassReader(inputStream);
            StringWriter sw = new StringWriter();
            TraceClassVisitor traceClassVisitor = new TraceClassVisitor(new PrintWriter(sw));
            classReader.accept(traceClassVisitor, 0);
            System.out.println(sw.toString());
        } finally {
            inputStream.close();
        }
    }

    public static class ByteClassLoader extends URLClassLoader {
        private final Map<String, byte[]> extraClassDefs;

        public ByteClassLoader(URL[] urls, ClassLoader parent, Map<String, byte[]> extraClassDefs) {
            super(urls, parent);
            this.extraClassDefs = extraClassDefs;
        }

        @Override
        protected Class<?> findClass(final String name) throws ClassNotFoundException {
            byte[] classBytes = this.extraClassDefs.get(name);
            if (classBytes != null) {
                return defineClass(name, classBytes, 0, classBytes.length);
            }
            return super.findClass(name);
        }

    }
}
