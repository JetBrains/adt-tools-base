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

package com.android.builder.testing;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.ddmlib.IDevice;
import com.android.utils.ILogger;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(MockitoJUnitRunner.class)
public class ConnectedDeviceTest {

    @Mock
    public IDevice mIDevice;

    @Mock
    public ILogger mLogger;

    private ConnectedDevice mDevice;

    @Before
    public void createDevice() {
        when(mIDevice.getSystemProperty(anyString())).thenReturn(futureOf( null));
        mDevice = new ConnectedDevice(mIDevice, mLogger,  10000, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testGetAbisForLAndAbove() {
        when(mIDevice.getSystemProperty(IDevice.PROP_DEVICE_CPU_ABI_LIST))
                .thenReturn(futureOf("x86,x86_64"));
        assertThat(mDevice.getAbis()).containsExactly("x86", "x86_64");
    }


    @Test
    public void testGetSingleAbiForPreL() {
        when(mIDevice.getSystemProperty(IDevice.PROP_DEVICE_CPU_ABI)).thenReturn(futureOf("x86"));
        assertThat(mDevice.getAbis()).containsExactly("x86");
    }

    @Test
    public void testGetAbisForPreL() {
        when(mIDevice.getSystemProperty(IDevice.PROP_DEVICE_CPU_ABI)).thenReturn(futureOf("x86"));
        when(mIDevice.getSystemProperty(IDevice.PROP_DEVICE_CPU_ABI2))
                .thenReturn(futureOf("x86_64"));
        assertThat(mDevice.getAbis()).containsExactly("x86", "x86_64");
    }

    @Test
    public void testGetDensityFromDevice() {
        when(mIDevice.getSystemProperty(IDevice.PROP_DEVICE_DENSITY)).thenReturn(futureOf("480"));
        assertThat(mDevice.getDensity()).isEqualTo(480);
    }

    @Test
    public void testGetDensityFromEmulator() {
        when(mIDevice.getSystemProperty(IDevice.PROP_DEVICE_EMULATOR_DENSITY))
                .thenReturn(futureOf("380"));
        assertThat(mDevice.getDensity()).isEqualTo(380);
    }

    @Test
    public void testGetDensityTimeout() {
        when(mIDevice.getSystemProperty(IDevice.PROP_DEVICE_DENSITY)).thenReturn(TIMEOUT_FUTURE);
        assertThat(mDevice.getDensity()).isEqualTo(-1);
    }


    @Test
    public void testGetDensityInfiniteTimeout() {
        ConnectedDevice device = new ConnectedDevice(mIDevice, mLogger, 0, TimeUnit.MILLISECONDS);
        when(mIDevice.getSystemProperty(IDevice.PROP_DEVICE_DENSITY))
                .thenReturn(noTimeoutFutureOf("480"));
        assertThat(device.getDensity()).isEqualTo(480);
    }


    private abstract static class TestFuture implements Future<String> {

        boolean isDone = false;

        @Override
        public final boolean isDone() {
            return isDone;
        }

        @Override
        public final boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public final boolean isCancelled() {
            return false;
        }

        @Override
        public String get() throws InterruptedException, ExecutionException {
            throw new AssertionError("Should not call get().");
        }

        @Override
        public String get(long timeout, @NonNull TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            throw new AssertionError("Should not call get(long, TimeUnit).");
        }

    }

    private static TestFuture futureOf(final String value) {
        return new TestFuture() {
            @Override
            public final String get(long timeout, @NonNull TimeUnit unit)
                    throws InterruptedException, ExecutionException, TimeoutException {
                isDone = true;
                return value;
            }
        };
    }

    private static TestFuture noTimeoutFutureOf(final String value) {
        return new TestFuture() {
            @Override
            public String get() throws InterruptedException, ExecutionException {
                isDone = true;
                return value;
            }
        };
    }

    private static final Future<String> TIMEOUT_FUTURE = new TestFuture() {
        @Override
        public final String get(long timeout, @NonNull TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            isDone = true;
            throw new TimeoutException("Future expected to time out");
        }
    };





}
