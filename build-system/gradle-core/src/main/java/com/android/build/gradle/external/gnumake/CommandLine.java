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
package com.android.build.gradle.external.gnumake;


import com.google.common.base.Joiner;

import java.util.List;

/**
 * A shell command with n arguments.
 */
class CommandLine {
    public final String executable;
    public final List<String> args;

    CommandLine(String executable, List<String> args) {
        this.executable = executable;
        this.args = args;
    }

    @Override
    public boolean equals(Object obj) {
        CommandLine other = (CommandLine) obj;
        return executable.equals(other.executable) && args.equals(other.args);
    }

    @Override
    public String toString() {
        return executable + " " + Joiner.on(' ').join(args);
    }
}
