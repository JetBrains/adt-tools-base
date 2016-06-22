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
import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.UsageTracker;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
                sINSTANCE.uploadData();
            }
            sINSTANCE.processRecorder = null;
        }
    }

    private static boolean sENABLED = !Strings.isNullOrEmpty(System.getenv("RECORD_SPANS"));

    @NonNull
    private ScheduledExecutorService mScheduledExecutorService = Executors.newScheduledThreadPool(1);

    @VisibleForTesting
    ProcessRecorderFactory() {}
    @Nullable
    private ILogger mLogger = null;

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

    /**
     * Set up the the ProcessRecorder. Idempotent for multi-project builds.
     *
     */
    public static void initialize(
            @NonNull String gradleVersion,
            @NonNull ILogger logger,
            @NonNull File out) {

        synchronized (LOCK) {
            if (sINSTANCE.isInitialized()) {
                return;
            }
            sINSTANCE.setLogger(logger);
            if (isEnabled()) {
                sINSTANCE.setOutputFile(out);
                try {
                    sINSTANCE.setRecordWriter(
                            new ProcessRecorder.JsonRecordWriter(new FileWriter(out)));
                } catch (IOException e) {
                    // This can only happen in performance test mode.
                    throw new RuntimeException("Unable to open json profile for writing", e);
                }
            }

            ProcessRecorder recorder = sINSTANCE.get(); // Initialize the ProcessRecorder instance
            setGlobalProperties(recorder, gradleVersion);
        }
    }

    private static void setGlobalProperties(
            @NonNull ProcessRecorder recorder, @NonNull String gradleVersion) {
        recorder.getProperties()
                .setOsName(System.getProperty("os.name"))
                .setOsVersion(System.getProperty("os.version"))
                .setJavaVersion(System.getProperty("java.version"))
                .setJavaVmVersion(System.getProperty("java.vm.version"))
                .setMaxMemory(Runtime.getRuntime().maxMemory())
                .setGradleVersion(gradleVersion);
    }

    public synchronized void setLogger(@NonNull ILogger iLogger) {
        assertRecorderNotCreated();
        this.mLogger = iLogger;
    }

    public static ProcessRecorderFactory getFactory() {
        return sINSTANCE;
    }

    boolean isInitialized() {
        return processRecorder != null;
    }

    @SuppressWarnings("VariableNotUsedInsideIf")
    private void assertRecorderNotCreated() {
        if (isInitialized()) {
            throw new RuntimeException("ProcessRecorder already created.");
        }
    }

    private static final Object LOCK = new Object();
    static ProcessRecorderFactory sINSTANCE = new ProcessRecorderFactory();

    @Nullable
    private ProcessRecorder processRecorder = null;
    @Nullable
    private ProcessRecorder.ExecutionRecordWriter recordWriter = null;

    @VisibleForTesting
    public static void initializeForTests(ProcessRecorder.ExecutionRecordWriter recordWriter) {
        sINSTANCE = new ProcessRecorderFactory();
        ProcessRecorder.resetForTests();
        setEnabled(true);
        sINSTANCE.setRecordWriter(recordWriter);
        ProcessRecorder recorder = sINSTANCE.get(); // Initialize the ProcessRecorder instance
        setGlobalProperties(recorder, "2.10");
    }

    private static void initializeAnalytics(@NonNull ILogger logger,
            @NonNull ScheduledExecutorService eventLoop) {
        AnalyticsSettings settings;
        try {
            settings = AnalyticsSettings.loadSettings();
        } catch (IOException e) {
            logger.error(e, "Could not initialize analytics, treating as opt-out.");
            settings = new AnalyticsSettings();
            settings.setHasOptedIn(false);
        }
        UsageTracker.initialize(settings, eventLoop);
        UsageTracker tracker = UsageTracker.getInstance();
        tracker.setMaxJournalTime(10, TimeUnit.MINUTES);
        tracker.setMaxJournalSize(1000);
    }

    private File outputFile = null;

    private void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    synchronized ProcessRecorder get() {
        if (processRecorder == null) {
            if (mLogger == null) {
                mLogger = new StdLogger(StdLogger.Level.INFO);
            }
            initializeAnalytics(mLogger, mScheduledExecutorService);
            processRecorder = new ProcessRecorder(recordWriter, mLogger);
        }

        return processRecorder;
    }

    private void uploadData() {

        if (outputFile == null) {
            return;
        }
        try {
            URL u = new URL("http://android-devtools-logging.appspot.com/log/");
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");

            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Content-Length", String.valueOf(outputFile.length()));
            try (InputStream is = new BufferedInputStream(new FileInputStream(outputFile));
                 OutputStream os = conn.getOutputStream()) {
                ByteStreams.copy(is, os);
            }

            String line;
            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(conn.getInputStream()))) {

                while ((line = reader.readLine()) != null) {
                    if (mLogger != null) {
                        mLogger.info("From POST : " + line);
                    }
                }
            }
        } catch(Exception e) {
            if (mLogger != null) {
                mLogger.warning("An exception while generated while uploading the profiler data");
                mLogger.error(e, "Exception while uploading the profiler data");
            }
        }
    }
}
