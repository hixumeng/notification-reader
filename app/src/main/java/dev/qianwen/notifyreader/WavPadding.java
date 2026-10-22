package dev.qianwen.notifyreader;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Arrays;

/** Adds a short silent lead-in while Android establishes the requested audio route. */
public final class WavPadding {
    public static void add(File file, int milliseconds) throws IOException {
        byte[] input = Files.readAllBytes(file.toPath());
        Files.write(file.toPath(), prepend(input, milliseconds));
    }
    public static byte[] prepend(byte[] input, int milliseconds) throws IOException {
        if (input.length < 44 || input.length > 30_000_000 || milliseconds < 0 || milliseconds > 1000)
            throw new IOException("Invalid audio size");
        ByteBuffer b = ByteBuffer.wrap(input).order(ByteOrder.LITTLE_ENDIAN);
        if (b.getInt(0) != 0x46464952 || b.getInt(8) != 0x45564157) throw new IOException("Not WAV");
        int bits = 0, byteRate = 0, alignment = 0, format = 0;
        for (int offset = 12; offset + 8 <= input.length; ) {
            int id = b.getInt(offset), length = b.getInt(offset + 4);
            if (length < 0 || (long) offset + 8 + length > input.length) throw new IOException("Invalid chunk");
            if (id == 0x20746d66 && length >= 16) {
                format = b.getShort(offset + 8) & 65535;
                byteRate = b.getInt(offset + 16);
                alignment = b.getShort(offset + 20) & 65535;
                bits = b.getShort(offset + 22) & 65535;
            }
            if (id == 0x61746164) {
                if ((format != 1 && format != 3) || alignment < 1 || byteRate < 1 || byteRate > 1_000_000)
                    throw new IOException("Unsupported WAV");
                int silence = (int) ((long) byteRate * milliseconds / 1000 / alignment * alignment);
                byte[] out = new byte[input.length + silence];
                System.arraycopy(input, 0, out, 0, offset + 8);
                if (bits == 8 && format == 1) Arrays.fill(out, offset + 8, offset + 8 + silence, (byte) 128);
                System.arraycopy(input, offset + 8, out, offset + 8 + silence, input.length - offset - 8);
                ByteBuffer result = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN);
                result.putInt(4, out.length - 8);
                result.putInt(offset + 4, length + silence);
                return out;
            }
            long next = (long) offset + 8 + length + (length & 1);
            if (next > input.length) throw new IOException("Invalid padding");
            offset = (int) next;
        }
        throw new IOException("Missing audio data");
    }
}
