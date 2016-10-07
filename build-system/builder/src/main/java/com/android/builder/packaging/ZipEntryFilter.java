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

package com.android.builder.packaging;

/**
 * Classes which implement this interface provides a method to check whether a file should
 * be added to a Jar file.
 */
public interface ZipEntryFilter {


    /**
     * Checks a file for inclusion in a Jar archive.
     * @param archivePath the archive file path of the entry
     * @return should the file be included?
     * @throws ZipAbortException if writing the file should be aborted.
     */
    boolean checkEntry(String archivePath) throws ZipAbortException;
}
