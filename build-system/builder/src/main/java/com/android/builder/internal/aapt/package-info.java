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

/**
 * The {@code aapt} package contains support for invoking the {@code aapt} tool. The package is
 * organized with a top-level package (the {@code aapt} package) that contains the common interface
 * for {@code aapt} as well as common implementation parts.
 *
 * <p>Individual {@code aapt} implementations are provided as sub-packages. Package {@code v1}
 * contains support for the original {@code aapt} tool.
 *
 * <p>Using the package requires instantiating an
 * {@link com.android.builder.internal.aapt.Aapt}
 * using one of the implementations that exist in the sub-packages.
 */
package com.android.builder.internal.aapt;