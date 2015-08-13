package com.android.tests.basic.buildscript;

import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.builder.testing.api.DeviceProvider;
import com.google.common.collect.Lists;

import java.util.List;

public class FakeProvider extends DeviceProvider {

    private boolean initCalled = false;
    private boolean terminateCalled = false;
    private List<FakeDevice> devices = Lists.newArrayList();

    @Override
    public String getName() {
        return "fake";
    }

    @Override
    public List<? extends DeviceConnector> getDevices() {
        return devices;
    }

    @Override
    public void init() throws DeviceException {
        System.out.println("INIT CALLED");
        initCalled = true;

        devices.add(new FakeDevice("device1"));
        devices.add(new FakeDevice("device2"));
    }

    @Override
    public void terminate() throws DeviceException {
        System.out.println("TERMINATE CALLED");
        terminateCalled = true;
    }

    @Override
    public int getTimeoutInMs() {
        return 0;
    }

    public String isValid() {
        if (!initCalled) {
            return "init not called";
        }

        if (!terminateCalled) {
            return "terminate not called";
        }

        for (FakeDevice device : devices) {
            String error = device.isValid();
            if (error != null) {
                return error;
            }
        }

        return null;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

}