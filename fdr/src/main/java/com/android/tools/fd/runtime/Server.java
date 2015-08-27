package com.android.tools.fd.runtime;

import android.app.Activity;
import android.app.Application;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.support.annotation.NonNull;
import android.util.Log;

import com.android.build.gradle.internal.incremental.PatchesLoader;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
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
    private final Application mApplication;

    public static void create(@NonNull String packageName, @NonNull Application application) {
        new Server(packageName, application);
    }

    private Server(@NonNull String packageName, @NonNull Application application) {
        mApplication = application;
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

    private class SocketServerReplyThread extends Thread {
        private final LocalSocket mSocket;

        SocketServerReplyThread(LocalSocket socket) {
            mSocket = socket;
        }

        @Override
        public void run() {
            boolean requiresRestart = false;
            boolean incrementalCode = false;
            boolean incrementalResources = false;
            try {
                DataInputStream input = new DataInputStream(mSocket.getInputStream());
                try {
                    List<ApplicationPatch> changes = ApplicationPatch.read(input);
                    if (changes == null) {
                        return;
                    }
                    FileManager.startUpdate();
                    for (ApplicationPatch change : changes) {
                        if (change.forceRestart) {
                            requiresRestart = true; // This is hacky at needs to be cleaned up
                        }
                        String path = change.getPath();
                        if (path.endsWith(CLASSES_DEX_SUFFIX)) {
                            if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                                Log.i(LOG_TAG, "Received restart code patch");
                            }
                            FileManager.writeDexFile(change.getBytes());
                        } else if (path.endsWith(CLASSES_DEX_3_SUFFIX)) {
                            if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                                Log.i(LOG_TAG, "Received incremental code patch");
                            }
                            requiresRestart = false;
                            try {
                                String dexFile = FileManager.writeTempDexFile(change.getBytes());
                                if (dexFile == null) {
                                    Log.e(LOG_TAG, "No file to write the code to");
                                    continue;
                                } else if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                                    Log.i(LOG_TAG, "Reading live code from " + dexFile);
                                }
                                String nativeLibraryPath = FileManager.getNativeLibraryFolder().getPath();
                                DexClassLoader dexClassLoader = new DexClassLoader(dexFile,
                                        mApplication.getCacheDir().getPath(), nativeLibraryPath,
                                        getClass().getClassLoader()) {
                                    @Override
                                    protected Class<?> findClass(String name) throws ClassNotFoundException {
                                        try {
                                            Class<?> aClass = super.findClass(name);
                                            if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                                                Log.i(LOG_TAG, "Reload class loader: findClass(" + name + ") = " + aClass);
                                            }

                                            return aClass;
                                        } catch (ClassNotFoundException e) {
                                            if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                                                Log.i(LOG_TAG, "Reload class loader: findClass(" + name + ") : not found");
                                            }
                                            throw e;
                                        }
                                    }

                                    @Override
                                    public Class<?> loadClass(String className) throws ClassNotFoundException {
                                        try {
                                            Class<?> aClass = super.loadClass(className);
                                            if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                                                Log.i(LOG_TAG, "Reload class loader: loadClass(" + className + ") = " + aClass);
                                            }

                                            return aClass;
                                        } catch (ClassNotFoundException e) {
                                            if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                                                Log.i(LOG_TAG, "Reload class loader: loadClass(" + className + ") : not found");
                                            }
                                            throw e;
                                        }
                                    }
                                };

                                // we should transform this process with an interface/impl
                                Class<?> aClass = Class.forName("com.android.build.gradle.internal.incremental.AppPatchesLoaderImpl", true, dexClassLoader);
                                try {
                                    if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                                        Log.i(LOG_TAG, "Got the patcher class " + aClass);
                                    }

                                    PatchesLoader loader = (PatchesLoader) aClass.newInstance();
                                    if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                                        Log.i(LOG_TAG, "Got the patcher instance " + loader);
                                    }
                                    String[] getPatchedClasses = (String[]) aClass.getDeclaredMethod("getPatchedClasses").invoke(loader);
                                    if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                                        Log.i(LOG_TAG, "Got the list of classes ");
                                        for (String getPatchedClass : getPatchedClasses) {
                                            Log.i(LOG_TAG, "class " + getPatchedClass);
                                        }
                                    }
                                    requiresRestart = !loader.load();
                                    incrementalCode = true;
                                } catch (Exception e) {
                                    Log.e(LOG_TAG, "Couldn't apply code changes", e);
                                    e.printStackTrace();
                                    requiresRestart = true;
                                }
                            } catch (Throwable e) {
                                Log.e(LOG_TAG, "Couldn't apply code changes", e);
                                requiresRestart = true;
                            }
                        } else {
                            if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                                Log.i(LOG_TAG, "Received resource changes (" + path + ")");
                            }
                            FileManager.writeAaptResources(path, change.getBytes());
                            incrementalResources = true;
                        }
                    }
                    FileManager.finishUpdate(incrementalResources);
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


            if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                Log.i(LOG_TAG, "Finished loading changes; requires restart=" + requiresRestart);
            }

            // Changed the .arsc contents? If so, we need to restart the whole app
            // Requires restart

            // Compute activity
            List<Activity> activities = Restarter.getActivities(false);
            if (incrementalResources) {
                // Try to just replace the resources on the fly!
                File file = FileManager.getExternalResourceFile();

                Activity activity = Restarter.getForegroundActivity();
                if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                    Log.i(LOG_TAG, "About to update resource file=" + file +
                            ", activities=" + activities);
                }

                if (file != null) {
                    String resources = file.getPath();
                    MonkeyPatcher.monkeyPatchApplication(null, null, resources);
                    MonkeyPatcher.monkeyPatchExistingResources(resources, activities);

                    if (activity != null) {
                        if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                            Log.i(LOG_TAG, "Restarting activity only!");
                        }
                        Restarter.restartActivityOnUiThread(activity);
                        return;
                    }

                    if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                        Log.i(LOG_TAG, "No activity found, falling through to do a full app restart");
                    }
                } else {
                    Log.e(LOG_TAG, "No resource file found to apply");
                }
                // Fall through to full app start
            } else if (incrementalCode) {
                if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                    Log.i(LOG_TAG, "Applying incremental code without restart");
                }
                Activity activity = Restarter.getForegroundActivity();
                Restarter.restartActivityOnUiThread(activity);
                return;
            }

            if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                Log.i(LOG_TAG, "Performing full app restart to apply code=" + incrementalCode
                        + " and resources=" + incrementalResources);
            }

            Restarter.restartApp(activities);
        }
    }
}
