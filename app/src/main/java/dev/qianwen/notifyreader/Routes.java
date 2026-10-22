package dev.qianwen.notifyreader;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import java.util.ArrayList;
import java.util.List;

final class Routes {
    static List<AudioDeviceInfo> outputs(Context context) {
        List<AudioDeviceInfo> result = new ArrayList<>();
        AudioManager manager = context.getSystemService(AudioManager.class);
        for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int type = device.getType();
            if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_HEARING_AID
                    || (Build.VERSION.SDK_INT >= 31 && (type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    || type == AudioDeviceInfo.TYPE_BLE_SPEAKER))) result.add(device);
        }
        return result;
    }
    static String key(AudioDeviceInfo device) {
        String address = device.getAddress();
        return device.getType() + "|" + (address.isEmpty() ? device.getProductName().toString() : address);
    }
    static AudioDeviceInfo selected(Context context) {
        String wanted = new Prefs(context).route();
        for (AudioDeviceInfo device : outputs(context)) if (key(device).equals(wanted)) return device;
        return null;
    }
    static boolean matches(Context context, AudioDeviceInfo device) {
        return device != null && key(device).equals(new Prefs(context).route());
    }
}
