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

import com.android.ddmlib.PropertyFetcher.GetPropReceiver;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class PropertyFetcherTest {

    final static String GETPROP_RESPONSE =
            "[ro.sf.lcd_density]: [480]\r\n" +
            "[ro.secure]: [1]\r\n";

    /**
     * Simple test to ensure parsing result of 'shell getprop' works as expected
     */
    @Test
    public void testGetPropReceiver() {
        GetPropReceiver receiver = new GetPropReceiver();
        byte[] byteData = GETPROP_RESPONSE.getBytes();
        receiver.addOutput(byteData, 0, byteData.length);
        Assert.assertEquals("480", receiver.getCollectedProperties().get("ro.sf.lcd_density"));
    }

    /**
     * Test that getProperty works as expected when queries made in different states
     * @throws Exception
     */
    @Test
    public void getProperty() throws Exception {
        IDevice mockDevice = EasyMock.createMock(IDevice.class);
        DeviceTest.injectShellResponse(mockDevice, GETPROP_RESPONSE);
        EasyMock.replay(mockDevice);

        PropertyFetcher fetcher = new PropertyFetcher(mockDevice);
        // do query in unpopulated state
        Future<String> unpopulatedFuture = fetcher.getProperty("ro.sf.lcd_density");
        // do query in fetching state
        Future<String> fetchingFuture = fetcher.getProperty("ro.secure");

        Assert.assertEquals("480", unpopulatedFuture.get());
        // do queries with short timeout to ensure props already available
        Assert.assertEquals("1", fetchingFuture.get(1, TimeUnit.MILLISECONDS));
        Assert.assertEquals("480", fetcher.getProperty("ro.sf.lcd_density").get(1,
                TimeUnit.MILLISECONDS));
    }

    /**
     * Test that getProperty always does a getprop query when requested prop is not
     * read only aka volatile
     *
     * @throws Exception
     */
    @Test
    public void getProperty_volatile() throws Exception {

        IDevice mockDevice = EasyMock.createMock(IDevice.class);
        DeviceTest.injectShellResponse(mockDevice, "[dev.bootcomplete]: [0]\r\n");
        DeviceTest.injectShellResponse(mockDevice, "[dev.bootcomplete]: [1]\r\n");
        EasyMock.replay(mockDevice);

        PropertyFetcher fetcher = new PropertyFetcher(mockDevice);
        Assert.assertEquals("0", fetcher.getProperty("dev.bootcomplete").get());
        Assert.assertEquals("1", fetcher.getProperty("dev.bootcomplete").get());
    }

    /**
     * Test that getProperty returns when the 'shell getprop' command response is invalid
     *
     * @throws Exception
     */
    @Test
    public void getProperty_badResponse() throws Exception {
        IDevice mockDevice = EasyMock.createMock(IDevice.class);
        DeviceTest.injectShellResponse(mockDevice, "blargh");
        EasyMock.replay(mockDevice);

        PropertyFetcher fetcher = new PropertyFetcher(mockDevice);
        Assert.assertNull(fetcher.getProperty("dev.bootcomplete").get());
    }

    /**
     * Test that null is returned when querying an unknown property
     * @throws Exception
     */
    @Test
    public void getProperty_unknown() throws Exception {
        IDevice mockDevice = EasyMock.createMock(IDevice.class);
        DeviceTest.injectShellResponse(mockDevice, GETPROP_RESPONSE);
        EasyMock.replay(mockDevice);

        PropertyFetcher fetcher = new PropertyFetcher(mockDevice);
        Assert.assertNull(fetcher.getProperty("unknown").get());
    }
}
