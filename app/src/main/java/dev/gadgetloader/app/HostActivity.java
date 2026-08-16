package dev.gadgetloader.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Hosted-only Unity runtime. Target code/resources are loaded into this private
 * GadgetLoader :host process, then Frida Gadget is loaded before UnityPlayer.
 */
public final class HostActivity extends Activity {
    public static final String EXTRA_TARGET_PACKAGE = "target_package";

    private static final String TAG = "GadgetLoader.Host";
    private static final String UNITY6_ACTIVITY_PLAYER =
            "com.unity3d.player.UnityPlayerForActivityOrService";
    private static final String[] UNITY_PLAYER_CLASSES = {
            UNITY6_ACTIVITY_PLAYER,
            "com.unity3d.player.UnityPlayer"
    };

    private static boolean gadgetLoaded;

    private String targetPackage;
    private Context targetContext;
    private HostContextBridge bridge;
    private Object unityPlayer;
    private View unityView;
    private TextView bootStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        targetPackage = getIntent().getStringExtra(EXTRA_TARGET_PACKAGE);
        if (targetPackage == null || targetPackage.trim().isEmpty()) {
            showFatal("Pacote alvo não informado", null);
            return;
        }

        showBootUi("Preparando " + targetPackage + "…");
        try {
            startHostedUnity();
        } catch (Throwable error) {
            showFatal("Falha ao iniciar alvo hospedado", error);
        }
    }

    private void startHostedUnity() throws Exception {
        File script = ScriptStore.ensureDefault(this);
        Log.i(TAG, "Frida script=" + script.getAbsolutePath());

        targetContext = createPackageContext(
                targetPackage,
                Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY
        );
        bridge = new HostContextBridge(targetContext, this);

        ClassLoader targetLoader = targetContext.getClassLoader();
        if (targetLoader == null) {
            throw new IllegalStateException("ClassLoader do alvo é nulo");
        }
        Thread.currentThread().setContextClassLoader(targetLoader);

        updateBoot("Carregando Frida Gadget 17.17.0…");
        loadFridaGadget();

        updateBoot("Localizando UnityPlayer…");
        Class<?> playerClass = findUnityPlayerClass(targetLoader);
        Log.i(TAG, "UnityPlayer=" + playerClass.getName());

        updateBoot("Criando runtime Unity hospedado…");
        unityPlayer = constructUnityPlayer(playerClass);
        unityView = extractUnityView(unityPlayer);
        if (unityView == null) {
            throw new IllegalStateException(
                    "UnityPlayer foi criado, mas nenhuma View compatível foi encontrada"
            );
        }

        unityView.setFocusableInTouchMode(true);
        unityView.requestFocus();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addView(unityView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        Log.i(TAG, "Hosted target started: package=" + targetPackage
                + "; localPackage=" + getPackageName()
                + "; opPackage=" + getOpPackageName()
                + "; uid=" + Process.myUid()
                + "; source=" + getApplicationInfo().sourceDir
                + "; nativeLibDir=" + getApplicationInfo().nativeLibraryDir);

        Toast.makeText(this,
                "Frida Gadget ativo • " + targetPackage,
                Toast.LENGTH_SHORT).show();
    }

    private void loadFridaGadget() {
        if (gadgetLoaded) {
            Log.i(TAG, "Frida Gadget já estava carregado no processo :host");
            return;
        }
        System.loadLibrary("gadget");
        gadgetLoaded = true;
        Log.i(TAG, "Frida Gadget 17.17.0 carregado");
    }

    private Class<?> findUnityPlayerClass(ClassLoader loader) throws ClassNotFoundException {
        List<String> errors = new ArrayList<>();
        for (String name : UNITY_PLAYER_CLASSES) {
            try {
                return Class.forName(name, true, loader);
            } catch (ClassNotFoundException error) {
                errors.add(name);
            }
        }
        throw new ClassNotFoundException("UnityPlayer não encontrado: " + errors);
    }

    private Object constructUnityPlayer(Class<?> playerClass) throws Exception {
        if (UNITY6_ACTIVITY_PLAYER.equals(playerClass.getName())) {
            Object unity6 = constructUnity6Player(playerClass);
            if (unity6 != null) return unity6;
        }

        Constructor<?>[] constructors = playerClass.getDeclaredConstructors();
        Arrays.sort(constructors, Comparator.comparingInt(Constructor::getParameterCount));

        Throwable last = null;
        for (Constructor<?> constructor : constructors) {
            Object[] args = buildConstructorArguments(constructor.getParameterTypes());
            if (args == null) continue;
            try {
                constructor.setAccessible(true);
                Object value = constructor.newInstance(args);
                Log.i(TAG, "Unity constructor=" + constructor);
                return value;
            } catch (Throwable error) {
                last = error;
                Log.w(TAG, "Unity constructor failed: " + constructor, error);
            }
        }

        IllegalStateException failure = new IllegalStateException(
                "Nenhum construtor UnityPlayer compatível"
        );
        if (last != null) failure.initCause(last);
        throw failure;
    }

    private Object constructUnity6Player(Class<?> playerClass) throws Exception {
        ClassLoader loader = targetContext.getClassLoader();
        Class<?> lifecycleType = Class.forName(
                "com.unity3d.player.IUnityPlayerLifecycleEvents", true, loader);

        Object lifecycleProxy = Proxy.newProxyInstance(
                loader,
                new Class<?>[]{lifecycleType},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("onUnityPlayerQuitted".equals(name)) {
                        runOnUiThread(this::finish);
                    } else if ("onUnityPlayerUnloaded".equals(name)) {
                        Log.i(TAG, "Unity unloaded");
                    } else if ("toString".equals(name)) {
                        return "GadgetLoaderLifecycleProxy";
                    } else if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy);
                    } else if ("equals".equals(name)) {
                        return args != null && args.length == 1 && proxy == args[0];
                    }
                    return defaultValue(method.getReturnType());
                }
        );

        Constructor<?> constructor = playerClass.getDeclaredConstructor(
                Context.class, lifecycleType);
        constructor.setAccessible(true);
        return constructor.newInstance(this, lifecycleProxy);
    }

    private Object[] buildConstructorArguments(Class<?>[] types) {
        if (types.length == 0) return new Object[0];

        Object[] args = new Object[types.length];
        boolean hasContext = false;

        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];

            if (type.isInstance(this)) {
                args[i] = this;
                hasContext = true;
                continue;
            }
            if (bridge != null && type.isInstance(bridge)) {
                args[i] = bridge;
                hasContext = true;
                continue;
            }
            if (Context.class.isAssignableFrom(type)) {
                if (type.isAssignableFrom(getClass())) {
                    args[i] = this;
                    hasContext = true;
                    continue;
                }
                if (bridge != null && type.isAssignableFrom(bridge.getClass())) {
                    args[i] = bridge;
                    hasContext = true;
                    continue;
                }
                return null;
            }
            if (type.isInterface() && type.getName().contains("IUnityPlayerLifecycleEvents")) {
                args[i] = Proxy.newProxyInstance(
                        targetContext.getClassLoader(),
                        new Class<?>[]{type},
                        (proxy, method, methodArgs) -> defaultValue(method.getReturnType()));
                continue;
            }
            if (type.isPrimitive()) {
                args[i] = defaultValue(type);
                continue;
            }
            if (type == String.class) {
                args[i] = "";
                continue;
            }
            args[i] = null;
        }

        return hasContext ? args : null;
    }

    private View extractUnityView(Object player) {
        if (player == null) return null;
        if (player instanceof View) return (View) player;

        for (String methodName : new String[]{"getFrameLayout", "getView"}) {
            try {
                Method method = player.getClass().getMethod(methodName);
                method.setAccessible(true);
                Object value = method.invoke(player);
                if (value instanceof View) return (View) value;
            } catch (Throwable error) {
                Log.d(TAG, "Unity view method unavailable: " + methodName, error);
            }
        }
        return null;
    }

    private static Object defaultValue(Class<?> type) {
        if (type == void.class || !type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        return null;
    }

    private boolean invokeUnity(String name, Class<?>[] parameterTypes, Object... args) {
        Object player = unityPlayer;
        if (player == null) return false;

        Class<?> current = player.getClass();
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                method.invoke(player, args);
                return true;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            } catch (Throwable error) {
                Log.w(TAG, "Unity lifecycle call failed: " + name, error);
                return false;
            }
        }
        return false;
    }

    private void invokeUnityAny(String primary, String fallback) {
        if (!invokeUnity(primary, new Class<?>[0]) && fallback != null) {
            invokeUnity(fallback, new Class<?>[0]);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        invokeUnityAny("onStart", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        invokeUnityAny("onResume", "resume");
    }

    @Override
    protected void onPause() {
        invokeUnityAny("onPause", "pause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        invokeUnityAny("onStop", null);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (!invokeUnity("destroy", new Class<?>[0])) {
            if (!invokeUnity("quit", new Class<?>[0])) {
                invokeUnity("shutdown", new Class<?>[0]);
            }
        }
        unityPlayer = null;
        unityView = null;
        super.onDestroy();

        if (isFinishing() && !isChangingConfigurations()) {
            new Thread(() -> {
                try {
                    Thread.sleep(120L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                Process.killProcess(Process.myPid());
            }, "gadget-host-exit").start();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        invokeUnity("windowFocusChanged", new Class<?>[]{boolean.class}, hasFocus);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        invokeUnity("configurationChanged", new Class<?>[]{Configuration.class}, newConfig);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        invokeUnity("lowMemory", new Class<?>[0]);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        invokeUnity("newIntent", new Class<?>[]{Intent.class}, intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Object player = unityPlayer;
        if (player == null) return;
        try {
            Method method = player.getClass().getMethod(
                    "permissionResponse",
                    Activity.class,
                    int.class,
                    String[].class,
                    int[].class);
            method.invoke(player, this, requestCode, permissions, grantResults);
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable error) {
            Log.w(TAG, "Unity permissionResponse failed", error);
        }
    }

    private void showBootUi(String message) {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        ProgressBar progress = new ProgressBar(this);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        root.addView(progress, progressParams);

        bootStatus = new TextView(this);
        bootStatus.setText(message);
        bootStatus.setTextColor(Color.WHITE);
        bootStatus.setTextSize(14f);
        bootStatus.setGravity(Gravity.CENTER);
        bootStatus.setPadding(dp(20), dp(20), dp(20), dp(20));
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        root.addView(bootStatus, statusParams);
        setContentView(root);
    }

    private void updateBoot(String message) {
        if (bootStatus != null) bootStatus.setText(message);
        Log.i(TAG, message);
    }

    private void showFatal(String message, Throwable error) {
        Log.e(TAG, message, error);
        TextView text = new TextView(this);
        String details = error == null ? "" : "\n\n"
                + error.getClass().getSimpleName() + ": " + error.getMessage();
        text.setText(message + details + "\n\nVolte ao GadgetLoader.");
        text.setTextColor(Color.WHITE);
        text.setBackgroundColor(Color.BLACK);
        text.setTextSize(16f);
        text.setGravity(Gravity.CENTER);
        text.setPadding(dp(24), dp(24), dp(24), dp(24));
        setContentView(text);
    }

    @Override
    public AssetManager getAssets() {
        return bridge != null ? bridge.getAssets() : super.getAssets();
    }

    @Override
    public Resources getResources() {
        return bridge != null ? bridge.getResources() : super.getResources();
    }

    @Override
    public ClassLoader getClassLoader() {
        return bridge != null ? bridge.getClassLoader() : super.getClassLoader();
    }

    @Override
    public Context getApplicationContext() {
        return bridge != null ? bridge : super.getApplicationContext();
    }

    @Override
    public ApplicationInfo getApplicationInfo() {
        return bridge != null ? bridge.getApplicationInfo() : super.getApplicationInfo();
    }

    @Override
    public String getPackageName() {
        return bridge != null ? bridge.getPackageName() : super.getPackageName();
    }

    @Override
    public String getOpPackageName() {
        // Never spoof this: Android system services validate it against Binder UID.
        return super.getOpPackageName();
    }

    @Override
    public String getPackageCodePath() {
        return bridge != null ? bridge.getPackageCodePath() : super.getPackageCodePath();
    }

    @Override
    public String getPackageResourcePath() {
        return bridge != null ? bridge.getPackageResourcePath() : super.getPackageResourcePath();
    }

    @Override
    public File getFilesDir() {
        return bridge != null ? bridge.getFilesDir() : super.getFilesDir();
    }

    @Override
    public File getCacheDir() {
        return bridge != null ? bridge.getCacheDir() : super.getCacheDir();
    }

    @Override
    public File getExternalFilesDir(String type) {
        return bridge != null ? bridge.getExternalFilesDir(type) : super.getExternalFilesDir(type);
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return bridge != null ? bridge.getSharedPreferences(name, mode)
                : super.getSharedPreferences(name, mode);
    }

    private int dp(int value) {
        return Math.round(value * super.getResources().getDisplayMetrics().density);
    }
}
