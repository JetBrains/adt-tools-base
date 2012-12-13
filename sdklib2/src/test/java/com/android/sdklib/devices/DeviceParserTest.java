/*
 * Copyright (C) 2012 The Android Open Source Project
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

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import org.xml.sax.SAXParseException;

import com.android.dvlib.DeviceSchemaTest;
import com.android.resources.Density;
import com.android.resources.Keyboard;
import com.android.resources.KeyboardState;
import com.android.resources.Navigation;
import com.android.resources.NavigationState;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenRatio;
import com.android.resources.ScreenSize;
import com.android.resources.TouchScreen;
import com.android.sdklib.devices.Storage.Unit;

public class DeviceParserTest extends TestCase {

    public void testValidDevices() throws Exception {
        InputStream devicesFile = DeviceSchemaTest.class.getResourceAsStream("devices_minimal.xml");
        List<Device> devices = DeviceParser.parse(devicesFile);
        assertEquals("Parsing devices_minimal.xml produces the wrong number of devices", 1,
                devices.size());

        Device device = devices.get(0);
        assertEquals("Galaxy Nexus", device.getName());
        assertEquals("Samsung", device.getManufacturer());

        // Test Meta information
        Meta meta = device.getMeta();
        assertFalse(meta.hasIconSixtyFour());
        assertFalse(meta.hasIconSixteen());
        assertFalse(meta.hasFrame());

        // Test Hardware information
        Hardware hw = device.getDefaultHardware();
        Screen screen = hw.getScreen();
        assertEquals(screen.getSize(), ScreenSize.NORMAL);
        assertEquals(4.65, screen.getDiagonalLength());
        assertEquals(Density.XHIGH, screen.getPixelDensity());
        assertEquals(ScreenRatio.LONG, screen.getRatio());
        assertEquals(720, screen.getXDimension());
        assertEquals(1280, screen.getYDimension());
        assertEquals(316.0, screen.getXdpi());
        assertEquals(316.0, screen.getYdpi());
        assertEquals(Multitouch.JAZZ_HANDS, screen.getMultitouch());
        assertEquals(TouchScreen.FINGER, screen.getMechanism());
        assertEquals(ScreenType.CAPACITIVE, screen.getScreenType());
        Set<Network> networks = hw.getNetworking();
        assertTrue(networks.contains(Network.BLUETOOTH));
        assertTrue(networks.contains(Network.WIFI));
        assertTrue(networks.contains(Network.NFC));
        Set<Sensor> sensors = hw.getSensors();
        assertTrue(sensors.contains(Sensor.ACCELEROMETER));
        assertTrue(sensors.contains(Sensor.BAROMETER));
        assertTrue(sensors.contains(Sensor.GYROSCOPE));
        assertTrue(sensors.contains(Sensor.COMPASS));
        assertTrue(sensors.contains(Sensor.GPS));
        assertTrue(sensors.contains(Sensor.PROXIMITY_SENSOR));
        assertTrue(hw.hasMic());
        assertEquals(2, hw.getCameras().size());
        Camera c = hw.getCamera(CameraLocation.FRONT);
        assertTrue(c != null);
        assertEquals(c.getLocation(), CameraLocation.FRONT);
        assertFalse(c.hasFlash());
        assertTrue(c.hasAutofocus());
        c = hw.getCamera(CameraLocation.BACK);
        assertTrue(c != null);
        assertEquals(c.getLocation(), CameraLocation.BACK);
        assertTrue(c.hasFlash());
        assertTrue(c.hasAutofocus());
        assertEquals(Keyboard.NOKEY, hw.getKeyboard());
        assertEquals(Navigation.NONAV, hw.getNav());
        assertEquals(new Storage(1, Unit.GiB), hw.getRam());
        assertEquals(ButtonType.SOFT, hw.getButtonType());
        List<Storage> storage = hw.getInternalStorage();
        assertEquals(1, storage.size());
        assertEquals(new Storage(16, Unit.GiB), storage.get(0));
        storage = hw.getRemovableStorage();
        assertEquals(0, storage.size());
        assertEquals("OMAP 4460", hw.getCpu());
        assertEquals("PowerVR SGX540", hw.getGpu());
        Set<Abi> abis = hw.getSupportedAbis();
        assertEquals(2, abis.size());
        assertTrue(abis.contains(Abi.ARMEABI));
        assertTrue(abis.contains(Abi.ARMEABI_V7A));
        assertEquals(0, hw.getSupportedUiModes().size());
        assertEquals(PowerType.BATTERY, hw.getChargeType());

        // Test Software
        assertEquals(1, device.getAllSoftware().size());
        Software sw = device.getSoftware(15);
        assertEquals(15, sw.getMaxSdkLevel());
        assertEquals(15, sw.getMinSdkLevel());
        assertTrue(sw.hasLiveWallpaperSupport());
        assertEquals(12, sw.getBluetoothProfiles().size());
        assertTrue(sw.getBluetoothProfiles().contains(BluetoothProfile.A2DP));
        assertEquals("2.0", sw.getGlVersion());
        assertEquals(29, sw.getGlExtensions().size());
        assertTrue(sw.getGlExtensions().contains("GL_OES_depth24"));

        // Test States
        assertEquals(2, device.getAllStates().size());
        State s = device.getDefaultState();
        assertEquals("Portrait", s.getName());
        assertTrue(s.isDefaultState());
        assertEquals("The phone in portrait view", s.getDescription());
        assertEquals(ScreenOrientation.PORTRAIT, s.getOrientation());
        assertEquals(KeyboardState.SOFT, s.getKeyState());
        assertEquals(NavigationState.HIDDEN, s.getNavState());
        s = device.getState("Landscape");
        assertEquals("Landscape", s.getName());
        assertFalse(s.isDefaultState());
        assertEquals(ScreenOrientation.LANDSCAPE, s.getOrientation());
    }

    public void testApiRange() throws Exception {
        Map<String, String> replacements = new HashMap<String, String>();
        replacements.put("api-level", "1-");
        InputStream stream = DeviceSchemaTest.getReplacedStream(replacements);
        List<Device> devices = DeviceParser.parse(stream);
        assertEquals(1, devices.size());
        Device device = devices.get(0);
        assertTrue(device.getSoftware(1) != null);
        assertTrue(device.getSoftware(2) != null);
        assertTrue(device.getSoftware(0) == null);
        replacements.put("api-level", "-2");
        stream = DeviceSchemaTest.getReplacedStream(replacements);
        device = DeviceParser.parse(stream).get(0);
        assertTrue(device.getSoftware(2) != null);
        assertTrue(device.getSoftware(3) == null);
        replacements.put("api-level", "1-2");
        stream = DeviceSchemaTest.getReplacedStream(replacements);
        device = DeviceParser.parse(stream).get(0);
        assertTrue(device.getSoftware(0) == null);
        assertTrue(device.getSoftware(1) != null);
        assertTrue(device.getSoftware(2) != null);
        assertTrue(device.getSoftware(3) == null);
        replacements.put("api-level", "-");
        stream = DeviceSchemaTest.getReplacedStream(replacements);
        device = DeviceParser.parse(stream).get(0);
        assertTrue(device.getSoftware(0) != null);
        assertTrue(device.getSoftware(15) != null);
    }

    public void testBadNetworking() throws Exception {
        Map<String, String> replacements = new HashMap<String, String>();
        replacements.put("networking", "NFD");
        InputStream stream = DeviceSchemaTest.getReplacedStream(replacements);
        try {
            List<Device> devices = DeviceParser.parse(stream);
            assertEquals(1, devices.size());
            assertEquals(0, devices.get(0).getDefaultHardware().getNetworking().size());
            fail();
        } catch (SAXParseException e) {
            assertTrue(e.getMessage().startsWith("cvc-enumeration-valid: Value 'NFD'"));
        }
    }
}
