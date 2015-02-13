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

package com.android.builder.profile;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import com.google.common.base.Strings;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Configures and creates instances of {@link ProcessRecorder}.
 *
 * There can be only one instance of {@link ProcessRecorder} per process (well class loader
 * to be exact). This instance can be configured initially before any calls to
 * {@link ThreadRecorder#get()} is made. An exception will be thrown if an attempt is made to
 * configure the instance of {@link ProcessRecorder} past this initialization window.
 *
 */
public class ProcessRecorderFactory {

    public static void shutdown() throws InterruptedException {
        synchronized (LOCK) {
            if (sINSTANCE.isInitialized()) {
                sINSTANCE.get().finish();
            }
            sINSTANCE.processRecorder = null;
        }
    }

    public static void initialize(ILogger logger, File out) throws IOException {

        synchronized (LOCK) {
            if (sINSTANCE.isInitialized() || !isEnabled()) {
                return;
            }
            sINSTANCE.setLogger(logger);
            sINSTANCE.setRecordWriter(new ProcessRecorder.JsonRecordWriter(new FileWriter(out)));
        }
    }

    private static boolean sENABLED = !Strings.isNullOrEmpty(System.getenv("RECORD_SPANS"));

    static boolean isEnabled() {
        return sENABLED;
    }

    @VisibleForTesting
    static void setEnabled(boolean enabled) {
        sENABLED = enabled;
    }

    /**
     * Sets the {@link ProcessRecorder.JsonRecordWriter }
     * @param recordWriter
     */
    public synchronized void setRecordWriter(
            @NonNull ProcessRecorder.ExecutionRecordWriter recordWriter) {
        assertRecorderNotCreated();
        this.recordWriter = recordWriter;
    }

    public synchronized void setLogger(@NonNull ILogger iLogger) {
        assertRecorderNotCreated();
        this.iLogger = iLogger;
    }

    public static ProcessRecorderFactory getFactory() {
        return sINSTANCE;
    }

    private boolean isInitialized() {
        return processRecorder != null;
    }

    @SuppressWarnings("VariableNotUsedInsideIf")
    private void assertRecorderNotCreated() {
        if (isInitialized()) {
            throw new RuntimeException("ProcessRecorder already created.");
        }
    }

    static final Object LOCK = new Object();
    static ProcessRecorderFactory sINSTANCE = new ProcessRecorderFactory();

    @Nullable
    private ProcessRecorder processRecorder = null;
    @Nullable
    private ProcessRecorder.ExecutionRecordWriter recordWriter = null;
    @Nullable
    private ILogger iLogger = null;

    synchronized ProcessRecorder get() {
        if (processRecorder == null) {
            if (recordWriter == null) {
                throw new RuntimeException("recordWriter not configured.");
            }
            if (iLogger == null) {
                iLogger = new StdLogger(StdLogger.Level.INFO);
            }
            processRecorder = new ProcessRecorder(recordWriter, iLogger);
        }
        return processRecorder;
    }
}
