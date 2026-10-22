package dev.qianwen.notifyreader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import java.io.File;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ReadingService extends Service {
    static final String TEST = "dev.qianwen.notifyreader.TEST";
    static final String STOP = "dev.qianwen.notifyreader.STOP";
    static final String SILENCE = "dev.qianwen.notifyreader.SILENCE";
    static volatile ReadingService instance;
    private static final String CHANNEL = "reader";
    private static final int NOTICE = 1;
    private final long startedWall = System.currentTimeMillis();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<Part> queue = new ArrayDeque<>();
    private final ReadingRules rules = new ReadingRules();
    private Prefs prefs;
    private AudioManager audio;
    private TextToSpeech tts;
    private AudioFocusRequest focus;
    private MediaPlayer player;
    private boolean ready, initialized, routeVerified;
    private String currentId = "";
    private File currentFile;
    private long routeStarted;
    private final AudioAttributes attributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    private final AudioDeviceCallback devices = new AudioDeviceCallback() {
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
            if (Routes.selected(ReadingService.this) == null) fail("眼镜已断开，停止并清空待播通知。");
        }
    };
    private final Runnable routeCheck = new Runnable() {
        @Override public void run() {
            MediaPlayer active = player;
            if (active == null) return;
            try {
                AudioDeviceInfo routed = active.getRoutedDevice();
                if (Routes.selected(ReadingService.this) == null) { fail("所选设备已断开，已停止。"); return; }
                if (routed != null && !Routes.matches(ReadingService.this, routed)) {
                    fail("音频输出与所选设备不一致，已停止。"); return;
                }
                if (routed != null && !routeVerified) {
                    routeVerified = true;
                    active.seekTo(0, MediaPlayer.SEEK_CLOSEST);
                    active.setVolume(1, 1);
                    prefs.status("正在通过所选设备朗读。");
                }
                if (!routeVerified && SystemClock.elapsedRealtime() - routeStarted > 1800) {
                    fail("无法确认眼镜音频路由，请检查媒体音频输出。"); return;
                }
                handler.postDelayed(this, 120);
            } catch (IllegalStateException e) { fail("音频播放状态异常，请重新测试。"); }
        }
    };

    static void offer(String source, String key, String app, String title, String body, long date) {
        ReadingService service = instance;
        if (service != null) service.handler.post(() -> {
            if (instance == service) service.receive(source, key, app, title, body, date);
        });
    }
    static boolean acceptsPost(long postedAt) {
        ReadingService service = instance;
        return service != null && postedAt >= service.startedWall - 2000;
    }
    static void stopSpeech() {
        ReadingService service = instance;
        if (service != null) service.handler.post(() -> service.fail("已停止朗读，待播队列已清空。"));
    }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        prefs = new Prefs(this);
        audio = getSystemService(AudioManager.class);
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(CHANNEL, "通知朗读运行状态", NotificationManager.IMPORTANCE_LOW));
        foreground(false);
        audio.registerAudioDeviceCallback(devices, handler);
        cleanupCache();
        tts = new TextToSpeech(getApplicationContext(), status -> handler.post(() -> initializeTts(status)));
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) { handler.post(() -> synthesized(id)); }
            @Override public void onError(String id) { handler.post(() -> {
                if (id.equals(currentId)) fail("语音合成失败，请检查系统中文 TTS 引擎。");
                else new File(getCacheDir(), id + ".wav").delete();
            }); }
        });
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if (STOP.equals(action)) {
            prefs.enabled(false);
            fail("通知朗读已关闭。");
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (SILENCE.equals(action)) fail("已停止当前朗读，继续监听新通知。");
        if (TEST.equals(action)) enqueue("这是通知随听的测试。如果你在眼镜里听到了这句话，音频连接已成功。", true);
        else if (!prefs.enabled()) { stopSelf(); return START_NOT_STICKY; }
        else prefs.status(ready ? "已开启，等待所选应用的新通知。" : "正在初始化系统中文语音引擎……");
        return prefs.enabled() ? START_STICKY : START_NOT_STICKY;
    }

    private void initializeTts(int status) {
        if (instance != this) return;
        initialized = true;
        if (status != TextToSpeech.SUCCESS) { fail("系统 TTS 无法启动，请配置中文语音引擎。"); return; }
        int language = tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            fail("系统缺少中文语音，请在手机的文字转语音设置中安装或选择中文。"); return;
        }
        // Prefer an installed offline Chinese voice; otherwise retain the system's selected voice.
        if (tts.getVoices() != null) for (Voice voice : tts.getVoices()) {
            if (voice.getLocale().getLanguage().equals("zh") && !voice.isNetworkConnectionRequired()) {
                tts.setVoice(voice); break;
            }
        }
        tts.setSpeechRate(1.0f);
        ready = true;
        prefs.status("中文语音已准备好。" + (prefs.enabled() ? "正在等待新通知。" : ""));
        next();
    }

    private void receive(String source, String key, String app, String title, String body, long date) {
        if (!prefs.enabled() || !prefs.apps().contains(source) || body.isEmpty()) return;
        if (date > 0 && date < startedWall - 2000) return;
        if (Routes.selected(this) == null) { prefs.status("所选眼镜未连接，本次通知已跳过。"); return; }
        if (!rules.accept(source, key, title + "\n" + body, date, SystemClock.elapsedRealtime())) return;
        enqueue(ReadingRules.spoken(app, title, body), false);
    }

    private void enqueue(String text, boolean test) {
        if (Routes.selected(this) == null) {
            prefs.status("请连接所选蓝牙设备，并在蓝牙设置中开启媒体音频。");
            if (!prefs.enabled()) stopSelf();
            return;
        }
        if (initialized && !ready) { prefs.status("中文语音尚不可用，请先配置系统 TTS。"); return; }
        if (!test && text.length() > 12000) { prefs.status("本次通知超过可朗读长度，已跳过。"); return; }
        List<String> parts = ReadingRules.chunks(text, 450);
        if (parts.size() + queue.size() > 80) { prefs.status("待播队列已满，本次通知已跳过。"); return; }
        long now = SystemClock.elapsedRealtime();
        for (String part : parts) queue.add(new Part(part, now));
        next();
    }

    private void next() {
        if (!ready || !currentId.isEmpty()) return;
        while (!queue.isEmpty() && SystemClock.elapsedRealtime() - queue.peek().time > 300_000L) queue.remove();
        if (queue.isEmpty()) {
            foreground(false);
            prefs.status("本次朗读完成。" + (prefs.enabled() ? "继续等待新通知。" : ""));
            if (!prefs.enabled()) stopSelf();
            return;
        }
        if (Routes.selected(this) == null) { fail("设备已断开，待播通知已清空。"); return; }
        Part part = queue.remove();
        currentId = "speech-" + UUID.randomUUID();
        currentFile = new File(getCacheDir(), currentId + ".wav");
        prefs.status("正在准备语音……");
        handler.postDelayed(synthesisTimeout, 30_000);
        if (tts.synthesizeToFile(part.text, new Bundle(), currentFile, currentId) != TextToSpeech.SUCCESS)
            fail("语音合成请求失败，请检查系统 TTS。");
    }

    private final Runnable synthesisTimeout = () -> fail("语音引擎响应超时，本次队列已清空。");
    private void synthesized(String id) {
        if (!id.equals(currentId) || instance != this) { new File(getCacheDir(), id + ".wav").delete(); return; }
        handler.removeCallbacks(synthesisTimeout);
        AudioDeviceInfo target = Routes.selected(this);
        if (target == null) { fail("设备已断开，本次朗读取消。"); return; }
        try {
            WavPadding.add(currentFile, 350);
            foreground(true);
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attributes).setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(change -> {
                        if (change < 0) fail("音频被通话或其他应用占用，本次朗读已停止。");
                    }, handler).build();
            if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                fail("暂时无法取得音频播放权限，本次朗读已跳过。"); return;
            }
            player = new MediaPlayer();
            MediaPlayer active = player;
            active.setAudioAttributes(attributes);
            active.setDataSource(currentFile.getAbsolutePath());
            active.setVolume(0, 0);
            if (!active.setPreferredDevice(target)) { fail("系统未接受所选音频设备。"); return; }
            active.addOnRoutingChangedListener(router -> {
                if (player == active && routeVerified && !Routes.matches(this, active.getRoutedDevice()))
                    fail("音频路由发生变化，已停止朗读。");
            }, handler);
            active.setOnErrorListener((p, what, extra) -> { fail("音频播放失败，请重新测试。"); return true; });
            active.setOnCompletionListener(p -> complete(active));
            active.setOnPreparedListener(p -> {
                if (player != active || Routes.selected(this) == null) { fail("设备状态变化，已停止。"); return; }
                routeVerified = false;
                routeStarted = SystemClock.elapsedRealtime();
                active.start();
                handler.post(routeCheck);
            });
            active.prepareAsync();
        } catch (Exception e) { fail("无法播放合成语音，请检查 TTS 与音频设备。"); }
    }

    private void complete(MediaPlayer active) {
        if (player != active) return;
        if (!routeVerified) { fail("本次未能确认眼镜音频路由，请重新测试。"); return; }
        releaseCurrent();
        next();
    }
    private void fail(String message) {
        queue.clear();
        if (tts != null) tts.stop();
        releaseCurrent();
        if (prefs != null) {
            prefs.status(message);
            if (instance == this) foreground(false);
            if (!prefs.enabled()) stopSelf();
        }
    }
    private void releaseCurrent() {
        handler.removeCallbacks(routeCheck);
        handler.removeCallbacks(synthesisTimeout);
        MediaPlayer old = player;
        player = null;
        if (old != null) { try { old.setVolume(0, 0); old.release(); } catch (Exception ignored) { } }
        if (focus != null) { audio.abandonAudioFocusRequest(focus); focus = null; }
        if (currentFile != null) { currentFile.delete(); currentFile = null; }
        currentId = "";
        routeVerified = false;
    }

    private void foreground(boolean playing) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, ReadingService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notice = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_app)
                .setContentTitle(playing ? "通知随听 · 正在朗读" : "通知随听 · 等待通知")
                .setContentText("仅处理你选定的应用，点此打开设置。")
                .setContentIntent(content).setOngoing(true).setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(null, "关闭朗读", stop).build()).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTICE, notice,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE | (playing ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0));
        else if (Build.VERSION.SDK_INT >= 29) startForeground(NOTICE, notice, playing ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0);
        else startForeground(NOTICE, notice);
    }
    private void cleanupCache() {
        File[] files = getCacheDir().listFiles((dir, name) -> name.startsWith("speech-") && name.endsWith(".wav"));
        if (files != null) for (File file : files) file.delete();
    }
    @Override public void onDestroy() {
        if (instance == this) instance = null;
        handler.removeCallbacksAndMessages(null);
        queue.clear();
        if (audio != null) audio.unregisterAudioDeviceCallback(devices);
        if (tts != null) { tts.stop(); tts.shutdown(); }
        releaseCurrent();
        cleanupCache();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    private static final class Part {
        final String text;
        final long time;
        Part(String text, long time) { this.text = text; this.time = time; }
    }
}
