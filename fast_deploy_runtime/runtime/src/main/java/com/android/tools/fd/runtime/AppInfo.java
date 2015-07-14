package com.android.tools.fd.runtime;

public class AppInfo {
    private AppInfo() {
    }
    /**
     * The application id of this app (e.g. the package name). Used to pick a unique
     * directory for the app's reloaded resources. (We can't look for it in the manifest,
     * since we need this information very early in the app life cycle, and we don't want
     * to call into the framework and cause more parts of it to be initialized before
     * we've monkey-patched the application class and resource loaders.)
     * <p>
     * (Not final: Will be replaced by byte-code manipulation at build time)
     */
    public static String applicationId = "com.android.tools.fd.runtime.testapp";

    /**
     * The fully qualified name of the real application to run. This is the user's app,
     * which has been hidden from the manifest during build time. Can be null if the
     * app does not have a custom application (in which case a default android.app.Application
     * is used.)
     * <p>
     */
    public static String applicationClass = "com.android.tools.fd.runtime.testapp.MyApplication";
}
