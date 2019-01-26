package com.curtismj.logoplus;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

public class LogoPlusNotificationListener extends NotificationListenerService {
    private SharedPreferences settings;

    @Override
    public void onCreate() {
        super.onCreate();
        notifs = new HashMap<>();
        settings = getSharedPreferences(BuildConfig.APPLICATION_ID + ".prefs", Context.MODE_PRIVATE);
    }

    HashMap<String, Integer> notifs;

    public static int[] toPrimitive(Integer[] IntegerArray) {

        int[] result = new int[IntegerArray.length];
        for (int i = 0; i < IntegerArray.length; i++) {
            result[i] = IntegerArray[i];
        }
        return result;
    }

    private  void notifyServiceChange(Integer[] colors)
    {
        Log.d("debug", "update notfis requested");
        if (LogoPlusService.ServiceRunning) {
            Log.d("debug", "update notfis in main service");
            Intent sendColor = new Intent(this, LogoPlusService.class);
            sendColor.putExtra("notif", true);
            sendColor.putExtra("colors", toPrimitive(colors));
            startService(sendColor);
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn){
            String key = "COLOR:" + sbn.getPackageName();
            synchronized (notifs)
            {
                if (!settings.contains(key) || notifs.containsKey(key)) return;
                notifs.put(key, settings.getInt(key, Color.RED));
                Collection<Integer> vals = notifs.values();
                Integer[] colors = vals.toArray(new Integer[vals.size()]);
                notifyServiceChange(colors);
            }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn){
        String key = "COLOR:" + sbn.getPackageName();
        synchronized (notifs)
        {
            if (notifs.containsKey(key)) notifs.remove(key);
            Collection<Integer> vals = notifs.values();
            Integer[] colors = vals.toArray(new Integer[vals.size()]);
            notifyServiceChange(colors);
        }
    }
}
