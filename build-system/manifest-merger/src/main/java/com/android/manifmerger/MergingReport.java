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

package com.android.manifmerger;

import com.android.annotations.NonNull;
import com.android.annotations.concurrency.Immutable;
import com.android.utils.ILogger;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * Contains the result of 2 files merging.
 *
 * TODO: more work necessary, this is pretty raw as it stands.
 */
@Immutable
public class MergingReport {

    private final Optional<XmlDocument> mMergedDocument;
    private final Result mResult;
    // list of logging events, ordered by their recording time.
    private final ImmutableList<Record> mRecords;
    private final ImmutableList<String> mIntermediaryStages;
    private final ActionRecorder mActionRecorder;

    private MergingReport(Optional<XmlDocument> mergedDocument,
            @NonNull Result result,
            @NonNull ImmutableList<Record> records,
            @NonNull ImmutableList<String> intermediaryStages,
            @NonNull ActionRecorder actionRecorder) {
        mMergedDocument = mergedDocument;
        mResult = result;
        mRecords = records;
        mIntermediaryStages = intermediaryStages;
        mActionRecorder = actionRecorder;
    }

    /**
     * dumps all logging records to a logger.
     */
    public void log(ILogger logger) {
        for (Record record : mRecords) {
            switch(record.mType) {
                case WARNING:
                    logger.warning(record.mLog);
                    break;
                case ERROR:
                    logger.error(null /* throwable */, record.mLog);
                    break;
                case INFO:
                    logger.info(record.mLog);
                    break;
                default:
                    logger.error(null /* throwable */, "Unhandled record type " + record.mType);
            }
        }
        mActionRecorder.log(logger);
    }

    /**
     * Return the resulting merged document.
     */
    public Optional<XmlDocument> getMergedDocument() {
        return mMergedDocument;
    }

    /**
     * Returns all the merging intermediary stages if
     * {@link com.android.manifmerger.ManifestMerger2.Invoker.Features#KEEP_INTERMEDIARY_STAGES}
     * is set.
     */
    public ImmutableList<String> getIntermediaryStages() {
        return mIntermediaryStages;
    }

    /**
     * Overall result of the merging process.
     */
    public enum Result {
        SUCCESS,

        WARNING,

        ERROR
    }

    public Result getResult() {
        return mResult;
    }

    /**
     * Log record. This is used to give users some information about what is happening and
     * what might have gone wrong.
     *
     * TODO: need to enhance to add SourceLocation, and make this more machine readable.
     */
    private static class Record {

        private Record(Type type, String mLog) {
            this.mType = type;
            this.mLog = mLog;
        }

        enum Type {WARNING, ERROR, INFO }

        private final Type mType;
        private final String mLog;
    }

    /**
     * This builder is used to accumulate logging, action recording and intermediary results as
     * well as final result of the merging activity.
     *
     * Once the merging is finished, the {@link #build()} is called to return an immutable version
     * of itself with all the logging, action recordings and xml files obtainable.
     *
     */
    static class Builder {

        private Optional<XmlDocument> mMergedDocument = Optional.absent();
        private ImmutableList.Builder<Record> mRecordBuilder = new ImmutableList.Builder<Record>();
        private ImmutableList.Builder<String> mIntermediaryStages = new ImmutableList.Builder<String>();
        private boolean mHasWarnings = false;
        private boolean mHasErrors = false;
        private ActionRecorder.Builder mActionRecorder = new ActionRecorder.Builder();
        private final ILogger mLogger;

        Builder(ILogger logger) {
            mLogger = logger;
        }


        Builder setMergedDocument(@NonNull XmlDocument mergedDocument) {
            mMergedDocument = Optional.of(mergedDocument);
            return this;
        }

        Builder addInfo(String info) {
            mRecordBuilder.add(new Record(Record.Type.INFO, info));
            return this;
        }

        Builder addWarning(String warning) {
            mHasWarnings = true;
            mRecordBuilder.add(new Record(Record.Type.WARNING, warning));
            return this;
        }

        Builder addError(String error) {
            mHasErrors = true;
            mRecordBuilder.add(new Record(Record.Type.ERROR, error));
            return this;
        }

        Builder addMergingStage(String xml) {
            mIntermediaryStages.add(xml);
            return this;
        }

        ActionRecorder.Builder getActionRecorder() {
            return mActionRecorder;
        }

        MergingReport build() {
            Result result = mHasErrors
                    ? Result.ERROR
                    : mHasWarnings
                            ? Result.WARNING
                            : Result.SUCCESS;

            return new MergingReport(
                    mMergedDocument,
                    result,
                    mRecordBuilder.build(),
                    mIntermediaryStages.build(),
                    mActionRecorder.build());
        }

        public ILogger getLogger() {
            return mLogger;
        }
    }
}
