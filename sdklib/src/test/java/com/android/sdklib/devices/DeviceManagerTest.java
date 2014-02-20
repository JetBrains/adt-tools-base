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

package com.android.sdklib.devices;

import com.android.sdklib.SdkManagerTestCase;
import com.android.sdklib.devices.Device.Builder;
import com.android.sdklib.devices.DeviceManager.DeviceFilter;
import com.android.sdklib.mock.MockLog;

import java.io.File;
import java.util.EnumSet;

public class DeviceManagerTest extends SdkManagerTestCase {

    private DeviceManager dm;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        dm = createDeviceManager();
    }

    private DeviceManager createDeviceManager() {
        MockLog log = super.getLog();
        File sdkLocation = getSdkManager().getLocalSdk().getLocation();
        return DeviceManager.createInstance(sdkLocation, log);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    public final void testGetDevices_Default() {
        // no user devices defined in the test's custom .android home folder
        assertEquals("[]", dm.getDevices(EnumSet.of(DeviceFilter.USER)).toString());

        // this list comes from devices.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/devices.xml
        assertEquals(
                "[2.7\" QVGA, 2.7\" QVGA slider, 3.2\" HVGA slider (ADP1), 3.2\" QVGA (ADP2), " +
                 "3.3\" WQVGA, 3.4in WQVGA, 3.7\" WVGA (Nexus One), 3.7\" FWVGA slider, " +
                 "4\" WVGA (Nexus S), 4.65\" 720p (Galaxy Nexus), 4.7\" WXGA, 5.1\" WVGA, " +
                 "5.4\" FWVGA, 7\" WSVGA (Tablet), 10.1\" WXGA (Tablet)]",
                dm.getDevices(EnumSet.of(DeviceFilter.DEFAULT)).toString());


        // this list comes from the nexus.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/nexus.xml
        assertEquals(
                "[Nexus One, Nexus S, Galaxy Nexus, Nexus 7 (2012), " +
                 "Nexus 4, Nexus 10, Nexus 7, Nexus 5]",
                dm.getDevices(EnumSet.of(DeviceFilter.VENDOR)).toString());

        assertEquals(
                "[2.7\" QVGA, 2.7\" QVGA slider, 3.2\" HVGA slider (ADP1), 3.2\" QVGA (ADP2), " +
                 "3.3\" WQVGA, 3.4in WQVGA, 3.7\" WVGA (Nexus One), 3.7\" FWVGA slider, " +
                 "4\" WVGA (Nexus S), 4.65\" 720p (Galaxy Nexus), 4.7\" WXGA, 5.1\" WVGA, " +
                 "5.4\" FWVGA, 7\" WSVGA (Tablet), 10.1\" WXGA (Tablet), " +
                 "Nexus One, Nexus S, Galaxy Nexus, Nexus 7 (2012), " +
                 "Nexus 4, Nexus 10, Nexus 7, Nexus 5]",
                dm.getDevices(DeviceManager.ALL_DEVICES).toString());
    }

    public final void testGetDevice() {
        // get a definition from the bundled devices.xml file
        Device d1 = dm.getDevice("7in WSVGA (Tablet)", "Generic");
        assertEquals("7\" WSVGA (Tablet)", d1.toString());

        // get a definition from the bundled nexus.xml file
        Device d2 = dm.getDevice("Nexus One", "Google");
        assertEquals("Nexus One", d2.toString());
    }

    public final void testGetDevices_UserDevice() {

        Device d1 = dm.getDevice("7in WSVGA (Tablet)", "Generic");

        Builder b = new Device.Builder(d1);
        b.setId("MyCustomTablet");
        b.setName("My Custom Tablet");
        b.setManufacturer("OEM");

        Device d2 = b.build();

        dm.addUserDevice(d2);
        dm.saveUserDevices();

        assertEquals("My Custom Tablet", dm.getDevice("MyCustomTablet", "OEM").toString());

        // create a new device manager, forcing it reload all files
        DeviceManager dm2 = createDeviceManager();

        assertEquals("My Custom Tablet", dm.getDevice("MyCustomTablet", "OEM").toString());

        // no user devices defined in the test's custom .android home folder
        assertEquals("[My Custom Tablet]", dm.getDevices(EnumSet.of(DeviceFilter.USER)).toString());

        // this list comes from devices.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/devices.xml
        assertEquals(
                "[2.7\" QVGA, 2.7\" QVGA slider, 3.2\" HVGA slider (ADP1), 3.2\" QVGA (ADP2), " +
                 "3.3\" WQVGA, 3.4in WQVGA, 3.7\" WVGA (Nexus One), 3.7\" FWVGA slider, " +
                 "4\" WVGA (Nexus S), 4.65\" 720p (Galaxy Nexus), 4.7\" WXGA, 5.1\" WVGA, " +
                 "5.4\" FWVGA, 7\" WSVGA (Tablet), 10.1\" WXGA (Tablet)]",
                dm.getDevices(EnumSet.of(DeviceFilter.DEFAULT)).toString());


        // this list comes from the nexus.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/nexus.xml
        assertEquals(
                "[Nexus One, Nexus S, Galaxy Nexus, Nexus 7 (2012), " +
                 "Nexus 4, Nexus 10, Nexus 7, Nexus 5]",
                dm.getDevices(EnumSet.of(DeviceFilter.VENDOR)).toString());

        assertEquals(
                "[My Custom Tablet, " +
                 "2.7\" QVGA, 2.7\" QVGA slider, 3.2\" HVGA slider (ADP1), 3.2\" QVGA (ADP2), " +
                 "3.3\" WQVGA, 3.4in WQVGA, 3.7\" WVGA (Nexus One), 3.7\" FWVGA slider, " +
                 "4\" WVGA (Nexus S), 4.65\" 720p (Galaxy Nexus), 4.7\" WXGA, 5.1\" WVGA, " +
                 "5.4\" FWVGA, 7\" WSVGA (Tablet), 10.1\" WXGA (Tablet), " +
                 "Nexus One, Nexus S, Galaxy Nexus, Nexus 7 (2012), " +
                 "Nexus 4, Nexus 10, Nexus 7, Nexus 5]",
                dm.getDevices(DeviceManager.ALL_DEVICES).toString());
    }

}
