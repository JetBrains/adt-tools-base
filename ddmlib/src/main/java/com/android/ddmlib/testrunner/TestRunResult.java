/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.ddmlib.testrunner;

import com.android.ddmlib.Log;
import com.android.ddmlib.testrunner.TestResult.TestStatus;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Holds results from a single test run.
 * <p/>
 * Maintains an accurate count of tests during execution, and tracks incomplete tests.
 */
public class TestRunResult {
    private static final String LOG_TAG = TestRunResult.class.getSimpleName();
    private final String mTestRunName;
    // Uses a synchronized map to make thread safe.
    // Uses a LinkedHashMap to have predictable iteration order
    private Map<TestIdentifier, TestResult> mTestResults =
        Collections.synchronizedMap(new LinkedHashMap<TestIdentifier, TestResult>());
    private Map<String, String> mRunMetrics = new HashMap<String, String>();
    private boolean mIsRunComplete = false;
    private long mElapsedTime = 0;
    private int mNumFailedTests = 0;
    private int mNumErrorTests = 0;
    private int mNumPassedTests = 0;
    private int mNumInCompleteTests = 0;
    private String mRunFailureError = null;

    /**
     * Create a {@link TestRunResult}.
     *
     * @param runName
     */
    public TestRunResult(String runName) {
        mTestRunName = runName;
    }

    /**
     * Create an empty{@link TestRunResult}.
     */
    public TestRunResult() {
        this("not started");
    }

    /**
     * @return the test run name
     */
    public String getName() {
        return mTestRunName;
    }

    /**
     * Gets a map of the test results.
     * @return
     */
    public Map<TestIdentifier, TestResult> getTestResults() {
        return mTestResults;
    }

    /**
     * Adds test run metrics.
     * <p/>
     * @param runMetrics the run metrics
     * @param aggregateMetrics if <code>true</code>, attempt to add given metrics values to any
     * currently stored values. If <code>false</code>, replace any currently stored metrics with
     * the same key.
     */
    public void addMetrics(Map<String, String> runMetrics, boolean aggregateMetrics) {
        if (aggregateMetrics) {
            for (Map.Entry<String, String> entry : runMetrics.entrySet()) {
                String existingValue = mRunMetrics.get(entry.getKey());
                String combinedValue = combineValues(existingValue, entry.getValue());
                mRunMetrics.put(entry.getKey(), combinedValue);
            }
        } else {
            mRunMetrics.putAll(runMetrics);
        }
    }

    /**
     * Combine old and new metrics value
     *
     * @param existingValue
     * @param value
     * @return
     */
    private String combineValues(String existingValue, String newValue) {
        if (existingValue != null) {
            try {
                Long existingLong = Long.parseLong(existingValue);
                Long newLong = Long.parseLong(newValue);
                return Long.toString(existingLong + newLong);
            } catch (NumberFormatException e) {
                // not a long, skip to next
            }
            try {
               Double existingDouble = Double.parseDouble(existingValue);
               Double newDouble = Double.parseDouble(newValue);
               return Double.toString(existingDouble + newDouble);
            } catch (NumberFormatException e) {
                // not a double either, fall through
            }
        }
        // default to overriding existingValue
        return newValue;
    }

    /**
     * @return a {@link Map} of the test test run metrics.
     */
    public Map<String, String> getRunMetrics() {
        return mRunMetrics;
    }

    /**
     * Gets the set of completed tests.
     */
    public Set<TestIdentifier> getCompletedTests() {
        Set<TestIdentifier> completedTests = new LinkedHashSet<TestIdentifier>();
        for (Map.Entry<TestIdentifier, TestResult> testEntry : getTestResults().entrySet()) {
            if (!testEntry.getValue().getStatus().equals(TestStatus.INCOMPLETE)) {
                completedTests.add(testEntry.getKey());
            }
        }
        return completedTests;
    }

    /**
     * @return <code>true</code> if test run failed.
     */
    public boolean isRunFailure() {
        return mRunFailureError != null;
    }

    /**
     * @return <code>true</code> if test run finished.
     */
    public boolean isRunComplete() {
        return mIsRunComplete;
    }

    void setRunComplete(boolean runComplete) {
        mIsRunComplete = runComplete;
    }

    void addElapsedTime(long elapsedTime) {
        mElapsedTime+= elapsedTime;
    }

    void setRunFailureError(String errorMessage) {
        mRunFailureError  = errorMessage;
    }

    /**
     * Gets the number of passed tests for this run.
     */
    public int getNumPassedTests() {
        return mNumPassedTests;
    }

    /**
     * Gets the number of tests in this run.
     */
    public int getNumTests() {
        return mTestResults.size();
    }

    /**
     * Gets the number of complete tests in this run ie with status != incomplete.
     */
    public int getNumCompleteTests() {
        return getNumTests() - getNumIncompleteTests();
    }

    /**
     * Gets the number of failed tests in this run.
     */
    public int getNumFailedTests() {
        return mNumFailedTests;
    }

    /**
     * Gets the number of error tests in this run.
     */
    public int getNumErrorTests() {
        return mNumErrorTests;
    }

    /**
     * Gets the number of incomplete tests in this run.
     */
    public int getNumIncompleteTests() {
        return mNumInCompleteTests;
    }

    /**
     * @return <code>true</code> if test run had any failed or error tests.
     */
    public boolean hasFailedTests() {
        return getNumErrorTests() > 0 || getNumFailedTests() > 0;
    }

    /**
     * @return
     */
    public long getElapsedTime() {
        return mElapsedTime;
    }

    /**
     * Return the run failure error message, <code>null</code> if run did not fail.
     */
    public String getRunFailureMessage() {
        return mRunFailureError;
    }

    /**
     * Report the start of a test.
     * @param test
     */
    void reportTestStarted(TestIdentifier test) {
        TestResult result = mTestResults.get(test);

        if (result != null) {
            Log.d(LOG_TAG, String.format("Replacing result for %s", test));
            switch (result.getStatus()) {
                case ERROR:
                    mNumErrorTests--;
                    break;
                case FAILURE:
                    mNumFailedTests--;
                    break;
                case PASSED:
                    mNumPassedTests--;
                    break;
                case INCOMPLETE:
                    // ignore
                    break;
            }
        } else {
            mNumInCompleteTests++;
        }
        mTestResults.put(test, new TestResult());
    }

    /**
     * Report a test failure.
     *
     * @param test
     * @param status
     * @param trace
     */
    void reportTestFailure(TestIdentifier test, TestStatus status, String trace) {
        TestResult result = mTestResults.get(test);
        if (result == null) {
            Log.d(LOG_TAG, String.format("Received test failure for %s without testStarted", test));
            result = new TestResult();
            mTestResults.put(test, result);
        } else if (result.getStatus().equals(TestStatus.PASSED)) {
            // this should never happen...
            Log.d(LOG_TAG, String.format("Replacing passed result for %s", test));
            mNumPassedTests--;
        }

        result.setStackTrace(trace);
        switch (status) {
            case ERROR:
                mNumErrorTests++;
                result.setStatus(TestStatus.ERROR);
                break;
            case FAILURE:
                result.setStatus(TestStatus.FAILURE);
                mNumFailedTests++;
                break;
        }
    }

    /**
     * Report the end of the test
     *
     * @param test
     * @param testMetrics
     * @return <code>true</code> if test was recorded as passed, false otherwise
     */
    boolean reportTestEnded(TestIdentifier test, Map<String, String> testMetrics) {
        TestResult result = mTestResults.get(test);
        if (result == null) {
            Log.d(LOG_TAG, String.format("Received test ended for %s without testStarted", test));
            result = new TestResult();
            mTestResults.put(test, result);
        } else {
            mNumInCompleteTests--;
        }

        result.setEndTime(System.currentTimeMillis());
        result.setMetrics(testMetrics);
        if (result.getStatus().equals(TestStatus.INCOMPLETE)) {
            result.setStatus(TestStatus.PASSED);
            mNumPassedTests++;
            return true;
        }
        return false;
    }
}
