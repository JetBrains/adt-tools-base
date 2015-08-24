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

import com.android.SdkConstants;
import com.android.utils.Pair;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads all the android.jar files found in an SDK and generate a map of {@link ApiClass}.
 *
 */
public class AndroidJarReader {
    private final int mMinApi;
    private final int mCurrentApi;
    private final File mCurrentJar;
    private final List<String> mPatterns;

    public AndroidJarReader(List<String> patterns, int minApi, File currentJar, int currentApi) {
        mPatterns = patterns;
        mMinApi = minApi;
        mCurrentJar = currentJar;
        mCurrentApi = currentApi;
    }

    public Map<String, ApiClass> getClasses() {
        HashMap<String, ApiClass> map = new HashMap<String, ApiClass>();

        // Get all the android.jar. They are in platforms-#
        int apiLevel = mMinApi - 1;
        while (true) {
            apiLevel++;
            try {
                File jar = null;
                if (apiLevel == mCurrentApi) {
                    jar = mCurrentJar;
                }
                if (jar == null) {
                    jar = getAndroidJarFile(apiLevel);
                }
                if (jar == null || !jar.isFile()) {
                    System.out.println("Last API level found: " + (apiLevel-1));
                    break;
                }
                System.out.println("Found API " + apiLevel + " at " + jar.getPath());

                FileInputStream fis = new FileInputStream(jar);
                ZipInputStream zis = new ZipInputStream(fis);
                ZipEntry entry = zis.getNextEntry();
                while (entry != null) {
                    String name = entry.getName();

                    if (name.endsWith(SdkConstants.DOT_CLASS)) {
                        byte[] bytes = ByteStreams.toByteArray(zis);
                        if (bytes == null) {
                            System.err.println("Warning: Couldn't read " + name);
                            entry = zis.getNextEntry();
                            continue;
                        }

                        ClassReader reader = new ClassReader(bytes);
                        ClassNode classNode = new ClassNode();
                        reader.accept(classNode, 0 /*flags*/);

                        ApiClass theClass = addClass(map, classNode.name, apiLevel,
                                (classNode.access & Opcodes.ACC_DEPRECATED) != 0);

                        // super class
                        if (classNode.superName != null) {
                            theClass.addSuperClass(classNode.superName, apiLevel);
                        }

                        // interfaces
                        for (Object interfaceName : classNode.interfaces) {
                            theClass.addInterface((String) interfaceName, apiLevel);
                        }

                        // fields
                        for (Object field : classNode.fields) {
                            FieldNode fieldNode = (FieldNode) field;
                            if ((fieldNode.access & Opcodes.ACC_PRIVATE) != 0) {
                                continue;
                            }
                            if (!fieldNode.name.startsWith("this$") &&
                                    !fieldNode.name.equals("$VALUES")) {
                                boolean deprecated = (fieldNode.access & Opcodes.ACC_DEPRECATED) != 0;
                                theClass.addField(fieldNode.name, apiLevel, deprecated);
                            }
                        }

                        // methods
                        for (Object method : classNode.methods) {
                            MethodNode methodNode = (MethodNode) method;
                            if ((methodNode.access & Opcodes.ACC_PRIVATE) != 0) {
                                continue;
                            }
                            if (!methodNode.name.equals("<clinit>")) {
                                boolean deprecated = (methodNode.access & Opcodes.ACC_DEPRECATED) != 0;
                                theClass.addMethod(methodNode.name + methodNode.desc, apiLevel, deprecated);
                            }
                        }
                    }
                    entry = zis.getNextEntry();
                }

                Closeables.close(fis, true);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        postProcessClasses(map);

        return map;
    }

    private File getAndroidJarFile(int apiLevel) {
        for (String pattern : mPatterns) {
            File f = new File(pattern.replace("%", Integer.toString(apiLevel)));
            if (f.isFile()) {
                return f;
            }
        }
        return null;
    }

    private void postProcessClasses(Map<String, ApiClass> classes) {
        for (ApiClass theClass : classes.values()) {
            Map<String, Integer> methods = theClass.getMethods();
            Map<String, Integer> fixedMethods = new HashMap<String, Integer>();

            List<Pair<String, Integer>> superClasses = theClass.getSuperClasses();
            List<Pair<String, Integer>> interfaces = theClass.getInterfaces();

            methodLoop: for (Entry<String, Integer> method : methods.entrySet()) {
                String methodName = method.getKey();
                int apiLevel = method.getValue();

                if (!methodName.startsWith("<init>(")) {

                    for (Pair<String, Integer> parent : superClasses) {
                        // only check the parent if it was a parent class at the introduction
                        // of the method.
                        if (parent.getSecond() <= apiLevel) {
                            ApiClass parentClass = classes.get(parent.getFirst());
                            assert parentClass != null;
                            if (parentClass != null &&
                                    checkClassContains(theClass.getName(),
                                            methodName, apiLevel,
                                            classes, parentClass)) {
                                continue methodLoop;
                            }
                        }
                    }

                    for (Pair<String, Integer> parent : interfaces) {
                        // only check the parent if it was a parent class at the introduction
                        // of the method.
                        if (parent.getSecond() <= apiLevel) {
                            ApiClass parentClass = classes.get(parent.getFirst());
                            assert parentClass != null;
                            if (parentClass != null &&
                                    checkClassContains(theClass.getName(),
                                            methodName, apiLevel,
                                            classes, parentClass)) {
                                continue methodLoop;
                            }
                        }
                    }
                }

                // if we reach here. the method isn't an override
                fixedMethods.put(methodName, method.getValue());
            }

            theClass.replaceMethods(fixedMethods);
        }
    }

    private boolean checkClassContains(String className, String methodName, int apiLevel,
            Map<String, ApiClass> classMap, ApiClass parentClass) {

        Integer parentMethodApiLevel = parentClass.getMethods().get(methodName);
        if (parentMethodApiLevel != null && parentMethodApiLevel <= apiLevel) {
            // the parent class has the method and it was introduced in the parent at the
            // same api level as the method, or before.
            return true;
        }

        // check on this class parents.
        List<Pair<String, Integer>> superClasses = parentClass.getSuperClasses();
        List<Pair<String, Integer>> interfaces = parentClass.getInterfaces();

        for (Pair<String, Integer> parent : superClasses) {
            // only check the parent if it was a parent class at the introduction
            // of the method.
            if (parent.getSecond() <= apiLevel) {
                ApiClass superParentClass = classMap.get(parent.getFirst());
                assert superParentClass != null;
                if (superParentClass != null && checkClassContains(className, methodName, apiLevel,
                        classMap, superParentClass)) {
                    return true;
                }
            }
        }

        for (Pair<String, Integer> parent : interfaces) {
            // only check the parent if it was a parent class at the introduction
            // of the method.
            if (parent.getSecond() <= apiLevel) {
                ApiClass superParentClass = classMap.get(parent.getFirst());
                assert superParentClass != null;
                if (superParentClass != null && checkClassContains(className, methodName, apiLevel,
                        classMap, superParentClass)) {
                    return true;
                }
            }
        }

        return false;
    }

    private ApiClass addClass(HashMap<String, ApiClass> classes, String name, int apiLevel, boolean deprecated) {
        ApiClass theClass = classes.get(name);
        if (theClass == null) {
            theClass = new ApiClass(name, apiLevel);
            classes.put(name, theClass);
        }

        if (deprecated && apiLevel < theClass.deprecatedIn) {
            theClass.deprecatedIn = apiLevel;
        }

        return theClass;
    }
}
