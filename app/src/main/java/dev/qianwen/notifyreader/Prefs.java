package dev.qianwen.notifyreader;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

final class Prefs {
    private final SharedPreferences prefs;
    Prefs(Context context) { prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE); }
    boolean enabled() { return prefs.getBoolean("enabled", false); }
    void enabled(boolean value) { prefs.edit().putBoolean("enabled", value).apply(); }
    Set<String> apps() { return new HashSet<>(prefs.getStringSet("apps", new HashSet<>())); }
    void apps(Set<String> value) { prefs.edit().putStringSet("apps", new HashSet<>(value)).apply(); }
    String route() { return prefs.getString("route", ""); }
    String routeLabel() { return prefs.getString("routeLabel", "尚未选择"); }
    void route(String key, String label) { prefs.edit().putString("route", key).putString("routeLabel", label).apply(); }
    String status() { return prefs.getString("status", "选择应用和音频设备后，先测试朗读。"); }
    void status(String value) { prefs.edit().putString("status", value).apply(); }
}
