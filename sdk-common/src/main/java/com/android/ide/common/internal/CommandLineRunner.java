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

package com.android.ide.common.internal;

import com.android.annotations.Nullable;
import com.android.sdklib.util.GrabProcessOutput;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.util.List;

public class CommandLineRunner {

    private final ILogger mLogger;

    private class OutputGrabber implements GrabProcessOutput.IProcessOutput {

        private boolean mFoundError = false;
        private List<String> mErrors = Lists.newArrayList();

        @Override
        public void out(@Nullable String line) {
            if (line != null) {
                mLogger.info(line);
            }
        }

        @Override
        public void err(@Nullable String line) {
            if (line != null) {
                mLogger.error(null /*throwable*/, line);
                mErrors.add(line);
                mFoundError = true;
            }
        }

        private boolean foundError() {
            return mFoundError;
        }
    }

    public CommandLineRunner(ILogger logger) {
        mLogger = logger;
    }

    public void runCmdLine(List<String> command)
            throws IOException, InterruptedException, LoggedErrorException {
        String[] cmdArray = command.toArray(new String[command.size()]);
        runCmdLine(cmdArray);
    }

    public void runCmdLine(String[] command)
            throws IOException, InterruptedException, LoggedErrorException {
        printCommand(command);

        // launch the command line process
        Process process = Runtime.getRuntime().exec(command);

        // get the output and return code from the process
        OutputGrabber grabber = new OutputGrabber();

        int returnCode = GrabProcessOutput.grabProcessOutput(
                process,
                GrabProcessOutput.Wait.WAIT_FOR_READERS, // we really want to make sure we get all the output!
                grabber);

        if (returnCode != 0) {
            throw new LoggedErrorException(
                    returnCode,
                    grabber.mErrors,
                    Joiner.on(' ').join(command));
        }
    }

    private void printCommand(String[] command) {
        mLogger.info("command: " + Joiner.on(' ').join(command));
    }
}
