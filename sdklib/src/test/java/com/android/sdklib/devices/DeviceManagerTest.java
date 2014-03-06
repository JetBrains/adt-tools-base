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
import com.android.sdklib.devices.DeviceManager.DeviceStatus;
import com.android.sdklib.mock.MockLog;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DeviceManagerTest extends SdkManagerTestCase {

    private DeviceManager dm;
    private MockLog log;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        dm = createDeviceManager();
    }

    private DeviceManager createDeviceManager() {
        log = super.getLog();
        File sdkLocation = getSdkManager().getLocalSdk().getLocation();
        return DeviceManager.createInstance(sdkLocation, log);
    }

    /** Returns a list of just the devices' display names, for unit test comparisons. */
    private static List<String> listDisplayNames(List<Device> devices) {
        if (devices == null) return null;
        List<String> names = new ArrayList<String>();
        for (Device d : devices) {
            names.add(d.getDisplayName());
        }
        return names;
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    public final void testGetDevices_Default() {
        // no user devices defined in the test's custom .android home folder
        assertEquals("[]", dm.getDevices(DeviceFilter.USER).toString());
        assertEquals("", log.toString());

        // no system-images devices defined in the SDK by default
        assertEquals("[]", dm.getDevices(DeviceFilter.SYSTEM_IMAGES).toString());
        assertEquals("", log.toString());

        // this list comes from devices.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/devices.xml
        assertEquals(
                "[2.7\" QVGA, 2.7\" QVGA slider, 3.2\" HVGA slider (ADP1), 3.2\" QVGA (ADP2), " +
                 "3.3\" WQVGA, 3.4in WQVGA, 3.7\" WVGA (Nexus One), 3.7\" FWVGA slider, " +
                 "4\" WVGA (Nexus S), 4.65\" 720p (Galaxy Nexus), 4.7\" WXGA, 5.1\" WVGA, " +
                 "5.4\" FWVGA, 7\" WSVGA (Tablet), 10.1\" WXGA (Tablet)]",
                 listDisplayNames(dm.getDevices(DeviceFilter.DEFAULT)).toString());
        assertEquals("", log.toString());

        // this list comes from the nexus.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/nexus.xml
        assertEquals(
                "[Nexus One, Nexus S, Galaxy Nexus, Nexus 7 (2012), " +
                 "Nexus 4, Nexus 10, Nexus 7, Nexus 5]",
                 listDisplayNames(dm.getDevices(DeviceFilter.VENDOR)).toString());
        assertEquals("", log.toString());

        assertEquals(
                "[2.7\" QVGA, 2.7\" QVGA slider, 3.2\" HVGA slider (ADP1), 3.2\" QVGA (ADP2), " +
                 "3.3\" WQVGA, 3.4in WQVGA, 3.7\" WVGA (Nexus One), 3.7\" FWVGA slider, " +
                 "4\" WVGA (Nexus S), 4.65\" 720p (Galaxy Nexus), 4.7\" WXGA, 5.1\" WVGA, " +
                 "5.4\" FWVGA, 7\" WSVGA (Tablet), 10.1\" WXGA (Tablet), " +
                 "Nexus One, Nexus S, Galaxy Nexus, Nexus 7 (2012), " +
                 "Nexus 4, Nexus 10, Nexus 7, Nexus 5]",
                 listDisplayNames(dm.getDevices(DeviceManager.ALL_DEVICES)).toString());
        assertEquals("", log.toString());
    }

    public final void testGetDevice() {
        // get a definition from the bundled devices.xml file
        Device d1 = dm.getDevice("7in WSVGA (Tablet)", "Generic");
        assertEquals("7\" WSVGA (Tablet)", d1.getDisplayName());
        assertEquals("", log.toString());

        // get a definition from the bundled nexus.xml file
        Device d2 = dm.getDevice("Nexus One", "Google");
        assertEquals("Nexus One", d2.getDisplayName());
        assertEquals("", log.toString());
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

        assertEquals("My Custom Tablet", dm.getDevice("MyCustomTablet", "OEM").getDisplayName());
        assertEquals("", log.toString());

        // create a new device manager, forcing it reload all files
        dm = null;
        DeviceManager dm2 = createDeviceManager();

        assertEquals("My Custom Tablet", dm2.getDevice("MyCustomTablet", "OEM").getDisplayName());
        assertEquals("", log.toString());

        // 1 user device defined in the test's custom .android home folder
        assertEquals("[My Custom Tablet]",
                     listDisplayNames(dm2.getDevices(DeviceFilter.USER)).toString());
        assertEquals("", log.toString());

        // no system-images devices defined in the SDK by default
        assertEquals("[]",
                     listDisplayNames(dm2.getDevices(DeviceFilter.SYSTEM_IMAGES)).toString());
        assertEquals("", log.toString());

        // this list comes from devices.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/devices.xml
        assertEquals(
                "[2.7\" QVGA, 2.7\" QVGA slider, 3.2\" HVGA slider (ADP1), 3.2\" QVGA (ADP2), " +
                 "3.3\" WQVGA, 3.4in WQVGA, 3.7\" WVGA (Nexus One), 3.7\" FWVGA slider, " +
                 "4\" WVGA (Nexus S), 4.65\" 720p (Galaxy Nexus), 4.7\" WXGA, 5.1\" WVGA, " +
                 "5.4\" FWVGA, 7\" WSVGA (Tablet), 10.1\" WXGA (Tablet)]",
                 listDisplayNames(dm2.getDevices(DeviceFilter.DEFAULT)).toString());
        assertEquals("", log.toString());

        // this list comes from the nexus.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/nexus.xml
        assertEquals(
                "[Nexus One, Nexus S, Galaxy Nexus, Nexus 7 (2012), " +
                 "Nexus 4, Nexus 10, Nexus 7, Nexus 5]",
                 listDisplayNames(dm2.getDevices(DeviceFilter.VENDOR)).toString());
        assertEquals("", log.toString());

        assertEquals(
                "[My Custom Tablet, " +
                 "2.7\" QVGA, 2.7\" QVGA slider, 3.2\" HVGA slider (ADP1), 3.2\" QVGA (ADP2), " +
                 "3.3\" WQVGA, 3.4in WQVGA, 3.7\" WVGA (Nexus One), 3.7\" FWVGA slider, " +
                 "4\" WVGA (Nexus S), 4.65\" 720p (Galaxy Nexus), 4.7\" WXGA, 5.1\" WVGA, " +
                 "5.4\" FWVGA, 7\" WSVGA (Tablet), 10.1\" WXGA (Tablet), " +
                 "Nexus One, Nexus S, Galaxy Nexus, Nexus 7 (2012), " +
                 "Nexus 4, Nexus 10, Nexus 7, Nexus 5]",
                 listDisplayNames(dm2.getDevices(DeviceManager.ALL_DEVICES)).toString());
        assertEquals("", log.toString());
    }

    public final void testGetDevices_SysImgDevice() throws Exception {
        // this adds a devices.xml with one device
        makeSystemImageFolder(TARGET_DIR_NAME_0, "tag-1", "x86");

        // no user devices defined in the test's custom .android home folder
        assertEquals("[]", listDisplayNames(dm.getDevices(DeviceFilter.USER)).toString());
        assertEquals("", log.toString());

        // no system-images devices defined in the SDK by default
        assertEquals("[Mock Tag 1 Device Name]",
                listDisplayNames(dm.getDevices(DeviceFilter.SYSTEM_IMAGES)).toString());
        assertEquals("", log.toString());

        // this list comes from devices.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/devices.xml
        assertEquals(
                "[2.7\" QVGA, 2.7\" QVGA slider, 3.2\" HVGA slider (ADP1), 3.2\" QVGA (ADP2), " +
                 "3.3\" WQVGA, 3.4in WQVGA, 3.7\" WVGA (Nexus One), 3.7\" FWVGA slider, " +
                 "4\" WVGA (Nexus S), 4.65\" 720p (Galaxy Nexus), 4.7\" WXGA, 5.1\" WVGA, " +
                 "5.4\" FWVGA, 7\" WSVGA (Tablet), 10.1\" WXGA (Tablet)]",
                 listDisplayNames(dm.getDevices(DeviceFilter.DEFAULT)).toString());
        assertEquals("", log.toString());

        // this list comes from the nexus.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/nexus.xml
        assertEquals(
                "[Nexus One, Nexus S, Galaxy Nexus, Nexus 7 (2012), " +
                 "Nexus 4, Nexus 10, Nexus 7, Nexus 5]",
                 listDisplayNames(dm.getDevices(DeviceFilter.VENDOR)).toString());
        assertEquals("", log.toString());

        assertEquals(
                "[2.7\" QVGA, 2.7\" QVGA slider, 3.2\" HVGA slider (ADP1), 3.2\" QVGA (ADP2), " +
                 "3.3\" WQVGA, 3.4in WQVGA, 3.7\" WVGA (Nexus One), 3.7\" FWVGA slider, " +
                 "4\" WVGA (Nexus S), 4.65\" 720p (Galaxy Nexus), 4.7\" WXGA, 5.1\" WVGA, " +
                 "5.4\" FWVGA, 7\" WSVGA (Tablet), 10.1\" WXGA (Tablet), " +
                 "Nexus One, Nexus S, Galaxy Nexus, Nexus 7 (2012), " +
                 "Nexus 4, Nexus 10, Nexus 7, Nexus 5, " +
                 "Mock Tag 1 Device Name]",
                 listDisplayNames(dm.getDevices(DeviceManager.ALL_DEVICES)).toString());
        assertEquals("", log.toString());
    }

    public final void testGetDeviceStatus() {
        // get a definition from the bundled devices.xml file
        // Note: the recorded hash code of this device should not change in future implementations
        // otherwise the AVD Manager will list existing AVDs as having changed.
        assertEquals(DeviceStatus.EXISTS,
                     dm.getDeviceStatus("7in WSVGA (Tablet)", "Generic", -1338842870));

        // same device but with an invalid hash code
        assertEquals(DeviceStatus.CHANGED,
                     dm.getDeviceStatus("7in WSVGA (Tablet)", "Generic", 1));


        // get a definition from the bundled oem file with its canonical hash code
        assertEquals(DeviceStatus.EXISTS,
                     dm.getDeviceStatus("Nexus One", "Google", -1812631727));

        assertEquals(DeviceStatus.CHANGED,
                     dm.getDeviceStatus("Nexus One", "Google", 2));

        // try a device that does not exist
        assertEquals(DeviceStatus.MISSING,
                     dm.getDeviceStatus("My Device", "Custom OEM", 3));
    }
}
