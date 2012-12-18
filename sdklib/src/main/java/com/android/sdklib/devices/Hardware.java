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

import com.android.resources.Keyboard;
import com.android.resources.Navigation;
import com.android.resources.UiMode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Hardware {
    private Screen mScreen;
    private Set<Network> mNetworking = new HashSet<Network>();
    private Set<Sensor> mSensors = new HashSet<Sensor>();
    private boolean mMic;
    private List<Camera> mCameras = new ArrayList<Camera>();
    private Keyboard mKeyboard;
    private Navigation mNav;
    private Storage mRam;
    private ButtonType mButtons;
    private List<Storage> mInternalStorage = new ArrayList<Storage>();
    private List<Storage> mRemovableStorage = new ArrayList<Storage>();
    private String mCpu;
    private String mGpu;
    private Set<Abi> mAbis = new HashSet<Abi>();
    private Set<UiMode> mUiModes = new HashSet<UiMode>();
    private PowerType mPluggedIn;

    public Set<Network> getNetworking() {
        return mNetworking;
    }

    public void addNetwork(Network n) {
        mNetworking.add(n);
    }

    public void addAllNetworks(Collection<Network> ns) {
        mNetworking.addAll(ns);
    }

    public Set<Sensor> getSensors() {
        return mSensors;
    }

    public void addSensor(Sensor sensor) {
        mSensors.add(sensor);
    }

    public void addAllSensors(Collection<Sensor> sensors) {
        mSensors.addAll(sensors);
    }

    public boolean hasMic() {
        return mMic;
    }

    public void setHasMic(boolean hasMic) {
        mMic = hasMic;
    }

    public List<Camera> getCameras() {
        return mCameras;
    }

    public void addCamera(Camera c) {
        mCameras.add(c);
    }

    public void addAllCameras(Collection<Camera> cs) {
        mCameras.addAll(cs);
    }

    public Camera getCamera(int i) {
        return mCameras.get(i);
    }

    public Camera getCamera(CameraLocation location) {
        for (Camera c : mCameras) {
            if (location.equals(c.getLocation())) {
                return c;
            }
        }
        return null;
    }

    public Keyboard getKeyboard() {
        return mKeyboard;
    }

    public void setKeyboard(Keyboard k) {
        mKeyboard = k;
    }

    public Navigation getNav() {
        return mNav;
    }

    public void setNav(Navigation n) {
        mNav = n;
    }

    public Storage getRam() {
        return mRam;
    }

    public void setRam(Storage ram) {
        mRam = ram;
    }

    public ButtonType getButtonType() {
        return mButtons;
    }

    public void setButtonType(ButtonType bt) {
        mButtons = bt;
    }

    public List<Storage> getInternalStorage() {
        return mInternalStorage;
    }

    public void addInternalStorage(Storage is) {
        mInternalStorage.add(is);
    }

    public void addAllInternalStorage(Collection<Storage> is) {
        mInternalStorage.addAll(is);
    }

    public List<Storage> getRemovableStorage() {
        return mRemovableStorage;
    }

    public void addRemovableStorage(Storage rs) {
        mRemovableStorage.add(rs);
    }

    public void addAllRemovableStorage(Collection<Storage> rs) {
        mRemovableStorage.addAll(rs);
    }

    public String getCpu() {
        return mCpu;
    }

    public void setCpu(String cpuName) {
        mCpu = cpuName;
    }

    public String getGpu() {
        return mGpu;
    }

    public void setGpu(String gpuName) {
        mGpu = gpuName;
    }

    public Set<Abi> getSupportedAbis() {
        return mAbis;
    }

    public void addSupportedAbi(Abi abi) {
        mAbis.add(abi);
    }

    public void addAllSupportedAbis(Collection<Abi> abis) {
        mAbis.addAll(abis);
    }

    public Set<UiMode> getSupportedUiModes() {
        return mUiModes;
    }

    public void addSupportedUiMode(UiMode uiMode) {
        mUiModes.add(uiMode);
    }

    public void addAllSupportedUiModes(Collection<UiMode> uiModes) {
        mUiModes.addAll(uiModes);
    }

    public PowerType getChargeType() {
        return mPluggedIn;
    }

    public void setChargeType(PowerType chargeType) {
        mPluggedIn = chargeType;
    }

    public Screen getScreen() {
        return mScreen;
    }

    public void setScreen(Screen s) {
        mScreen = s;
    }

    /**
     * Returns a copy of the object that shares no state with it,
     * but is initialized to equivalent values.
     *
     * @return A copy of the object.
     */
    public Hardware deepCopy() {
        Hardware hw = new Hardware();
        hw.mScreen = mScreen.deepCopy();
        hw.mNetworking = new HashSet<Network>(mNetworking);
        hw.mSensors = new HashSet<Sensor>(mSensors);
        // Get the constant boolean value
        hw.mMic = mMic;
        hw.mCameras = new ArrayList<Camera>();
        for (Camera c : mCameras) {
            hw.mCameras.add(c.deepCopy());
        }
        hw.mKeyboard = mKeyboard;
        hw.mNav = mNav;
        hw.mRam = mRam.deepCopy();
        hw.mButtons = mButtons;
        hw.mInternalStorage = new ArrayList<Storage>();
        for (Storage s : mInternalStorage) {
            hw.mInternalStorage.add(s.deepCopy());
        }
        hw.mRemovableStorage = new ArrayList<Storage>();
        for (Storage s : mRemovableStorage) {
            hw.mRemovableStorage.add(s.deepCopy());
        }
        hw.mCpu = mCpu;
        hw.mGpu = mGpu;
        hw.mAbis = new HashSet<Abi>(mAbis);
        hw.mUiModes = new HashSet<UiMode>(mUiModes);
        hw.mPluggedIn = mPluggedIn;
        return hw;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Hardware)) {
            return false;
        }
        Hardware hw = (Hardware) o;
        return mScreen.equals(hw.getScreen())
                && mNetworking.equals(hw.getNetworking())
                && mSensors.equals(hw.getSensors())
                && mMic == hw.hasMic()
                && mCameras.equals(hw.getCameras())
                && mKeyboard == hw.getKeyboard()
                && mNav == hw.getNav()
                && mRam.equals(hw.getRam())
                && mButtons == hw.getButtonType()
                && mInternalStorage.equals(hw.getInternalStorage())
                && mRemovableStorage.equals(hw.getRemovableStorage())
                && mCpu.equals(hw.getCpu())
                && mGpu.equals(hw.getGpu())
                && mAbis.equals(hw.getSupportedAbis())
                && mUiModes.equals(hw.getSupportedUiModes())
                && mPluggedIn == hw.getChargeType();

    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = 31 * hash + mScreen.hashCode();

        // Since sets have no defined order, we need to hash them in such a way that order doesn't
        // matter.
        int temp = 0;
        for (Network n : mNetworking) {
            temp |= 1 << n.ordinal();
        }
        hash = 31 * hash + temp;

        temp = 0;
        for (Sensor s : mSensors) {
            temp |= 1 << s.ordinal();
        }

        hash = 31 * hash + temp;
        hash = 31 * hash + (mMic ? 1 : 0);
        hash = mCameras.hashCode();
        hash = 31 * hash + mKeyboard.ordinal();
        hash = 31 * hash + mNav.ordinal();
        hash = 31 * hash + mRam.hashCode();
        hash = 31 * hash + mButtons.ordinal();
        hash = 31 * hash + mInternalStorage.hashCode();
        hash = 31 * hash + mRemovableStorage.hashCode();

        for (Character c : mCpu.toCharArray()) {
            hash = 31 * hash + c;
        }

        for (Character c : mGpu.toCharArray()) {
            hash = 31 * hash + c;
        }

        temp = 0;
        for (Abi a : mAbis) {
            temp |= 1 << a.ordinal();
        }
        hash = 31 * hash + temp;

        temp = 0;
        for (UiMode ui : mUiModes) {
            temp |= 1 << ui.ordinal();
        }
        hash = 31 * hash + temp;

        return hash;
    }
}
