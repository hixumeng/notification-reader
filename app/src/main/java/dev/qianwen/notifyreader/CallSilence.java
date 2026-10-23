package dev.qianwen.notifyreader;

/** Call interruption policy, independent of Android and notification content. */
public final class CallSilence {
    public static final long SETTLE_MS = 1500;
    private boolean blocked;
    private long resumeAt = Long.MIN_VALUE;
    private long ignoreMessagesThrough = Long.MIN_VALUE;

    public boolean update(boolean communicationActive, long wallTime, long elapsedTime) {
        if (blocked == communicationActive) return false;
        blocked = communicationActive;
        if (!blocked) {
            // Wait for Bluetooth to leave its call route. Never replay bundled call-time messages.
            resumeAt = elapsedTime + SETTLE_MS;
            ignoreMessagesThrough = Math.max(ignoreMessagesThrough, wallTime);
        }
        return true;
    }

    public boolean canSpeak(long elapsedTime) { return !blocked && elapsedTime >= resumeAt; }
    public boolean isCallActive() { return blocked; }
    public boolean isOldMessage(long messageDate) {
        return messageDate > 0 && messageDate <= ignoreMessagesThrough;
    }
}
