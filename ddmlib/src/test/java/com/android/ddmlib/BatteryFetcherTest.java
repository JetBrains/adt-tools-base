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
package com.android.ddmlib;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class BatteryFetcherTest {

    /**
     * Test that getBattery works as expected when queries made in different states.
     */
    @Test
    public void getBattery() throws Exception {
        IDevice mockDevice = EasyMock.createMock(IDevice.class);
        DeviceTest.injectShellResponse(mockDevice, "20\r\n");
        EasyMock.replay(mockDevice);

        BatteryFetcher fetcher = new BatteryFetcher(mockDevice);
        // do query in unpopulated state
        Future<Integer> uncachedFuture = fetcher.getBattery(0, TimeUnit.MILLISECONDS);
        // do query in fetching state
        Future<Integer> fetchingFuture = fetcher.getBattery(0, TimeUnit.MILLISECONDS);
        // do query in fetching state

        Assert.assertEquals(20, uncachedFuture.get().intValue());
        // do queries with short timeout to ensure battery already available
        Assert.assertEquals(20, fetchingFuture.get(1, TimeUnit.MILLISECONDS).intValue());
        Assert.assertEquals(20,
                fetcher.getBattery(1, TimeUnit.SECONDS).get(1, TimeUnit.MILLISECONDS).intValue());
    }

    /**
     * Test that getBattery returns exception when battery checks return invalid data.
     */
    @Test(expected=ExecutionException.class)
    public void getBattery_badResponse() throws Exception {
        IDevice mockDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(mockDevice.getSerialNumber()).andStubReturn("serial");
        DeviceTest.injectShellResponse(mockDevice, "blargh");
        DeviceTest.injectShellResponse(mockDevice, "blargh");
        EasyMock.replay(mockDevice);

        BatteryFetcher fetcher = new BatteryFetcher(mockDevice);
        try {
            fetcher.getBattery(0, TimeUnit.MILLISECONDS).get();
        } catch (ExecutionException e) {
            // expected
            Assert.assertTrue(e.getCause() instanceof IOException);
            throw e;
        }
    }

    /**
     * Test that getBattery propagates executeShell exceptions.
     */
    @Test(expected=ExecutionException.class)
    public void getBattery_shellException() throws Exception {
        IDevice mockDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(mockDevice.getSerialNumber()).andStubReturn("serial");
        mockDevice.executeShellCommand(EasyMock.<String>anyObject(),
                EasyMock.<IShellOutputReceiver>anyObject(),
                EasyMock.anyLong(), EasyMock.<TimeUnit>anyObject());
        EasyMock.expectLastCall().andThrow(new ShellCommandUnresponsiveException());
        EasyMock.replay(mockDevice);

        BatteryFetcher fetcher = new BatteryFetcher(mockDevice);
        try {
            fetcher.getBattery(0, TimeUnit.MILLISECONDS).get();
        } catch (ExecutionException e) {
            // expected
            Assert.assertTrue(e.getCause() instanceof ShellCommandUnresponsiveException);
            throw e;
        }
    }
}
