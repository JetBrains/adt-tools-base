/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.annotations.NonNull;

import java.io.File;

/**
 * Stub PNG cruncher to use when not actually crunching PNG. Throws if actually used.
 */
public class NopPngCruncher implements PngCruncher {

  @Override
  public int start() {
    return 0;
  }

  @Override
  public void crunchPng(int key, @NonNull File from, @NonNull File to) throws PngException {
    throw new PngException("unexpected use of NopPngCruncher");
  }

  @Override
  public void end(int key) throws InterruptedException {

  }
}
