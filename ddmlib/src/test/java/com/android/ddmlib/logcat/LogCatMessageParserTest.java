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
package com.android.ddmlib.logcat;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log.LogLevel;

import junit.framework.TestCase;
import org.easymock.EasyMock;

import java.util.List;

import static com.android.ddmlib.IDevice.*;

/**
 * Unit tests for {@link LogCatMessageParser}.
 */
public final class LogCatMessageParserTest extends TestCase {
    private List<LogCatMessage> mParsedMessages;

    /** A list of messages generated with the following code:
     * <pre>
     * {@code
     * Log.d("dtag", "debug message");
     * Log.e("etag", "error message");
     * Log.i("itag", "info message");
     * Log.v("vtag", "verbose message");
     * Log.w("wtag", "warning message");
     * Log.wtf("wtftag", "wtf message");
     * Log.d("dtag", "debug message");
     * }
     *  </pre>
     *  Note: On Android 2.3, Log.wtf doesn't really generate the message.
     *  It only produces the message header, but swallows the message tag.
     *  This string has been modified to include the message.
     */
    private static final String[] MESSAGES = new String[] {
            "[ 08-11 19:11:07.132   495:0x1ef D/dtag     ]", //$NON-NLS-1$
            "debug message",                                 //$NON-NLS-1$
            "[ 08-11 19:11:07.132   495:  234 E/etag     ]", //$NON-NLS-1$
            "error message",                                 //$NON-NLS-1$
            "[ 08-11 19:11:07.132   495:0x1ef I/itag     ]", //$NON-NLS-1$
            "info message",                                  //$NON-NLS-1$
            "[ 08-11 19:11:07.132   495:0x1ef V/vtag     ]", //$NON-NLS-1$
            "verbose message",                               //$NON-NLS-1$
            "[ 08-11 19:11:07.132   495:0x1ef W/wtag     ]", //$NON-NLS-1$
            "warning message",                               //$NON-NLS-1$
            "[ 08-11 19:11:07.132   495:0x1ef F/wtftag   ]", //$NON-NLS-1$
            "wtf message",                                   //$NON-NLS-1$
            "[ 08-11 21:15:35.754   540:0x21c D/dtag     ]", //$NON-NLS-1$
            "debug message",                                 //$NON-NLS-1$
    };

    @Override
    protected void setUp() throws Exception {
        LogCatMessageParser parser = new LogCatMessageParser();

        IDevice d = EasyMock.createMock(IDevice.class);
        EasyMock.expect(d.getClientName(495)).andStubReturn("com.example.name");
        EasyMock.expect(d.getClientName(EasyMock.anyInt())).andStubReturn("");
        EasyMock.replay(d);

        mParsedMessages = parser.processLogLines(MESSAGES, d);
    }

    /** Check that the correct number of messages are received. */
    public void testMessageCount() {
        assertEquals(7, mParsedMessages.size());
    }

    /** Check the log level in a few of the parsed messages. */
    public void testLogLevel() {
        assertEquals(mParsedMessages.get(0).getLogLevel(), LogLevel.DEBUG);
        assertEquals(mParsedMessages.get(5).getLogLevel(), LogLevel.ASSERT);
    }

    /** Check the parsed tag. */
    public void testTag() {
        assertEquals(mParsedMessages.get(1).getTag(), "etag");
    }

    /** Check the time field. */
    public void testTime() {
        assertEquals(mParsedMessages.get(6).getTimestamp().toString(), "08-11 21:15:35.754");
    }

    /** Check the message field. */
    public void testMessage() {
        assertEquals(mParsedMessages.get(2).getMessage(), MESSAGES[5]);
    }

    public void testTid() {
        assertEquals(mParsedMessages.get(0).getTid(), 0x1ef);
        assertEquals(mParsedMessages.get(1).getTid(), 234);
    }

    public void testTimeAsDate() {
        LogCatHeader header = mParsedMessages.get(0).getHeader();
        // Test date against "08-11 19:11:07.132"
        assertEquals(header.getTimestamp().toString(), "08-11 19:11:07.132");
    }

    public void testPackageName() {
        assertEquals(mParsedMessages.get(0).getAppName(), "com.example.name");
        assertEquals(mParsedMessages.get(6).getAppName(), "?");
    }
}
