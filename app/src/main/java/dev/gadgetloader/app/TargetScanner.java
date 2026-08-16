package dev.gadgetloader.app;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;

final class TargetScanner {
    private TargetScanner() {}

    static List<TargetApp> scan(Context context) {
        PackageManager pm = context.getPackageManager();
        List<TargetApp> result = new ArrayList<>();

        @SuppressWarnings("deprecation")
        List<ApplicationInfo> installed = pm.getInstalledApplications(0);

        for (ApplicationInfo app : installed) {
            if (context.getPackageName().equals(app.packageName)) continue;
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;

            String label;
            try {
                CharSequence value = pm.getApplicationLabel(app);
                label = value == null ? app.packageName : value.toString();
            } catch (Throwable ignored) {
                label = app.packageName;
            }

            String versionName = "?";
            try {
                @SuppressWarnings("deprecation")
                PackageInfo packageInfo = pm.getPackageInfo(app.packageName, 0);
                if (packageInfo.versionName != null) versionName = packageInfo.versionName;
            } catch (Throwable ignored) {}

            List<String> apkPaths = new ArrayList<>();
            if (app.sourceDir != null) apkPaths.add(app.sourceDir);
            if (app.splitSourceDirs != null) {
                for (String split : app.splitSourceDirs) {
                    if (split != null) apkPaths.add(split);
                }
            }

            boolean unity = false;
            boolean il2cpp = false;
            boolean mono = false;
            boolean arm64 = false;

            for (String path : apkPaths) {
                File file = new File(path);
                if (!file.isFile()) continue;
                try (ZipFile zip = new ZipFile(file)) {
                    boolean unityArm64 = zip.getEntry("lib/arm64-v8a/libunity.so") != null;
                    boolean il2cppArm64 = zip.getEntry("lib/arm64-v8a/libil2cpp.so") != null;
                    boolean monoArm64 = zip.getEntry("lib/arm64-v8a/libmonobdwgc-2.0.so") != null
                            || zip.getEntry("lib/arm64-v8a/libmono.so") != null;

                    unity |= unityArm64
                            || zip.getEntry("assets/bin/Data/globalgamemanagers") != null
                            || zip.getEntry("assets/bin/Data/data.unity3d") != null;
                    il2cpp |= il2cppArm64;
                    mono |= monoArm64;
                    arm64 |= unityArm64 || il2cppArm64 || monoArm64
                            || zip.getEntry("lib/arm64-v8a/libmain.so") != null;
                } catch (Throwable ignored) {}
            }

            if (!unity) continue;

            result.add(new TargetApp(
                    label,
                    app.packageName,
                    versionName,
                    true,
                    il2cpp,
                    mono,
                    arm64,
                    apkPaths.size()
            ));
        }

        result.sort(Comparator.comparing(
                target -> target.label.toLowerCase(Locale.ROOT)
        ));
        return result;
    }
}
