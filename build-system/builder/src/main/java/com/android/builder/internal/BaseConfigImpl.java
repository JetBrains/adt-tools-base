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

package com.android.builder.internal;

import com.android.annotations.NonNull;
import com.android.builder.model.BaseConfig;
import com.android.builder.model.ClassField;
import com.google.common.collect.Lists;

import java.io.File;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * An object that contain a BuildConfig configuration
 */
public class BaseConfigImpl implements Serializable, BaseConfig {
    private static final long serialVersionUID = 1L;

    private final List<ClassField> mBuildConfigFields = Lists.newArrayList();
    private final List<File> mProguardFiles = Lists.newArrayList();
    private final List<File> mConsumerProguardFiles = Lists.newArrayList();

    public void setBuildConfigFields(@NonNull ClassField... fields) {
        mBuildConfigFields.clear();
        mBuildConfigFields.addAll(Arrays.asList(fields));
    }

    public void setBuildConfigFields(@NonNull Collection<ClassField> fields) {
        mBuildConfigFields.clear();
        mBuildConfigFields.addAll(fields);
    }

    public void addBuildConfigField(@NonNull ClassField field) {
        mBuildConfigFields.add(field);
    }

    @Override
    @NonNull
    public List<ClassField> getBuildConfigFields() {
        return mBuildConfigFields;
    }

    @Override
    @NonNull
    public List<File> getProguardFiles() {
        return mProguardFiles;
    }

    @Override
    @NonNull
    public List<File> getConsumerProguardFiles() {
        return mConsumerProguardFiles;
    }

    protected void _initWith(BaseConfig that) {
        setBuildConfigFields(that.getBuildConfigFields());

        mProguardFiles.clear();
        mProguardFiles.addAll(that.getProguardFiles());

        mConsumerProguardFiles.clear();
        mConsumerProguardFiles.addAll(that.getConsumerProguardFiles());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BaseConfigImpl that = (BaseConfigImpl) o;

        if (!mBuildConfigFields.equals(that.mBuildConfigFields)) return false;
        if (!mProguardFiles.equals(that.mProguardFiles)) return false;
        if (!mConsumerProguardFiles.equals(that.mConsumerProguardFiles)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = mBuildConfigFields.hashCode();
        result = 31 * result + mProguardFiles.hashCode();
        result = 31 * result + mConsumerProguardFiles.hashCode();
        return result;
    }
}
