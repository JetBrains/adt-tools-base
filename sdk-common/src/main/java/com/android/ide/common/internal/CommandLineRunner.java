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

import java.io.IOException;
import java.util.List;

public class CommandLineRunner {

    private final ILogger mLogger;

    private class OutputGrabber implements GrabProcessOutput.IProcessOutput {

        private boolean mFoundError = false;

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

    public void runCmdLine(List<String> command) throws IOException, InterruptedException {
        String[] cmdArray = command.toArray(new String[command.size()]);
        runCmdLine(cmdArray);
    }

    public void runCmdLine(String[] command) throws IOException, InterruptedException {
        printCommand(command);

        // launch the command line process
        Process process = Runtime.getRuntime().exec(command);

        // get the output and return code from the process
        if (grabProcessOutput(process) != 0) {
            throw new RuntimeException(String.format("Running %s failed. See output", command[0]));
        }
    }

    /**
     * Get the stderr output of a process and return when the process is done.
     * @param process The process to get the output from
     * @return the process return code.
     * @throws InterruptedException
     */
    private int grabProcessOutput(final Process process) throws InterruptedException {

        OutputGrabber grabber = new OutputGrabber();

        return GrabProcessOutput.grabProcessOutput(
                process,
                GrabProcessOutput.Wait.WAIT_FOR_READERS, // we really want to make sure we get all the output!
                grabber);
    }

    private void printCommand(String[] command) {
        mLogger.info("command: " + Joiner.on(' ').join(command));
    }
}
