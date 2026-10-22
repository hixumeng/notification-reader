package dev.qianwen.notifyreader;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Android-independent text and duplicate handling. Never stores notification text. */
public final class ReadingRules {
    private final LinkedHashMap<String, Long> seen = new LinkedHashMap<>();

    public static String clean(CharSequence value) {
        if (value == null) return "";
        return value.toString().replaceAll("[\\p{Cc}\\p{Cf}]+", " ")
                .replaceAll("\\s+", " ").trim();
    }

    public static String spoken(String app, String title, String body) {
        String a = clean(app), t = clean(title), b = clean(body);
        if (b.isEmpty()) return "";
        if (t.equalsIgnoreCase(a) || b.equals(t) || b.startsWith(t + "：")) t = "";
        return a + "。" + (t.isEmpty() ? "" : t + "。") + b;
    }

    /** Call only after source/output eligibility checks. Message dates distinguish real repeats. */
    public boolean accept(String source, String key, String body, long messageDate, long now) {
        Iterator<Map.Entry<String, Long>> it = seen.entrySet().iterator();
        while (it.hasNext()) if (now - it.next().getValue() > 600_000L) it.remove();
        String digest = digest(source + "\n" + key + "\n" + messageDate + "\n" + clean(body));
        Long previous = seen.get(digest);
        long window = messageDate > 0 ? 600_000L : 30_000L;
        if (previous != null && now - previous < window) return false;
        seen.put(digest, now);
        while (seen.size() > 512) seen.remove(seen.keySet().iterator().next());
        return true;
    }

    public static List<String> chunks(String input, int size) {
        if (size < 2) throw new IllegalArgumentException("size");
        List<String> result = new ArrayList<>();
        String text = clean(input);
        while (!text.isEmpty()) {
            int end = Math.min(size, text.length());
            if (end < text.length()) {
                int sentence = -1;
                for (int i = end - 1; i >= end / 2; i--) {
                    if ("。！？；.!?;".indexOf(text.charAt(i)) >= 0) { sentence = i + 1; break; }
                }
                if (sentence > 0) end = sentence;
                if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
            }
            result.add(text.substring(0, end));
            text = text.substring(end);
        }
        return result;
    }

    private static String digest(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : hash) out.append(String.format("%02x", b & 255));
            return out.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
