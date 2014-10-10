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

package com.android.builder.internal;

import com.android.annotations.NonNull;
import com.android.builder.png.NinePatchException;
import com.android.builder.png.PngProcessor;
import com.android.ide.common.internal.LoggedErrorException;
import com.android.ide.common.internal.PngCruncher;

import java.io.File;
import java.io.IOException;

/**
 * Implementation of the PngCruncher using Java code
 * only (intead of calling out to aapt).
 */
public class JavaPngCruncher implements PngCruncher {

    @Override
    public void crunchPng(@NonNull File from, @NonNull File to)
            throws InterruptedException, LoggedErrorException, IOException {
        try {
            PngProcessor.process(from, to);
        } catch (NinePatchException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void end() throws InterruptedException {
        // nothing to do, it's all synchronous.
    }
}
