package dev.gadgetloader.app;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class ScriptStore {
    static final String GADGET_SCRIPT_PATH =
            "/sdcard/Android/data/dev.gadgetloader.app/files/gadget/main.js";

    private ScriptStore() {}

    static File scriptFile(Context context) throws IOException {
        File external = context.getExternalFilesDir(null);
        if (external == null) {
            throw new IOException("External files dir indisponível");
        }
        File dir = new File(external, "gadget");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Não foi possível criar " + dir);
        }
        return new File(dir, "main.js");
    }

    static File ensureDefault(Context context) throws IOException {
        File target = scriptFile(context);
        if (!target.isFile() || target.length() == 0L) {
            writeDefault(context, true);
        }
        return target;
    }

    static File writeDefault(Context context, boolean force) throws IOException {
        File target = scriptFile(context);
        if (target.isFile() && !force) return target;
        try (InputStream in = context.getAssets().open("default.js")) {
            writeAtomic(target, in);
        }
        return target;
    }

    static File importFromUri(Context context, Uri uri) throws IOException {
        if (uri == null) throw new IOException("URI do script é nulo");
        File target = scriptFile(context);
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Não foi possível abrir o script");
            writeAtomic(target, in);
        }
        return target;
    }

    private static void writeAtomic(File target, InputStream in) throws IOException {
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (OutputStream out = new FileOutputStream(temp, false)) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }

        if (target.exists() && !target.delete()) {
            // FileOutputStream fallback below still replaces it if rename is unavailable.
        }
        if (!temp.renameTo(target)) {
            try (InputStream retryIn = new java.io.FileInputStream(temp);
                 OutputStream out = new FileOutputStream(target, false)) {
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = retryIn.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }

        // Make sure Frida's on_change watcher sees a fresh timestamp.
        //noinspection ResultOfMethodCallIgnored
        target.setLastModified(System.currentTimeMillis());
    }
}
