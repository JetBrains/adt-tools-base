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

package com.android.builder.core;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * Concrete implementation of {@link DexOptions}.
 */
@SuppressWarnings("WeakerAccess") // Exposed in the DSL.
public class DefaultDexOptions implements DexOptions {

    private boolean preDexLibraries = true;

    private boolean jumboMode = false;

    // By default, all dexing will happen in process to get maximum feedback quickly.
    private boolean dexInProcess = true;

    private Integer threadCount = null;

    private String javaMaxHeapSize;

    private List<String> additionalParameters = Lists.newArrayList();

    private volatile Integer maxProcessCount;


    public static DefaultDexOptions copyOf(DexOptions dexOptions) {
        DefaultDexOptions result = new DefaultDexOptions();

        result.setPreDexLibraries(dexOptions.getPreDexLibraries());
        result.setJumboMode(dexOptions.getJumboMode());
        result.setDexInProcess(dexOptions.getDexInProcess());
        result.setThreadCount(dexOptions.getThreadCount());
        result.setJavaMaxHeapSize(dexOptions.getJavaMaxHeapSize());
        result.setAdditionalParameters(dexOptions.getAdditionalParameters());
        result.setMaxProcessCount(dexOptions.getMaxProcessCount());

        return result;
    }

    /** @deprecated ignored */
    @Deprecated
    public boolean getIncremental() {
        return false;
    }

    @SuppressWarnings("UnusedParameters")
    public void setIncremental(boolean ignored) {}

    /**
     * Whether to pre-dex libraries. This can improve incremental builds, but clean builds may
     * be slower.
     */
    @Override
    public boolean getPreDexLibraries() {
        return preDexLibraries;
    }

    public void setPreDexLibraries(boolean preDexLibraries) {
        this.preDexLibraries = preDexLibraries;
    }

    /**
     * Enable jumbo mode in dx (--force-jumbo).
     */
    @Override
    public boolean getJumboMode() {
        return jumboMode;
    }

    public void setJumboMode(boolean jumboMode) {
        this.jumboMode = jumboMode;
    }

    /**
     * Whether to run the {@code dx} compiler as a separate process or inside the Gradle daemon JVM.
     *
     * <p>Running {@code dx} in-process can greatly improve performance, but is still experimental.
     */
    @Override
    public boolean getDexInProcess() {
        return dexInProcess;
    }

    public void setDexInProcess(boolean dexInProcess) {
        this.dexInProcess = dexInProcess;
    }

    /**
     * Number of threads to use when running dx. Defaults to 4.
     */
    @Nullable
    @Override
    public Integer getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(Integer threadCount) {
        this.threadCount = threadCount;
    }

    /**
     * Sets the -JXmx* value when calling dx. Format should follow the 1024M pattern.
     */
    @Nullable
    @Override
    public String getJavaMaxHeapSize() {
        return javaMaxHeapSize;
    }

    public void setJavaMaxHeapSize(String javaMaxHeapSize) {
        this.javaMaxHeapSize = javaMaxHeapSize;
    }

    /**
     * List of additional parameters to be passed to {@code dx}.
     */
    @NonNull
    @Override
    public List<String> getAdditionalParameters() {
        return additionalParameters;
    }

    public void setAdditionalParameters(List<String> additionalParameters) {
        this.additionalParameters = Lists.newArrayList(additionalParameters);
    }

    /**
     * Returns the maximum number of concurrent processes that can be used to dex. Defaults to 2.
     *
     * <p>Be aware that the number of concurrent process times the memory requirement represent the
     * minimum amount of memory that will be used by the dx processes:
     * {@code Total Memory = getMaxProcessCount() * getJavaMaxHeapSize()}
     *
     * <p>To avoid trashing, keep these two settings appropriate for your configuration.
     * @return the max number of concurrent dx processes.
     */
    @Nullable
    @Override
    public Integer getMaxProcessCount() {
        return maxProcessCount;
    }

    public void setMaxProcessCount(Integer maxProcessCount) {
        this.maxProcessCount = maxProcessCount;
    }
}
