package com.android.tests.basic.buildscript;

import com.android.annotations.NonNull;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.utils.ILogger;
import com.google.common.collect.Lists;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class FakeDevice extends DeviceConnector {

    private final String name;
    private boolean connectCalled = false;
    private boolean disconnectCalled = false;
    private boolean installCalled = false;
    private boolean uninstallCalled = false;
    private boolean execShellCalled = false;

    private final List<File> installedApks = Lists.newArrayList();


    FakeDevice(String name) {
        this.name = name;
    }

    @Override
    public void connect(int timeOut, ILogger logger) throws TimeoutException {
        logger.info("CONNECT(%S) CALLED", name);
        connectCalled = true;
    }

    @Override
    public void disconnect(int timeOut, ILogger logger) throws TimeoutException {
        logger.info("DISCONNECTED(%S) CALLED", name);
        disconnectCalled = true;
    }

    @Override
    public void installPackage(@NonNull File apkFile, int timeout, ILogger logger) throws DeviceException {
        logger.info("INSTALL(%S) CALLED", name);

        if (apkFile == null) {
            throw new NullPointerException("Null testApk");
        }

        System.out.println(String.format("\t(%s)ApkFile: %s", name, apkFile.getAbsolutePath()));

        if (!apkFile.isFile()) {
            throw new RuntimeException("Missing file: " + apkFile.getAbsolutePath());
        }

        if (!apkFile.getAbsolutePath().endsWith(".apk")) {
            throw new RuntimeException("Wrong extension: " + apkFile.getAbsolutePath());
        }

        if (installedApks.contains(apkFile)) {
            throw new RuntimeException("Already added: " + apkFile.getAbsolutePath());
        }

        installedApks.add(apkFile);

        installCalled = true;
    }

    @Override
    public void uninstallPackage(@NonNull String packageName, int timeout, ILogger logger) throws DeviceException {
        logger.info("UNINSTALL(%S) CALLED", name);
        uninstallCalled = true;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void executeShellCommand(String command, IShellOutputReceiver receiver,
                                    long maxTimeToOutputResponse, TimeUnit maxTimeUnits)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
            IOException {
        System.out.println(String.format("EXECSHELL(%S) CALLED", name));
        execShellCalled = true;
    }

    public String isValid() {
        if (!connectCalled) {
            return "connect not called on " + name;
        }

        if (!disconnectCalled) {
            return "disconnect not called on " + name;
        }

        if (!installCalled) {
            return "installPackage not called on " + name;
        }

        if (!uninstallCalled) {
            return "uninstallPackage not called on " + name;
        }

        if (!execShellCalled) {
            return "executeShellCommand not called on " + name;
        }

        return null;
    }

    public int getApiLevel() {
        return 99;
    }

    @NonNull
    public List<String> getAbis() {
        return Collections.singletonList("fake");
    }

    public int getDensity() {
        return 160;
    }

    public int getHeight() {
        return 800;
    }

    public int getWidth() {
        return 480;
    }
}
