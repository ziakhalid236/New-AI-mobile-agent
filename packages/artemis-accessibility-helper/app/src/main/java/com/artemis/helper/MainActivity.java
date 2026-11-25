package com.artemis.helper;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Launcher screen for the helper.
 *
 * The package is an accessibility service, so without this screen it installs
 * with no icon and nothing to open, which reads as "it did not install". The
 * screen reports whether the service and its loopback server are live and hands
 * the tester the one adb command they need.
 */
public class MainActivity extends Activity {

    private static final int REQUEST_NOTIFICATIONS = 1;
    private static final int COLOR_OK = 0xFF1E8E3E;
    private static final int COLOR_WARN = 0xFFB06000;

    private TextView serviceValue;
    private TextView serverValue;
    private TextView tokenValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildLayout());
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, REQUEST_NOTIFICATIONS);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));

        TextView title = new TextView(this);
        title.setText("Artemis Accessibility Helper");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Version " + serviceVersion());
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        subtitle.setPadding(0, dp(4), 0, dp(20));
        root.addView(subtitle);

        serviceValue = addRow(root, "Accessibility service");
        serverValue = addRow(root, "Loopback server (127.0.0.1:" + ArtemisAccessibilityService.DEFAULT_PORT + ")");
        tokenValue = addRow(root, "Session token");

        Button openSettings = new Button(this);
        openSettings.setText("Open accessibility settings");
        openSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAccessibilitySettings();
            }
        });
        root.addView(openSettings, buttonParams());

        Button copyCommand = new Button(this);
        copyCommand.setText("Copy adb command for the token");
        copyCommand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyTokenCommand();
            }
        });
        root.addView(copyCommand, buttonParams());

        Button refresh = new Button(this);
        refresh.setText("Refresh status");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshStatus();
            }
        });
        root.addView(refresh, buttonParams());

        TextView steps = new TextView(this);
        steps.setText("How to use\n\n"
                + "1. Turn the service on in accessibility settings.\n"
                + "2. On the computer, run `artemis helper install` (or `adb install` this APK).\n"
                + "3. Artemis pushes the session token over adb; the row above turns green once it arrives.\n\n"
                + "The helper only answers on this device's loopback interface, only with the session token, "
                + "and never sends data anywhere.");
        steps.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        steps.setPadding(0, dp(24), 0, 0);
        root.addView(steps);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        return scroll;
    }

    private TextView addRow(LinearLayout parent, String label) {
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        name.setPadding(0, dp(10), 0, 0);
        parent.addView(name);

        TextView value = new TextView(this);
        value.setText("checking...");
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        value.setPadding(0, dp(2), 0, dp(6));
        parent.addView(value);
        return value;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        return params;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private void refreshStatus() {
        boolean enabled = isServiceEnabled();
        setValue(serviceValue, enabled ? "enabled" : "disabled - tap the button below", enabled);

        setValue(serverValue, "checking...", false);
        final int port = ArtemisAccessibilityService.DEFAULT_PORT;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean reachable = canConnect(port);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setValue(serverValue, reachable ? "listening" : "not listening yet",
                                reachable);
                    }
                });
            }
        }).start();

        boolean token = TokenStore.isSet();
        setValue(tokenValue, token ? "received" : "not received yet", token);
    }

    private void setValue(TextView view, String text, boolean ok) {
        if (view == null) {
            return;
        }
        view.setText(text);
        view.setTextColor(ok ? COLOR_OK : COLOR_WARN);
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null || enabled.isEmpty()) {
            return false;
        }
        String target = getPackageName() + "/" + ArtemisAccessibilityService.class.getName();
        for (String entry : enabled.split(":")) {
            if (entry.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    private boolean canConnect(int port) {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 400);
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Nothing useful to do if the probe socket refuses to close.
            }
        }
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (RuntimeException e) {
            Toast.makeText(this, "No accessibility settings screen on this device",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void copyTokenCommand() {
        String command = "adb shell am broadcast -n " + getPackageName() + "/.TokenReceiver"
                + " -a " + TokenReceiver.ACTION_SET_TOKEN + " --es token <TOKEN>";
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, command, Toast.LENGTH_LONG).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Artemis token command", command));
        Toast.makeText(this, "adb command copied", Toast.LENGTH_SHORT).show();
    }

    private String serviceVersion() {
        ArtemisAccessibilityService service = ArtemisAccessibilityService.getInstance();
        if (service != null) {
            String name = service.getVersionName();
            long code = service.getVersionCode();
            if (name != null && !name.isEmpty()) {
                return name + " (" + code + ")";
            }
        }
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }
}
