package dev.qianwen.notifyreader;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public final class NotificationAccess extends NotificationListenerService {
    static volatile boolean connected;
    @Override public void onListenerConnected() { connected = true; }
    @Override public void onListenerDisconnected() {
        connected = false;
        ReadingService.stopSpeech();
    }
    @Override public void onDestroy() { connected = false; super.onDestroy(); }
    @Override public void onNotificationPosted(StatusBarNotification event) {
        if (event == null || event.getPackageName().equals(getPackageName())) return;
        Prefs prefs = new Prefs(this);
        if (!prefs.enabled() || !prefs.apps().contains(event.getPackageName())) return;
        if (!ReadingService.acceptsPost(event.getPostTime())) return;
        Notification notification = event.getNotification();
        if ((notification.flags & (Notification.FLAG_GROUP_SUMMARY | Notification.FLAG_ONGOING_EVENT)) != 0) return;
        if (notification.extras == null) return;
        String label = event.getPackageName();
        try { label = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(label, 0)).toString(); }
        catch (Exception ignored) { }
        Bundle extras = notification.extras;
        String title = ReadingRules.clean(extras.getCharSequence(Notification.EXTRA_TITLE));
        android.os.Parcelable[] rawMessages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (rawMessages != null && rawMessages.length > 0) {
            for (Notification.MessagingStyle.Message message : Notification.MessagingStyle.Message.getMessagesFromBundleArray(rawMessages)) {
                String sender = ReadingRules.clean(message.getSender());
                String body = ReadingRules.clean(message.getText());
                ReadingService.offer(event.getPackageName(), event.getKey(), label,
                        sender.isEmpty() ? title : sender, body, message.getTimestamp());
            }
            return;
        }
        String body = ReadingRules.clean(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if (body.isEmpty()) body = ReadingRules.clean(extras.getCharSequence(Notification.EXTRA_TEXT));
        if (body.isEmpty()) {
            CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lines != null) {
                StringBuilder joined = new StringBuilder();
                for (CharSequence line : lines) joined.append(ReadingRules.clean(line)).append("。");
                body = joined.toString();
            }
        }
        ReadingService.offer(event.getPackageName(), event.getKey(), label, title, body, 0);
    }
}
