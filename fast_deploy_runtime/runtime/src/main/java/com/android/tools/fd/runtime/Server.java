package com.android.tools.fd.runtime;

import android.app.Activity;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import dalvik.system.DexClassLoader;

import static com.android.tools.fd.runtime.BootstrapApplication.LOG_TAG;
import static com.android.tools.fd.runtime.FileManager.CLASSES_DEX_3_SUFFIX;
import static com.android.tools.fd.runtime.FileManager.CLASSES_DEX_SUFFIX;

/**
 * Server running in the app listening for messages from the IDE and updating the
 * resources
 */
public class Server {
    private LocalServerSocket mServerSocket;

    public static void create(@NonNull String packageName) {
        new Server(packageName);
    }

    private Server(@NonNull String packageName) {
        try {
            mServerSocket = new LocalServerSocket(packageName);
            if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                Log.i(LOG_TAG, "Starting server socket listening for package " + packageName
                        + " on " + mServerSocket.getLocalSocketAddress());
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "IO Error creating local socket at " + packageName, e);
            return;
        }
        startServer();

        if (Log.isLoggable(LOG_TAG, Log.INFO)) {
            Log.i(LOG_TAG, "Started server for package " + packageName);
        }
    }

    private void startServer() {
        try {
            Thread socketServerThread = new Thread(new SocketServerThread());
            socketServerThread.start();
        } catch (Throwable e) {
            // Make sure an exception doesn't cause the rest of the user's
            // onCreate() method to be invoked
            Log.i(LOG_TAG, "Fatal error starting server", e);
        }
    }

    private class SocketServerThread extends Thread {
        @Override
        public void run() {
            try {
                // We expect to bail out of this loop by an exception (SocketException when the
                // socket is closed by stop() above)
                while (true) {
                    LocalServerSocket serverSocket = mServerSocket;
                    if (serverSocket == null) {
                        break; // stopped?
                    }
                    LocalSocket socket = serverSocket.accept();

                    if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                        Log.i(LOG_TAG, "Received connection from IDE: spawning connection thread");
                    }

                    SocketServerReplyThread socketServerReplyThread = new SocketServerReplyThread(
                            socket);
                    socketServerReplyThread.run();
                }
            } catch (IOException e) {
                if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                    Log.i(LOG_TAG, "Fatal error accepting connection on local socket", e);
                }
            }
        }
    }

    private static class SocketServerReplyThread extends Thread {
        private LocalSocket mSocket;

        SocketServerReplyThread(LocalSocket socket) {
            mSocket = socket;
        }

        @Override
        public void run() {
            boolean requiresRestart = false;
            Activity foregroundActivity = Restarter.getForegroundActivity();
            try {
                DataInputStream input = new DataInputStream(mSocket.getInputStream());
                try {
                    List<ApplicationPatch> changes = ApplicationPatch.read(input);
                    if (changes == null) {
                        return;
                    }
                    FileManager.startUpdate();
                    boolean wroteResources = false;
                    for (ApplicationPatch change : changes) {
                        String path = change.getPath();
                        if (path.endsWith(CLASSES_DEX_SUFFIX)) {
                            FileManager.writeDexFile(change.getBytes());
                            requiresRestart = true;
                        } if (path.endsWith(CLASSES_DEX_3_SUFFIX)) {

                            DexClassLoader dexClassLoader = new DexClassLoader(path,
                                    foregroundActivity.getCacheDir().getPath(), null,
                                    getClass().getClassLoader());
                            try {
                                // we should transform this process with an interface/impl
                                Class aClass = dexClassLoader.loadClass("com.android.build.Patches");
                                try {
                                    aClass.getDeclaredMethod("load").invoke(null);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    requiresRestart = true;
                                }
                            } catch (ClassNotFoundException e) {
                                e.printStackTrace();
                                requiresRestart = true;
                            }


                        } else {
                            FileManager.writeAaptResources(path, change.getBytes());
                            wroteResources = true;
                        }
                        if (change.forceRestart) {
                            requiresRestart = true;
                        }
                    }
                    FileManager.finishUpdate(wroteResources);
                } finally {
                    try {
                        input.close();
                    } catch (IOException ignore) {
                    }
                }
            } catch (IOException e) {
                if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                    Log.i(LOG_TAG, "Fatal error receiving messages", e);
                }
                return;
            }

            // Changed the .arsc contents? If so, we need to restart the whole app
            // Requires restart

            // Compute activity
            List<Activity> activities = Restarter.getActivities(false);
            File file = FileManager.getExternalResourceFile();
            if (!requiresRestart && file != null) {
                Activity activity = foregroundActivity;

                // Try to just replace the resources on the fly!
                String resources = file.getPath();
                MonkeyPatcher.monkeyPatchApplication(null, null, resources);
                MonkeyPatcher.monkeyPatchExistingResources(resources, activities);
                if (activity != null) {
                    Restarter.restartActivityOnUiThread(activity);
                    return;
                }
            }
            Restarter.restartApp(activities);
        }
    }
}
