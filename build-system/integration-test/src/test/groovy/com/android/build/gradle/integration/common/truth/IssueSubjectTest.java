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

package com.android.build.gradle.integration.common.truth;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.builder.model.SyncIssue;

import org.junit.Test;

public class IssueSubjectTest {

    private static final class FakeIssue implements SyncIssue {

        private int severity;
        private int type;
        private String data;
        private String message;

        public FakeIssue(int severity, int type) {
            this(severity, type, "", "");
        }

        public FakeIssue(int severity, int type, String data) {
            this(severity, type, data, "");
        }

        public FakeIssue(int severity, int type, String data, String message) {
            this.severity = severity;
            this.type = type;
            this.data = data;
            this.message = message;
        }

        @Override
        public int getType() {
            return type;
        }

        @Override
        public int getSeverity() {
            return severity;
        }

        @NonNull
        @Override
        public String getData() {
            return data;
        }

        @NonNull
        @Override
        public String getMessage() {
            return message;
        }
    }

    @Test
    public void badSeverity() {

        FakeFailureStrategy failure = new FakeFailureStrategy();
        SyncIssue issue = new FakeIssue(1, 2);

        IssueSubject subject = new IssueSubject(failure, issue);
        subject.hasSeverity(0);

        assertThat(failure.message).is("Not true that <1|2|> has severity <0>. It is <1>");
    }

    @Test
    public void badType() {

        FakeFailureStrategy failure = new FakeFailureStrategy();
        SyncIssue issue = new FakeIssue(1, 2);

        IssueSubject subject = new IssueSubject(failure, issue);
        subject.hasType(0);

        assertThat(failure.message).is("Not true that <1|2|> has type <0>. It is <2>");
    }

    @Test
    public void badData() {

        FakeFailureStrategy failure = new FakeFailureStrategy();
        SyncIssue issue = new FakeIssue(1, 2, "foo");

        IssueSubject subject = new IssueSubject(failure, issue);
        subject.hasData("bar");

        assertThat(failure.message).is("Not true that <1|2|foo> has data <bar>. It is <foo>");
    }

    @Test
    public void badMessage() {

        FakeFailureStrategy failure = new FakeFailureStrategy();
        SyncIssue issue = new FakeIssue(1, 2, "foo", "bob");

        IssueSubject subject = new IssueSubject(failure, issue);
        subject.hasMessage("robert");

        assertThat(failure.message).is("Not true that <1|2|foo> has message <robert>. It is <bob>");
    }

}
