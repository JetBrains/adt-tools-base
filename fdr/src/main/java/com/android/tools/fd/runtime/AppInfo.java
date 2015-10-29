package com.android.tools.fd.runtime;

public class AppInfo {
    // Keep the structure of this class in sync with
    // GenerateInstantRunAppInfoTask#writeAppInfoClass

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
    @SuppressWarnings("CanBeFinal")
    public static String applicationId = null;

    /**
     * The fully qualified name of the real application to run. This is the user's app,
     * which has been hidden from the manifest during build time. Can be null if the
     * app does not have a custom application (in which case a default android.app.Application
     * is used.)
     * <p>
     */
    @SuppressWarnings("CanBeFinal")
    public static String applicationClass = null;

    /**
     * A token assigned to this app at build time. This is used such that the running
     * app socket server can be reasonably sure that it's responding to requests from
     * the IDE.
     */
    public static long token = 0L;
}
