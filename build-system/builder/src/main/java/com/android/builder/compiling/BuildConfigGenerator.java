/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.builder.compiling;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.AndroidBuilder;
import com.android.builder.model.ClassField;
import com.google.common.collect.Lists;
import com.squareup.javawriter.JavaWriter;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Class able to generate a BuildConfig class in Android project.
 * The BuildConfig class contains constants related to the build target.
 */
public class BuildConfigGenerator {

    public final static String BUILD_CONFIG_NAME = "BuildConfig.java";

    private final String mGenFolder;
    private final String mBuildConfigPackageName;

    private final List<ClassField> mFields = Lists.newArrayList();
    private List<Object> mItems = Lists.newArrayList();

    /**
     * Creates a generator
     * @param genFolder the gen folder of the project
     * @param buildConfigPackageName the package in which to create the class.
     */
    public BuildConfigGenerator(@NonNull String genFolder, @NonNull String buildConfigPackageName) {
        mGenFolder = checkNotNull(genFolder);
        mBuildConfigPackageName = checkNotNull(buildConfigPackageName);
    }

    public BuildConfigGenerator addField(
            @NonNull String type, @NonNull String name, @NonNull String value) {
        mFields.add(AndroidBuilder.createClassField(type, name, value));
        return this;
    }

    public BuildConfigGenerator addItems(@Nullable Collection<Object> items) {
        if (items != null) {
            mItems.addAll(items);
        }
        return this;
    }

    /**
     * Returns a File representing where the BuildConfig class will be.
     */
    public File getFolderPath() {
        File genFolder = new File(mGenFolder);
        return new File(genFolder, mBuildConfigPackageName.replace('.', File.separatorChar));
    }

    public File getBuildConfigFile() {
        File folder = getFolderPath();
        return new File(folder, BUILD_CONFIG_NAME);
    }

    /**
     * Generates the BuildConfig class.
     */
    public void generate() throws IOException {
        File pkgFolder = getFolderPath();
        if (!pkgFolder.isDirectory()) {
            if (!pkgFolder.mkdirs()) {
                throw new RuntimeException("Failed to create " + pkgFolder.getAbsolutePath());
            }
        }

        File buildConfigJava = new File(pkgFolder, BUILD_CONFIG_NAME);
        FileWriter out = new FileWriter(buildConfigJava);

        JavaWriter writer = new JavaWriter(out);

        Set<Modifier> publicFinal = EnumSet.of(Modifier.PUBLIC, Modifier.FINAL);
        Set<Modifier> publicFinalStatic = EnumSet.of(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC);

        writer.emitJavadoc("Automatically generated file. DO NOT MODIFY")
                .emitPackage(mBuildConfigPackageName)
                .beginType("BuildConfig", "class", publicFinal);

        for (ClassField field : mFields) {
            writer.emitField(
                    field.getType(),
                    field.getName(),
                    publicFinalStatic,
                    field.getValue());
        }

        for (Object item : mItems) {
            if (item instanceof ClassField) {
                ClassField field = (ClassField)item;
                writer.emitField(
                        field.getType(),
                        field.getName(),
                        publicFinalStatic,
                        field.getValue());

            } else if (item instanceof String) {
                writer.emitSingleLineComment((String) item);
            }
        }

        writer.endType();

        out.close();
    }
}
