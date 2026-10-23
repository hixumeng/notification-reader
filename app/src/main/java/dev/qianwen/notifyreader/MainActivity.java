package dev.qianwen.notifyreader;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioDeviceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler();
    private Prefs prefs;
    private TextView state, detail, appsLabel, deviceLabel;
    private Switch enabled;
    private boolean updating;
    private final int ink = Color.rgb(27, 39, 34), muted = Color.rgb(91, 104, 96);
    private final Runnable refresh = new Runnable() {
        @Override public void run() { updateStatus(); handler.postDelayed(this, 1000); }
    };

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        prefs = new Prefs(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(20), dp(22), dp(28));
        page.setBackgroundColor(Color.rgb(246, 247, 244));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll);
        page.addView(text("通知随听", 30, true));
        TextView sub = text("把选定应用的通知，读到你的眼镜里。", 14, false);
        sub.setTextColor(muted); page.addView(sub); gap(page, 24);

        LinearLayout statusCard = card(page);
        state = text("尚未开启", 19, true); statusCard.addView(state);
        detail = text("", 14, false); detail.setTextColor(muted); statusCard.addView(detail);
        enabled = new Switch(this);
        enabled.setText("开启通知朗读"); enabled.setTextSize(17); enabled.setPadding(0, dp(16), 0, 0);
        statusCard.addView(enabled);
        enabled.setOnCheckedChangeListener((button, checked) -> {
            if (updating) return;
            if (checked) enableReader();
            else { prefs.enabled(false); command(ReadingService.STOP); updateStatus(); }
        });
        gap(page, 16);

        LinearLayout config = card(page);
        config.addView(text("1  通知来源", 16, true));
        appsLabel = text("", 14, false); appsLabel.setTextColor(muted); config.addView(appsLabel);
        config.addView(button("选择应用", v -> chooseApps()));
        gap(config, 18);
        config.addView(text("2  蓝牙音频设备", 16, true));
        deviceLabel = text("", 14, false); deviceLabel.setTextColor(muted); config.addView(deviceLabel);
        config.addView(button("选择已连接的眼镜", v -> chooseDevice()));
        TextView routeNote = text("先连接眼镜并开启媒体音频。设备断开时跳过通知，连接后不补读。", 12, false);
        routeNote.setTextColor(muted); config.addView(routeNote);
        TextView callNote = text("来电或通话时暂停朗读，期间通知不补读。挂断后等待音频恢复，再朗读新通知。", 12, false);
        callNote.setTextColor(muted); config.addView(callNote);
        gap(page, 16);

        LinearLayout access = card(page);
        access.addView(text("3  授权与测试", 16, true));
        access.addView(button("打开通知访问设置", v -> notificationSettings()));
        access.addView(button("测试眼镜朗读", v -> {
            if (prefs.route().isEmpty()) { toast("请先选择已连接的蓝牙眼镜。"); return; }
            if (!postPermission()) return;
            command(ReadingService.TEST);
            toast("正在准备测试语音，请稍等。");
        }));
        access.addView(button("停止当前朗读", v -> ReadingService.stopSpeech()));
        access.addView(button("澎湃 OS 后台运行说明", v -> backgroundHelp()));
        gap(page, 20);

        TextView privacy = text("本应用无联网权限，不保存通知历史。语音使用手机的 TTS 引擎，优先选择可用的离线中文声音；引擎自身是否联网取决于它的设置。", 12, false);
        privacy.setTextColor(muted); page.addView(privacy);
        TextView version = text("v0.1.1 · Android 9 及以上 · 个人测试版", 12, false);
        version.setPadding(0, dp(12), 0, 0); version.setTextColor(muted); page.addView(version);
    }

    private boolean accessGranted() {
        return getSystemService(NotificationManager.class).isNotificationListenerAccessGranted(new ComponentName(this, NotificationAccess.class));
    }
    private boolean postPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
            toast("请允许运行状态通知，再点一次按钮。");
            return false;
        }
        return true;
    }
    private void enableReader() {
        if (!accessGranted()) { toast("先授权“通知随听”的通知访问权限。"); updateStatus(); notificationSettings(); return; }
        if (prefs.apps().isEmpty()) { toast("请先选择 Muse 或其他要朗读的应用。"); updateStatus(); return; }
        if (prefs.route().isEmpty()) { toast("请先选择眼镜音频设备。"); updateStatus(); return; }
        if (!postPermission()) { updateStatus(); return; }
        prefs.enabled(true);
        command(null);
        android.service.notification.NotificationListenerService.requestRebind(new ComponentName(this, NotificationAccess.class));
        updateStatus();
    }
    private void command(String action) {
        try {
            if (ReadingService.STOP.equals(action) && ReadingService.instance == null) { updateStatus(); return; }
            Intent intent = new Intent(this, ReadingService.class);
            if (action != null) intent.setAction(action);
            startForegroundService(intent);
        } catch (RuntimeException e) {
            prefs.enabled(false);
            prefs.status("系统暂未允许后台朗读，请检查应用权限并重新开启。");
            toast("无法启动朗读服务，请检查权限。");
        }
    }
    private void updateStatus() {
        boolean access = accessGranted();
        boolean running = ReadingService.instance != null;
        updating = true;
        enabled.setChecked(prefs.enabled() && running);
        updating = false;
        if (!access) state.setText("需要通知访问授权");
        else if (!running || !prefs.enabled()) state.setText("通知朗读已关闭");
        else if (!NotificationAccess.connected) state.setText("正在等待系统连接通知服务");
        else if (Routes.selected(this) == null) state.setText("已开启 · 等待眼镜连接");
        else state.setText("已开启 · 眼镜已连接");
        detail.setText(prefs.status());
        Set<String> selected = prefs.apps();
        List<String> labels = new ArrayList<>();
        for (String name : selected) {
            try { labels.add(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(name, 0)).toString()); }
            catch (Exception e) { labels.add(name + "（可能已卸载）"); }
        }
        appsLabel.setText(labels.isEmpty() ? "未选择；默认不读取任何应用。" : String.join("、", labels));
        deviceLabel.setText(prefs.routeLabel() + (Routes.selected(this) == null ? " · 未连接" : " · 已连接"));
    }

    private void chooseDevice() {
        List<AudioDeviceInfo> devices = Routes.outputs(this);
        if (devices.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("还没有蓝牙媒体音频设备")
                    .setMessage("请先连接 N1 Pro，在手机蓝牙设置中为它开启“媒体音频”，再返回选择。")
                    .setPositiveButton("蓝牙设置", (d, w) -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)))
                    .setNegativeButton("返回", null).show();
            return;
        }
        String[] names = new String[devices.size()];
        for (int i = 0; i < names.length; i++) names[i] = devices.get(i).getProductName().toString();
        new AlertDialog.Builder(this).setTitle("选择朗读使用的设备").setItems(names, (dialog, which) -> {
            ReadingService.stopSpeech();
            prefs.route(Routes.key(devices.get(which)), names[which]);
            updateStatus();
        }).setNegativeButton("取消", null).show();
    }

    private void chooseApps() {
        toast("正在读取可选应用……");
        new Thread(() -> {
            LinkedHashMap<String, AppEntry> found = new LinkedHashMap<>();
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            for (ResolveInfo item : getPackageManager().queryIntentActivities(launcher, 0)) {
                String pkg = item.activityInfo.packageName;
                if (!pkg.equals(getPackageName())) found.put(pkg, new AppEntry(pkg, item.loadLabel(getPackageManager()).toString()));
            }
            List<AppEntry> all = new ArrayList<>(found.values());
            all.sort(Comparator.comparing(item -> item.label.toLowerCase(LocaleHolder.LOCALE)));
            runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) showApps(all); });
        }, "list-apps").start();
    }
    private void showApps(List<AppEntry> all) {
        Set<String> chosen = prefs.apps();
        List<AppEntry> visible = new ArrayList<>(all);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(18), 0, dp(18), 0);
        EditText search = new EditText(this);
        search.setSingleLine(true); search.setHint("搜索 Muse 或应用名"); panel.addView(search);
        ListView list = new ListView(this);
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        ArrayAdapter<AppEntry> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, visible);
        list.setAdapter(adapter); panel.addView(list, new LinearLayout.LayoutParams(-1, dp(310)));
        Runnable checked = () -> { for (int i = 0; i < visible.size(); i++) list.setItemChecked(i, chosen.contains(visible.get(i).pkg)); };
        list.setOnItemClickListener((parent, view, position, id) -> {
            String pkg = visible.get(position).pkg;
            if (list.isItemChecked(position)) chosen.add(pkg); else chosen.remove(pkg);
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int f) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim().toLowerCase(LocaleHolder.LOCALE);
                visible.clear();
                for (AppEntry entry : all) if ((entry.label + " " + entry.pkg).toLowerCase(LocaleHolder.LOCALE).contains(query)) visible.add(entry);
                adapter.notifyDataSetChanged(); list.clearChoices(); checked.run();
            }
            @Override public void afterTextChanged(Editable text) { }
        });
        checked.run();
        new AlertDialog.Builder(this).setTitle("选择要朗读的应用").setView(panel)
                .setPositiveButton("保存", (dialog, which) -> { ReadingService.stopSpeech(); prefs.apps(chosen); updateStatus(); })
                .setNegativeButton("取消", null).show();
    }

    private void notificationSettings() {
        try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
        catch (RuntimeException e) { appSettings(); }
    }
    private void backgroundHelp() {
        new AlertDialog.Builder(this).setTitle("澎湃 OS 设置")
                .setMessage("在手机设置中搜索并为“通知随听”配置：\n\n• 通知使用权：允许读取通知\n• 自启动：允许\n• 应用省电策略：无限制\n\n保持 Muse 通知开启。菜单名称可能随系统版本不同。若提示“受限设置”，在应用信息中查看是否有“允许受限设置”，只为你确认来源的本应用授权。\n\n重启手机后，请打开本应用并重新开启朗读。通知访问关闭后，需重新授权。")
                .setPositiveButton("应用信息", (dialog, which) -> appSettings()).setNegativeButton("知道了", null).show();
    }
    private void appSettings() { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(sp); view.setTextColor(ink);
        view.setLineSpacing(dp(4), 1); view.setPadding(0, dp(4), 0, dp(4));
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }
    private Button button(String label, View.OnClickListener click) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setOnClickListener(click);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52)); params.topMargin = dp(8); button.setLayoutParams(params);
        return button;
    }
    private LinearLayout card(LinearLayout page) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18), dp(14), dp(18), dp(16));
        GradientDrawable shape = new GradientDrawable(); shape.setColor(Color.WHITE); shape.setCornerRadius(dp(18)); box.setBackground(shape);
        page.addView(box, new LinearLayout.LayoutParams(-1, -2)); return box;
    }
    private void gap(LinearLayout page, int dp) { View gap = new View(this); page.addView(gap, new LinearLayout.LayoutParams(1, dp(dp))); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    private static final class AppEntry {
        final String pkg, label;
        AppEntry(String pkg, String label) { this.pkg = pkg; this.label = label; }
        @Override public String toString() { return label + "\n" + pkg; }
    }
    private static final class LocaleHolder { static final java.util.Locale LOCALE = java.util.Locale.ROOT; }
}
