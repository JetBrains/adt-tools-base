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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.common.io.ByteStreams;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
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

    private ClassLoader loadAndPatch(String... classes) throws Exception {
        Map<String, byte[]> original = new HashMap<String, byte[]>();
        Map<String, byte[]> enhanced = new HashMap<String, byte[]>();
        for (String clazz : classes) {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(
                    clazz.replace(".", "/") + ".class");
            byte[] bytes = ByteStreams.toByteArray(inputStream);
            original.put(clazz, bytes);

            ClassReader classReader = new ClassReader(bytes);
            ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_FRAMES);
            IncrementalSupportVisitor visitor = new IncrementalSupportVisitor(classWriter);
            classReader.accept(visitor, ClassReader.EXPAND_FRAMES);
            enhanced.put(clazz, classWriter.toByteArray());
         //   traceClass(classWriter.toByteArray());

        }
        ClassLoader cl = prepareClassLoader(this.getClass().getClassLoader().getParent(), enhanced);

        Map<String, byte[]> change = new HashMap<String, byte[]>();
        for (String clazz : classes) {
            ClassReader classReader = new ClassReader(original.get(clazz));
            ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_FRAMES);
            IncrementalChangeVisitor incrementalChangeVisitor = new IncrementalChangeVisitor(
                    classWriter);
            classReader.accept(incrementalChangeVisitor, ClassReader.EXPAND_FRAMES);
            change.put(clazz + "$override", classWriter.toByteArray());
        //    traceClass(classWriter.toByteArray());

        }
        ClassLoader secondaryClassLoader = prepareClassLoader(cl, change);


        for (String clazz : classes) {
            Class<?> enhancedClass = secondaryClassLoader.loadClass(clazz + "$override");
            patchClass(cl, clazz, enhancedClass);
        }

        return cl;
    }

    @Test
    public void superTest() throws Exception {
        ClassLoader cl = loadAndPatch(
                "com.android.build.gradle.internal.incremental.BaseClass",
                "com.android.build.gradle.internal.incremental.ExtendedClass");
        Class<?> aClass = cl
                .loadClass("com.android.build.gradle.internal.incremental.ExtendedClass");
        Object nonEnhancedInstance = aClass.newInstance();
        Method method = nonEnhancedInstance.getClass().getMethod("methodA");
        assertEquals(42, method.invoke(nonEnhancedInstance));
    }

    @Test
    public void perpareForIncrementalSupportTest() throws Exception {
        ClassLoader cl = loadAndPatch("com.android.build.gradle.internal.incremental.SimpleMethodDispatch");
        Class<?> aClass = cl
                .loadClass("com.android.build.gradle.internal.incremental.SimpleMethodDispatch");
        Object nonEnhancedInstance = aClass.newInstance();
        Method getStringValue = nonEnhancedInstance.getClass()
                .getMethod("getIntValue", Integer.TYPE);
        System.out.println(getStringValue.invoke(nonEnhancedInstance, 143));
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

    private void patchClass(ClassLoader loadingLoader, String name, Class patchedClass) throws Exception {
        Class<?> supportRuntimeClass = loadingLoader.loadClass(name);
        Field field = supportRuntimeClass.getField("$change");
        Object change = patchedClass.newInstance();
        field.set(null, change);
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
