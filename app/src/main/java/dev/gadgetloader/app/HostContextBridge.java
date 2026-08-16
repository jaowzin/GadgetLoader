package dev.gadgetloader.app;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;

import java.io.File;

/**
 * Presents target code/resources to the hosted runtime while keeping all writable
 * state and Binder operation attribution inside GadgetLoader's own sandbox/UID.
 */
final class HostContextBridge extends ContextWrapper {
    private final Context targetContext;
    private final Context hostContext;
    private final ApplicationInfo bridgedInfo;
    private final String prefPrefix;

    HostContextBridge(Context targetContext, Context hostContext) {
        super(targetContext);
        this.targetContext = targetContext;
        Context app = hostContext.getApplicationContext();
        this.hostContext = app != null ? app : hostContext;
        this.prefPrefix = "hosted_" + safe(targetContext.getPackageName()) + "_";

        ApplicationInfo target = targetContext.getApplicationInfo();
        ApplicationInfo host = this.hostContext.getApplicationInfo();
        bridgedInfo = new ApplicationInfo(target);
        bridgedInfo.uid = host.uid;
        bridgedInfo.dataDir = host.dataDir;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            bridgedInfo.deviceProtectedDataDir = host.deviceProtectedDataDir;
        }
    }

    @Override
    public AssetManager getAssets() {
        return targetContext.getAssets();
    }

    @Override
    public Resources getResources() {
        return targetContext.getResources();
    }

    @Override
    public ClassLoader getClassLoader() {
        return targetContext.getClassLoader();
    }

    @Override
    public PackageManager getPackageManager() {
        return targetContext.getPackageManager();
    }

    @Override
    public String getPackageName() {
        return targetContext.getPackageName();
    }

    @Override
    public String getOpPackageName() {
        // Binder-facing package attribution must belong to the actual host UID.
        return hostContext.getOpPackageName();
    }

    @Override
    public String getPackageCodePath() {
        return targetContext.getPackageCodePath();
    }

    @Override
    public String getPackageResourcePath() {
        return targetContext.getPackageResourcePath();
    }

    @Override
    public ApplicationInfo getApplicationInfo() {
        return new ApplicationInfo(bridgedInfo);
    }

    @Override
    public Context getApplicationContext() {
        return this;
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return hostContext.getSharedPreferences(prefPrefix + safe(name), mode);
    }

    @Override
    public boolean deleteSharedPreferences(String name) {
        return hostContext.deleteSharedPreferences(prefPrefix + safe(name));
    }

    @Override
    public File getFilesDir() {
        return hostContext.getFilesDir();
    }

    @Override
    public File getCacheDir() {
        return hostContext.getCacheDir();
    }

    @Override
    public File getCodeCacheDir() {
        return hostContext.getCodeCacheDir();
    }

    @Override
    public File getNoBackupFilesDir() {
        return hostContext.getNoBackupFilesDir();
    }

    @Override
    public File getDataDir() {
        return hostContext.getDataDir();
    }

    @Override
    public File getExternalFilesDir(String type) {
        return hostContext.getExternalFilesDir(type);
    }

    @Override
    public File[] getExternalFilesDirs(String type) {
        return hostContext.getExternalFilesDirs(type);
    }

    @Override
    public File getExternalCacheDir() {
        return hostContext.getExternalCacheDir();
    }

    @Override
    public File getDatabasePath(String name) {
        return hostContext.getDatabasePath(prefPrefix + safe(name));
    }

    @Override
    public File getDir(String name, int mode) {
        return hostContext.getDir(prefPrefix + safe(name), mode);
    }

    private static String safe(String value) {
        if (value == null || value.isEmpty()) return "default";
        return value.replace('/', '_').replace('\\', '_').replace('.', '_');
    }
}
