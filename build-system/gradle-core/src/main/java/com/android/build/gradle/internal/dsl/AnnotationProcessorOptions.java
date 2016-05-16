/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * DSL object for configuring APT options.
 */
@SuppressWarnings("UnnecessaryInheritDoc")
public class AnnotationProcessorOptions implements CoreAnnotationProcessorOptions {

    private final List<String> classNames = Lists.newArrayList();
    private final Map<String, String> arguments = Maps.newHashMap();
    @Nullable
    private Boolean includeClasspath = null;

    /** {@inheritDoc} */
    @NonNull
    @Override
    public List<String> getClassNames() {
        return classNames;
    }

    public void setClassNames(List<String> classNames) {
        this.classNames.clear();
        this.classNames.addAll(classNames);
    }

    public void className(String className) {
        classNames.add(className);
    }

    /**
     * Annotation processors to run.
     *
     * If empty, processors will be automatically discovered.
     */
    public void classNames(Collection<String> className) {
        classNames.addAll(className);
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public Map<String, String> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, String> arguments) {
        this.arguments.clear();
        this.arguments.putAll(arguments);
    }

    public void argument(@NonNull String key, @NonNull String value) {
        arguments.put(key, value);
    }

    /**
     * Options for the annotation processors.
     */
    public void arguments(Map<String, String> arguments) {
        this.arguments.putAll(arguments);
    }

    @Override
    @Nullable
    public Boolean getIncludeClasspath() {
        return includeClasspath;
    }

    public void setIncludeClasspath(@Nullable Boolean includeClasspath) {
        this.includeClasspath = includeClasspath;
    }

    public void _initWith(CoreAnnotationProcessorOptions aptOptions) {
        setClassNames(aptOptions.getClassNames());
        setArguments(aptOptions.getArguments());
        setIncludeClasspath(aptOptions.getIncludeClasspath());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AnnotationProcessorOptions that = (AnnotationProcessorOptions) o;
        return Objects.equal(classNames, that.classNames) &&
                Objects.equal(arguments, that.arguments) &&
                Objects.equal(includeClasspath, that.includeClasspath);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(classNames, arguments);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("classNames", classNames)
                .add("arguments", arguments)
                .add("includeClasspath", includeClasspath)
                .toString();
    }
}
