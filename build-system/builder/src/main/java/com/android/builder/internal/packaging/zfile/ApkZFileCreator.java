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

package com.android.builder.internal.packaging.zfile;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.internal.packaging.zip.StoredEntry;
import com.android.builder.internal.packaging.zip.ZFile;
import com.android.builder.internal.packaging.zip.ZFileOptions;
import com.android.builder.packaging.ApkCreator;
import com.android.builder.packaging.ApkCreatorFactory;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.io.Closer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * {@link ApkCreator} that uses {@link ZFileOptions} to generate the APK.
 */
class ApkZFileCreator implements ApkCreator {

    /**
     * The zip file.
     */
    @NonNull
    private final ZFile mZip;

    /**
     * Has the zip file been closed?
     */
    private boolean mClosed;

    /**
     * Creates a new creator.
     *
     * @param creationData the data needed to create the APK
     * @param options zip file options
     * @throws IOException failed to create the zip
     */
    ApkZFileCreator(@NonNull ApkCreatorFactory.CreationData creationData,
            @NonNull ZFileOptions options) throws IOException {
        mZip = ZFiles.apk(creationData.getApkPath(), options, creationData.getPrivateKey(),
                creationData.getCertificate(), creationData.getBuiltBy(),
                creationData.getCreatedBy(), creationData.getMinSdkVersion());
        mClosed = false;
    }

    @Override
    public void writeZip(@NonNull File zip, @Nullable Function<String, String> transform,
            @Nullable Predicate<String> isIgnored) throws IOException {
        Preconditions.checkState(!mClosed, "mClosed == true");
        Preconditions.checkArgument(zip.isFile(), "!zip.isFile()");

        Closer closer = Closer.create();
        try {
            ZFile toMerge = closer.register(new ZFile(zip));

            Predicate<String> predicate;
            if (isIgnored == null) {
                predicate = Predicates.alwaysFalse();
            } else {
                predicate = isIgnored;
            }

            mZip.mergeFrom(toMerge, predicate);
        } catch (Throwable t) {
            throw closer.rethrow(t);
        } finally {
            closer.close();
        }
    }

    @Override
    public void writeFile(@NonNull File inputFile, @NonNull String apkPath) throws IOException {
        Preconditions.checkState(!mClosed, "mClosed == true");

        Closer closer = Closer.create();
        try {
            FileInputStream inputFileStream = closer.register(new FileInputStream(inputFile));
            mZip.add(apkPath, inputFileStream, true);
        } catch (IOException e) {
            throw closer.rethrow(e, IOException.class);
        } catch (Throwable t) {
            throw closer.rethrow(t);
        } finally {
            closer.close();
        }
    }

    @Override
    public void deleteFile(@NonNull String apkPath) throws IOException {
        Preconditions.checkState(!mClosed, "mClosed == true");

        StoredEntry entry = mZip.get(apkPath);
        if (entry != null) {
            entry.delete();
        }
    }

    @Override
    public void close() throws IOException {
        if (mClosed) {
            return;
        }

        mZip.sortZipContents();
        mZip.close();
        mClosed = true;
    }
}
