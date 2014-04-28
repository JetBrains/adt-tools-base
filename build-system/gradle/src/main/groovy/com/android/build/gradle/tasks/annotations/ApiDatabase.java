/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.tasks.annotations;

import com.android.annotations.NonNull;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads a signature file in the format of the new API files in frameworks/base/api */
public class ApiDatabase {
    @NonNull
    private final List<String> lines;
    /** Map from class name to set of field names */
    @NonNull private final  Map<String,Set<String>> fieldMap =
            Maps.newHashMapWithExpectedSize(1000);
    /** Map from class name to map of method names whose values are overloaded signatures */
    @NonNull private final  Map<String,Map<String,List<String>>> methodMap =
            Maps.newHashMapWithExpectedSize(1000);
    @NonNull private final Map<String, List<String>> inheritsFrom =
            Maps.newHashMapWithExpectedSize(1000);

    public ApiDatabase(@NonNull List<String> lines) {
        this.lines = lines;
        readApi();
    }

    public ApiDatabase(@NonNull File api) throws IOException {
        this(Files.readLines(api, Charsets.UTF_8));
    }

    public boolean hasMethod(String className, String methodName, String arguments) {
        Map<String, List<String>> methods = methodMap.get(className);
        if (methods != null) {
            List<String> strings = methods.get(methodName);
            if (strings != null && strings.contains(arguments)) {
                return true;
            }
        }

        List<String> inheritsFrom = this.inheritsFrom.get(className);
        if (inheritsFrom != null) {
            for (String clz : inheritsFrom) {
                if (hasMethod(clz, methodName, arguments)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean hasField(String className, String fieldName) {
        Set<String> fields = fieldMap.get(className);
        if (fields != null && fields.contains(fieldName)) {
            return true;
        }

        List<String> inheritsFrom = this.inheritsFrom.get(className);
        if (inheritsFrom != null) {
            for (String clz : inheritsFrom) {
                if (hasField(clz, fieldName)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void readApi() {
        String MODIFIERS =
                "((deprecated|public|static|private|protected|final|abstract|\\s*)\\s+)*";
        Pattern PACKAGE = Pattern.compile("package (\\S+) \\{");
        Pattern CLASS =
                Pattern.compile(MODIFIERS + "(class|interface|enum)\\s+(\\S+)\\s+(extends (.+))?(implements (.+))?(.*)\\{");
        Pattern METHOD = Pattern.compile("(method|ctor)\\s+" +
                MODIFIERS + "(.+)??\\s+(\\S+)\\s*\\((.*)\\)(.*);");
        Pattern CTOR = Pattern.compile("(method|ctor)\\s+.*\\((.*)\\)(.*);");
        Pattern FIELD = Pattern.compile("(enum_constant|field)\\s+" +
                MODIFIERS + "(.+)\\s+(\\S+)\\s*;");

        String currentPackage = null;
        String currentClass = null;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.equals("}")) {
                continue;
            }
            if (line.startsWith("method ")) {
                Matcher matcher = METHOD.matcher(line);
                if (!matcher.matches()) {
                    Extractor.warning("Warning: Did not match as a member: " + line);
                } else {
                    assert currentClass != null;
                    Map<String,List<String>> memberMap = methodMap.get(currentClass);
                    if (memberMap == null) {
                        memberMap = Maps.newHashMap();
                        methodMap.put(currentClass, memberMap);
                    }
                    String methodName = matcher.group(5);
                    List<String> signatures = memberMap.get(methodName);
                    if (signatures == null) {
                        signatures = Lists.newArrayList();
                        memberMap.put(methodName, signatures);
                    }
                    String signature = matcher.group(6);
                    signature = signature.trim().replace(" ", "").replace(" ", "");
                    signatures.add(signature);
                }
            } else if (line.startsWith("ctor ")) {
                Matcher matcher = CTOR.matcher(line);
                if (!matcher.matches()) {
                    Extractor.warning("Warning: Did not match as a member: " + line);
                } else {
                    assert currentClass != null;
                    Map<String,List<String>> memberMap = methodMap.get(currentClass);
                    if (memberMap == null) {
                        memberMap = Maps.newHashMap();
                        methodMap.put(currentClass, memberMap);
                    }
                    @SuppressWarnings("UnnecessaryLocalVariable")
                    String methodName = currentClass;
                    List<String> signatures = memberMap.get(methodName);
                    if (signatures == null) {
                        signatures = Lists.newArrayList();
                        memberMap.put(methodName, signatures);
                        memberMap.put(methodName.substring(methodName.lastIndexOf('.') + 1),
                                signatures);
                    }
                    String signature = matcher.group(2);
                    signature = signature.trim().replace(" ", "").replace(" ", "");
                    signatures.add(signature);
                }
            } else if (line.startsWith("enum_constant ") || line.startsWith("field ")) {
                int equals = line.indexOf('=');
                if (equals != -1) {
                    line = line.substring(0, equals).trim();
                    int semi = line.indexOf(';');
                    if (semi == -1) {
                        line = line + ';';
                    }
                } else if (!line.endsWith(";")) {
                    int semi = line.indexOf(';');
                    if (semi != -1) {
                        line = line.substring(0, semi + 1);
                    }
                }
                Matcher matcher = FIELD.matcher(line);
                if (!matcher.matches()) {
                    Extractor.warning("Warning: Did not match as a member: " + line);
                } else {
                    assert currentClass != null;
                    String fieldName = matcher.group(5);
                    Set<String> fieldSet = fieldMap.get(currentClass);
                    if (fieldSet == null) {
                        fieldSet = Sets.newHashSet();
                        fieldMap.put(currentClass, fieldSet);
                    }
                    fieldSet.add(fieldName);
                }
            } else if (line.startsWith("package ")) {
                Matcher matcher = PACKAGE.matcher(line);
                if (!matcher.matches()) {
                    Extractor.warning("Warning: Did not match as a package: " + line);
                } else {
                    currentPackage = matcher.group(1);
                }
            } else {
                Matcher matcher = CLASS.matcher(line);
                if (!matcher.matches()) {
                    Extractor.warning("Warning: Did not match as a class/interface: " + line);
                } else {
                    currentClass = currentPackage + '.' + matcher.group(4);

                    String superClass = matcher.group(6);
                    if (superClass != null) {
                        Splitter splitter = Splitter.on(' ').trimResults().omitEmptyStrings();
                        for (String from : splitter.split(superClass)) {
                            if (from.equals("implements")) {  // workaround for broken regexp
                                continue;
                            }
                            addInheritsFrom(currentClass, from);
                        }
                        addInheritsFrom(currentClass, superClass.trim());
                    }
                    String implementsList = matcher.group(8);
                    if (implementsList != null) {
                        Splitter splitter = Splitter.on(' ').trimResults().omitEmptyStrings();
                        for (String from : splitter.split(implementsList)) {
                            addInheritsFrom(currentClass, from);
                        }
                    }
                }
            }
        }
    }

    private void addInheritsFrom(String cls, String inheritsFrom) {
        List<String> list = this.inheritsFrom.get(cls);
        if (list == null) {
            list = Lists.newArrayList();
            this.inheritsFrom.put(cls, list);
        }
        list.add(inheritsFrom);
    }
}
