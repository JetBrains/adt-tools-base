/*
 * Copyright (C) 2009 The Android Open Source Project
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
import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.testrunner.TestResult.TestStatus;

import org.kxml2.io.KXmlSerializer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Writes JUnit results to an XML files in a format consistent with
 * Ant's XMLJUnitResultFormatter.
 * <p/>
 * Creates a separate XML file per test run.
 * <p/>
 */
public class XmlTestRunListener implements ITestRunListener {

    private static final String LOG_TAG = "XmlResultReporter";

    private static final String TEST_RESULT_FILE_SUFFIX = ".xml";
    private static final String TEST_RESULT_FILE_PREFIX = "test_result_";

    private static final String TESTSUITE = "testsuite";
    private static final String TESTCASE = "testcase";
    private static final String ERROR = "error";
    private static final String FAILURE = "failure";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_TIME = "time";
    private static final String ATTR_ERRORS = "errors";
    private static final String ATTR_FAILURES = "failures";
    private static final String ATTR_TESTS = "tests";
    //private static final String ATTR_TYPE = "type";
    //private static final String ATTR_MESSAGE = "message";
    private static final String PROPERTIES = "properties";
    private static final String ATTR_CLASSNAME = "classname";
    private static final String TIMESTAMP = "timestamp";
    private static final String HOSTNAME = "hostname";

    /** the XML namespace */
    private static final String ns = null;

    private String mHostName = "localhost";

    private File mReportDir = new File(System.getProperty("java.io.tmpdir"));

    private String mReportPath = "";

    private TestRunResult mRunResult = new TestRunResult();

    /**
     * Sets the report file to use.
     */
    public void setReportDir(File file) {
        mReportDir = file;
    }

    public void setHostName(String hostName) {
        mHostName = hostName;
    }

    /**
     * Returns the {@link TestRunResult}
     * @return the test run results.
     */
    public TestRunResult getRunResult() {
        return mRunResult;
    }

    @Override
    public void testRunStarted(String runName, int numTests) {
        mRunResult = new TestRunResult(runName);
    }

    @Override
    public void testStarted(TestIdentifier test) {
       mRunResult.reportTestStarted(test);
    }

    @Override
    public void testFailed(TestFailure status, TestIdentifier test, String trace) {
        if (status.equals(TestFailure.ERROR)) {
            mRunResult.reportTestFailure(test, TestStatus.ERROR, trace);
        } else {
            mRunResult.reportTestFailure(test, TestStatus.FAILURE, trace);
        }
        Log.d(LOG_TAG, String.format("%s %s: %s", test, status, trace));
    }

    @Override
    public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
        mRunResult.reportTestEnded(test, testMetrics);
    }

    @Override
    public void testRunFailed(String errorMessage) {
        mRunResult.setRunFailureError(errorMessage);
    }

    @Override
    public void testRunStopped(long arg0) {
        // ignore
    }

    @Override
    public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
        mRunResult.setRunComplete(true);
        generateDocument(mReportDir, elapsedTime);
    }

    /**
     * Creates a report file and populates it with the report data from the completed tests.
     */
    private void generateDocument(File reportDir, long elapsedTime) {
        String timestamp = getTimestamp();

        OutputStream stream = null;
        try {
            stream = createOutputResultStream(reportDir);
            KXmlSerializer serializer = new KXmlSerializer();
            serializer.setOutput(stream, "UTF-8");
            serializer.startDocument("UTF-8", null);
            serializer.setFeature(
                    "http://xmlpull.org/v1/doc/features.html#indent-output", true);
            // TODO: insert build info
            printTestResults(serializer, timestamp, elapsedTime);
            serializer.endDocument();
            String msg = String.format("XML test result file generated at %s. Total tests %d, " +
                    "Failed %d, Error %d", getAbsoluteReportPath(), mRunResult.getNumTests(),
                    mRunResult.getNumFailedTests(), mRunResult.getNumErrorTests());
            Log.logAndDisplay(LogLevel.INFO, LOG_TAG, msg);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to generate report data");
            // TODO: consider throwing exception
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private String getAbsoluteReportPath() {
        return mReportPath ;
    }

    /**
     * Return the current timestamp as a {@link String}.
     */
    String getTimestamp() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",
                Locale.getDefault());
        TimeZone gmt = TimeZone.getTimeZone("UTC");
        dateFormat.setTimeZone(gmt);
        dateFormat.setLenient(true);
        String timestamp = dateFormat.format(new Date());
        return timestamp;
    }

    /**
     * Creates a {@link File} where the report will be created.
     * @param reportDir the root directory of the report.
     * @return a file
     * @throws IOException
     */
    protected File getResultFile(File reportDir) throws IOException {
        File reportFile = File.createTempFile(TEST_RESULT_FILE_PREFIX, TEST_RESULT_FILE_SUFFIX,
                reportDir);
        Log.i(LOG_TAG, String.format("Created xml report file at %s",
                reportFile.getAbsolutePath()));

        return reportFile;
    }

    /**
     * Creates the output stream to use for test results. Exposed for mocking.
     */
    OutputStream createOutputResultStream(File reportDir) throws IOException {
        File reportFile = getResultFile(reportDir);
        mReportPath = reportFile.getAbsolutePath();
        return new BufferedOutputStream(new FileOutputStream(reportFile));
    }

    protected String getTestSuiteName() {
        return mRunResult.getName();
    }

    void printTestResults(KXmlSerializer serializer, String timestamp, long elapsedTime)
            throws IOException {
        serializer.startTag(ns, TESTSUITE);
        String name = getTestSuiteName();
        if (name != null) {
            serializer.attribute(ns, ATTR_NAME, name);
        }
        serializer.attribute(ns, ATTR_TESTS, Integer.toString(mRunResult.getNumTests()));
        serializer.attribute(ns, ATTR_FAILURES, Integer.toString(mRunResult.getNumFailedTests()));
        serializer.attribute(ns, ATTR_ERRORS, Integer.toString(mRunResult.getNumErrorTests()));
        serializer.attribute(ns, ATTR_TIME, Double.toString((double) elapsedTime / 1000.f));
        serializer.attribute(ns, TIMESTAMP, timestamp);
        serializer.attribute(ns, HOSTNAME, mHostName);

        serializer.startTag(ns, PROPERTIES);
        setPropertiesAttributes(serializer, ns);
        serializer.endTag(ns, PROPERTIES);

        Map<TestIdentifier, TestResult> testResults = mRunResult.getTestResults();
        for (Map.Entry<TestIdentifier, TestResult> testEntry : testResults.entrySet()) {
            print(serializer, testEntry.getKey(), testEntry.getValue());
        }

        serializer.endTag(ns, TESTSUITE);
    }

    /**
     * Sets the attributes on properties.
     * @param serializer the serializer
     * @param namespace the namespace
     * @throws IOException
     */
    protected void setPropertiesAttributes(KXmlSerializer serializer, String namespace)
            throws IOException {
    }

    protected String getTestName(TestIdentifier testId) {
        return testId.getTestName();
    }

    void print(KXmlSerializer serializer, TestIdentifier testId, TestResult testResult)
            throws IOException {

        serializer.startTag(ns, TESTCASE);
        serializer.attribute(ns, ATTR_NAME, getTestName(testId));
        serializer.attribute(ns, ATTR_CLASSNAME, testId.getClassName());
        long elapsedTimeMs = testResult.getEndTime() - testResult.getStartTime();
        serializer.attribute(ns, ATTR_TIME, Double.toString((double) elapsedTimeMs / 1000.f));

        if (!TestStatus.PASSED.equals(testResult.getStatus())) {
            String result = testResult.getStatus().equals(TestStatus.FAILURE) ? FAILURE : ERROR;
            serializer.startTag(ns, result);
            // TODO: get message of stack trace ?
//            String msg = testResult.getStackTrace();
//            if (msg != null && msg.length() > 0) {
//                serializer.attribute(ns, ATTR_MESSAGE, msg);
//            }
           // TODO: get class name of stackTrace exception
            //serializer.attribute(ns, ATTR_TYPE, testId.getClassName());
            String stackText = sanitize(testResult.getStackTrace());
            serializer.text(stackText);
            serializer.endTag(ns, result);
        }

        serializer.endTag(ns, TESTCASE);
     }

    /**
     * Returns the text in a format that is safe for use in an XML document.
     */
    private String sanitize(String text) {
        return text.replace("\0", "<\\0>");
    }
}
