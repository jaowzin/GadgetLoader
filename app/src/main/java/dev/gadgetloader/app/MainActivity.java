package dev.gadgetloader.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQUEST_SCRIPT = 3107;

    private static final int BG = Color.rgb(7, 10, 17);
    private static final int CARD = Color.rgb(16, 23, 35);
    private static final int CARD_ALT = Color.rgb(22, 31, 46);
    private static final int TEXT = Color.rgb(244, 248, 255);
    private static final int MUTED = Color.rgb(145, 160, 181);
    private static final int ACCENT = Color.rgb(80, 200, 255);
    private static final int GREEN = Color.rgb(65, 220, 160);

    private final List<TargetApp> targets = new ArrayList<>();

    private Spinner targetSpinner;
    private TextView status;
    private TextView targetDetails;
    private TextView scriptPath;
    private ProgressBar progress;
    private Button launchButton;
    private Button importButton;
    private Button restoreButton;
    private Button scanButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);

        setContentView(buildUi());
        prepareScript();
        scanTargets();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(28));
        root.setBackgroundColor(BG);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView overline = text("HOSTED INSTRUMENTATION // ARM64", 12f, ACCENT, Typeface.BOLD);
        overline.setLetterSpacing(0.12f);
        root.addView(overline);

        TextView title = text("GadgetLoader", 32f, TEXT, Typeface.BOLD);
        title.setPadding(0, dp(4), 0, 0);
        root.addView(title);

        TextView subtitle = text("Frida Gadget 17.17.0 • Hosted-only • v0.1.0",
                14f, MUTED, Typeface.NORMAL);
        subtitle.setPadding(0, dp(4), 0, dp(20));
        root.addView(subtitle);

        LinearLayout targetCard = card();
        root.addView(targetCard, matchWrap(0));

        TextView targetHeader = text("ALVO UNITY INSTALADO", 12f, MUTED, Typeface.BOLD);
        targetHeader.setLetterSpacing(0.10f);
        targetCard.addView(targetHeader);

        targetSpinner = new Spinner(this);
        targetSpinner.setBackgroundColor(CARD_ALT);
        LinearLayout.LayoutParams spinnerParams = matchWrap(dp(12));
        spinnerParams.height = dp(58);
        targetCard.addView(targetSpinner, spinnerParams);

        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(26), dp(26));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.setMargins(0, dp(14), 0, 0);
        targetCard.addView(progress, progressParams);

        status = text("Escaneando apps Unity…", 13f, MUTED, Typeface.NORMAL);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(10), 0, 0);
        targetCard.addView(status);

        targetDetails = text("", 13f, MUTED, Typeface.NORMAL);
        targetDetails.setPadding(0, dp(14), 0, 0);
        targetDetails.setLineSpacing(0f, 1.18f);
        targetCard.addView(targetDetails);

        LinearLayout scriptCard = card();
        root.addView(scriptCard, matchWrap(dp(14)));

        TextView scriptHeader = text("SCRIPT DO GADGET", 12f, MUTED, Typeface.BOLD);
        scriptHeader.setLetterSpacing(0.10f);
        scriptCard.addView(scriptHeader);

        TextView scriptInfo = text(
                "O Frida roda main.js dentro do mesmo processo que hospeda o jogo. "
                        + "Ao substituir o arquivo, on_change=reload recarrega o script.",
                13f, TEXT, Typeface.NORMAL);
        scriptInfo.setPadding(0, dp(10), 0, 0);
        scriptInfo.setLineSpacing(0f, 1.15f);
        scriptCard.addView(scriptInfo);

        scriptPath = text("Preparando main.js…", 11f, MUTED, Typeface.NORMAL);
        scriptPath.setPadding(0, dp(10), 0, 0);
        scriptPath.setTextIsSelectable(true);
        scriptCard.addView(scriptPath);

        importButton = button("IMPORTAR / RECARREGAR JS", false);
        importButton.setOnClickListener(v -> chooseScript());
        scriptCard.addView(importButton, buttonParams(dp(14)));

        restoreButton = button("Restaurar script padrão", false);
        restoreButton.setOnClickListener(v -> restoreDefaultScript());
        scriptCard.addView(restoreButton, buttonParams(dp(8)));

        launchButton = button("INICIAR HOSTED + FRIDA", true);
        launchButton.setEnabled(false);
        launchButton.setOnClickListener(v -> launchSelected());
        root.addView(launchButton, buttonParams(dp(18)));

        scanButton = button("Reescanear apps", false);
        scanButton.setOnClickListener(v -> scanTargets());
        root.addView(scanButton, buttonParams(dp(10)));

        TextView warning = text(
                "Hosted-only: o código/Unity do alvo roda dentro do processo :host do GadgetLoader. "
                        + "O APK alvo não é alterado. UID, assinatura e chamadas Binder continuam pertencendo ao GadgetLoader.",
                12f, MUTED, Typeface.NORMAL);
        warning.setPadding(0, dp(18), 0, 0);
        warning.setLineSpacing(0f, 1.15f);
        root.addView(warning);

        return scroll;
    }

    private void prepareScript() {
        try {
            File file = ScriptStore.ensureDefault(this);
            scriptPath.setText("main.js: " + file.getAbsolutePath()
                    + "\nConfig Frida: " + ScriptStore.GADGET_SCRIPT_PATH);
        } catch (Throwable error) {
            scriptPath.setText("Erro preparando script: " + error.getMessage());
            Toast.makeText(this, "Falha ao preparar main.js", Toast.LENGTH_LONG).show();
        }
    }

    private void scanTargets() {
        progress.setVisibility(View.VISIBLE);
        status.setText("Escaneando apps Unity instalados…");
        scanButton.setEnabled(false);
        launchButton.setEnabled(false);

        new Thread(() -> {
            List<TargetApp> found = TargetScanner.scan(this);
            runOnUiThread(() -> showTargets(found));
        }, "gadget-target-scan").start();
    }

    private void showTargets(List<TargetApp> found) {
        targets.clear();
        targets.addAll(found);
        progress.setVisibility(View.GONE);
        scanButton.setEnabled(true);

        List<String> labels = new ArrayList<>();
        for (TargetApp target : targets) {
            String suffix = target.isHostable() ? "  •  READY" : "  •  ARM64 necessário";
            labels.add(target.label + suffix + "\n" + target.packageName);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, labels) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(TEXT);
                view.setTextSize(14f);
                view.setPadding(dp(10), 0, dp(10), 0);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(Color.BLACK);
                view.setTextSize(14f);
                view.setPadding(dp(12), dp(10), dp(12), dp(10));
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        targetSpinner.setAdapter(adapter);
        targetSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                       int position, long id) {
                renderTarget(position);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                launchButton.setEnabled(false);
            }
        });

        if (targets.isEmpty()) {
            status.setText("Nenhum app Unity foi detectado.");
            targetDetails.setText("O v0.1 hospeda Unity ARM64 (IL2CPP/Mono). Apps Android arbitrários ainda não possuem host genérico.");
            launchButton.setEnabled(false);
        } else {
            status.setText(String.format(Locale.ROOT, "%d alvo(s) Unity encontrado(s)", targets.size()));
            renderTarget(0);
        }
    }

    private void renderTarget(int position) {
        if (position < 0 || position >= targets.size()) return;
        TargetApp target = targets.get(position);
        targetDetails.setText(
                "Pacote: " + target.packageName
                        + "\nVersão: " + target.versionName
                        + "\nBackend: " + target.backendLabel()
                        + "\nABI ARM64: " + (target.arm64 ? "sim" : "não")
                        + "\nAPKs/splits: " + target.apkCount);
        launchButton.setEnabled(target.isHostable());
    }

    private void chooseScript() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/javascript", "text/javascript", "text/plain"
        });
        startActivityForResult(intent, REQUEST_SCRIPT);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_SCRIPT || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        try {
            File file = ScriptStore.importFromUri(this, uri);
            scriptPath.setText("main.js: " + file.getAbsolutePath()
                    + "\nAtualizado agora • Frida reload automático se o host estiver ativo");
            Toast.makeText(this, "main.js atualizado", Toast.LENGTH_SHORT).show();
        } catch (Throwable error) {
            Toast.makeText(this, "Erro importando JS: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void restoreDefaultScript() {
        try {
            File file = ScriptStore.writeDefault(this, true);
            scriptPath.setText("main.js padrão restaurado: " + file.getAbsolutePath());
            Toast.makeText(this, "Script padrão restaurado", Toast.LENGTH_SHORT).show();
        } catch (Throwable error) {
            Toast.makeText(this, "Erro restaurando JS: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void launchSelected() {
        int position = targetSpinner.getSelectedItemPosition();
        if (position < 0 || position >= targets.size()) return;
        TargetApp target = targets.get(position);
        if (!target.isHostable()) {
            Toast.makeText(this, "O alvo precisa ser Unity ARM64", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            ScriptStore.ensureDefault(this);
        } catch (Throwable error) {
            Toast.makeText(this, "main.js indisponível: " + error.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        Intent intent = new Intent(this, HostActivity.class);
        intent.putExtra(HostActivity.EXTRA_TARGET_PACKAGE, target.packageName);
        startActivity(intent);
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(18), dp(18), dp(18));
        layout.setBackgroundColor(CARD);
        return layout;
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", style));
        return view;
    }

    private Button button(String label, boolean primary) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(primary ? 15f : 14f);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(primary ? Color.rgb(5, 18, 25) : TEXT);
        button.setBackgroundColor(primary ? ACCENT : CARD_ALT);
        button.setGravity(Gravity.CENTER);
        return button;
    }

    private LinearLayout.LayoutParams matchWrap(int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(topMarginDp), 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams buttonParams(int topMarginDp) {
        LinearLayout.LayoutParams params = matchWrap(topMarginDp);
        params.height = dp(54);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
