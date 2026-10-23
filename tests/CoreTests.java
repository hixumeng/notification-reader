import dev.qianwen.notifyreader.ReadingRules;
import dev.qianwen.notifyreader.WavPadding;
import dev.qianwen.notifyreader.CallSilence;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

public final class CoreTests {
    static int checks;
    static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        CallSilence calls = new CallSilence();
        check(calls.canSpeak(0), "normal startup permits speech");
        check(!calls.isOldMessage(100), "no historical cutoff before any call");
        check(calls.update(true, 10_000, 100), "incoming ring enters silence");
        check(!calls.canSpeak(100), "ringing blocks speech");
        check(!calls.update(true, 11_000, 1100), "ringing-to-connected remains blocked");
        check(!calls.canSpeak(100_000), "long calls never time out into speech");
        check(calls.update(false, 20_000, 10_100), "hangup starts route cooldown");
        check(!calls.canSpeak(11_599), "speech cannot resume before route cooldown ends");
        check(calls.canSpeak(11_600), "only fresh speech may resume after cooldown");
        check(calls.isOldMessage(15_000), "bundled call-time messages cannot replay after hangup");
        check(calls.isOldMessage(20_000), "hangup boundary is also suppressed");
        check(!calls.isOldMessage(20_001), "new messages after hangup are eligible");
        check(!calls.isOldMessage(0), "undated notifications rely on content deduplication");
        check(!calls.update(false, 80_000, 90_000), "idle checks do not move historical cutoff");
        check(!calls.isOldMessage(30_000), "idle messages stay eligible");
        check(calls.update(true, 90_000, 100_000), "second call enters silence again");
        check(!calls.canSpeak(100_000), "second call cannot inherit first call's permission");
        calls.update(false, 95_000, 105_000);
        check(calls.isOldMessage(91_000), "second call extends historical suppression");
        check(!calls.canSpeak(106_499) && calls.canSpeak(106_500), "second hangup gets a fresh cooldown");
        ReadingRules skipped = new ReadingRules();
        check(skipped.accept("chat", "notice", "call-time message", 0, 100), "record skipped generic notification");
        check(!skipped.accept("chat", "notice", "call-time message", 0, 2000), "generic duplicate cannot replay just after hangup");
        check(ReadingRules.clean(null).equals(""), "null text");
        check(ReadingRules.clean("  新\n消息\u202e  ").equals("新 消息"), "control and bidi cleanup");
        check(ReadingRules.spoken("Muse", "Muse", "任务已完成").equals("Muse。任务已完成"), "no repeated app label");
        check(ReadingRules.spoken("Muse", "提醒", "").isEmpty(), "ignore empty message body");
        check(ReadingRules.spoken("Muse", "项目", "项目：完成").equals("Muse。项目：完成"), "avoid repeated title");
        ReadingRules rules = new ReadingRules();
        check(rules.accept("muse", "n1", "完成", 0, 100), "first event");
        check(!rules.accept("muse", "n1", "完成", 0, 1000), "duplicate update");
        check(rules.accept("muse", "n1", "新的正文", 0, 2000), "changed notification body");
        check(rules.accept("muse", "n1", "完成", 0, 31_000), "new repeat outside short window");
        check(rules.accept("muse", "conversation", "同样的消息", 123, 32_000), "first messaging-style message");
        check(!rules.accept("muse", "conversation", "同样的消息", 123, 60_000), "historical message in updated bundle");
        check(rules.accept("muse", "conversation", "同样的消息", 124, 61_000), "genuine identical new message");
        check(rules.accept("other", "n1", "完成", 0, 62_000), "dedupe isolated by source");
        String value = "开始。" + new String(new char[449]).replace('\0', '字') + "😀结束。";
        List<String> chunks = ReadingRules.chunks(value, 450);
        check(String.join("", chunks).equals(value), "chunks preserve all text");
        for (String chunk : chunks) {
            check(chunk.length() <= 450, "bounded synthesis size");
            check(!Character.isHighSurrogate(chunk.charAt(chunk.length()-1)), "emoji surrogate not split");
        }
        check(ReadingRules.chunks("", 450).isEmpty(), "empty queue input");
        byte[] wav = wave(16);
        byte[] padded = WavPadding.prepend(wav, 350);
        ByteBuffer b = ByteBuffer.wrap(padded).order(ByteOrder.LITTLE_ENDIAN);
        check(padded.length == wav.length + 11200, "350ms PCM silence");
        check(b.getInt(4) == padded.length - 8, "RIFF length updated");
        check(b.getInt(40) == 11204, "audio data length updated");
        check(padded[padded.length-1] == 4, "original audio preserved");
        byte[] eight = WavPadding.prepend(wave(8), 100);
        check(eight[44] == (byte)128, "8-bit PCM silence offset");
        boolean rejected = false;
        try { WavPadding.prepend(new byte[44], 350); } catch (java.io.IOException expected) { rejected = true; }
        check(rejected, "reject non-WAV rather than guessing playback");
        byte[] corrupt = wave(16); ByteBuffer.wrap(corrupt).order(ByteOrder.LITTLE_ENDIAN).putInt(40, Integer.MAX_VALUE);
        rejected = false;
        try { WavPadding.prepend(corrupt, 350); } catch (java.io.IOException expected) { rejected = true; }
        check(rejected, "reject invalid data length");
        System.out.println("Core checks passed: " + checks);
    }
    static byte[] wave(int bits) {
        ByteBuffer b = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x46464952).putInt(40).putInt(0x45564157).putInt(0x20746d66).putInt(16);
        b.putShort((short)1).putShort((short)1).putInt(16000).putInt(16000 * bits / 8);
        b.putShort((short)(bits/8)).putShort((short)bits).putInt(0x61746164).putInt(4);
        b.put(new byte[]{1,2,3,4}); return b.array();
    }
}
