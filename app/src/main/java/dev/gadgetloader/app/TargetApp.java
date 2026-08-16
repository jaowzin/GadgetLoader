package dev.gadgetloader.app;

final class TargetApp {
    final String label;
    final String packageName;
    final String versionName;
    final boolean unity;
    final boolean il2cpp;
    final boolean mono;
    final boolean arm64;
    final int apkCount;

    TargetApp(String label, String packageName, String versionName,
              boolean unity, boolean il2cpp, boolean mono,
              boolean arm64, int apkCount) {
        this.label = label;
        this.packageName = packageName;
        this.versionName = versionName;
        this.unity = unity;
        this.il2cpp = il2cpp;
        this.mono = mono;
        this.arm64 = arm64;
        this.apkCount = apkCount;
    }

    String backendLabel() {
        if (!unity) return "Não Unity";
        if (il2cpp) return "Unity IL2CPP";
        if (mono) return "Unity Mono";
        return "Unity";
    }

    boolean isHostable() {
        return unity && arm64;
    }

    @Override
    public String toString() {
        return label + "\n" + packageName;
    }
}
