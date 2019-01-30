package com.curtismj.logoplus;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.ArrayMap;
import android.util.Log;

import com.curtismj.logoplus.persist.AppNotification;
import com.curtismj.logoplus.persist.LogoDao;
import com.curtismj.logoplus.persist.LogoDatabase;


public class LogoPlusNotificationListener extends NotificationListenerService implements  ServiceConnection {
    private BroadcastReceiver resyncReceiver;
    private Messenger mMessenger;
    private volatile boolean mBound = false;
    private volatile boolean everBound = false;
    public static  final  String START_BROADCAST = BuildConfig.APPLICATION_ID + ".ListenerServiceAlive";
    private LogoDatabase db;
    private LogoDao dao;

    public void onServiceConnected(ComponentName className, IBinder service) {
        Log.d("debug", "remote service connecting");
        mMessenger = new Messenger(service);
        mBound = true;
        notifyServiceChange();
    }

    public void onServiceDisconnected(ComponentName className) {
        Log.d("debug", "remote service disconnecting");
        mMessenger = null;
        mBound = false;
    }

    private static class ResyncReceiver extends BroadcastReceiver {
        LogoPlusNotificationListener callback;

        public ResyncReceiver(LogoPlusNotificationListener parent) {
            callback = parent;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(LogoPlusService.START_BROADCAST)) {
                Log.d("debug", "rebind");
                if (!callback.mBound) {
                    callback.everBound = true;
                    callback.bindService(new Intent(callback, LogoPlusService.class), callback, 0);
                }
            }
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d("debug", "notification listener is starting");
        notifs = new ArrayMap<>();
        db = LogoDatabase.getInstance(getApplicationContext());
        dao = db.logoDao();
        IntentFilter intentFilter = new IntentFilter(LogoPlusService.START_BROADCAST);
        resyncReceiver = new ResyncReceiver(this);
        registerReceiver(resyncReceiver, intentFilter);
        Intent broadCastIntent = new Intent();
        broadCastIntent.setAction(START_BROADCAST);
        sendBroadcast(broadCastIntent);
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.d("debug", "notification listener is stopping");
        unregisterReceiver(resyncReceiver);
        notifs.clear();
        notifyServiceChange();
        if (everBound) unbindService(this);
        db = null;
        dao = null;
    }

    ArrayMap<String, Integer> notifs;

    private  void notifyServiceChange() {
        Log.d("debug", "update notfis requested");
        if (mBound) {
            int[] colors = new int[notifs.size()];
            for (int i = 0; i < colors.length; i++)
            {
                colors[i] = notifs.valueAt(i);
            }
            Message msg =  Message.obtain(null, LogoPlusService.NOTIF_PUSH, 0, 0);
            Bundle bundle = new Bundle();
            bundle.putBoolean("notif", true);
            bundle.putIntArray("colors", colors);
            msg.setData(bundle);
            try {
                mMessenger.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn){
            synchronized (notifs)
            {
                AppNotification notif = dao.getAppNotification(sbn.getPackageName()).onErrorReturnItem(new AppNotification()).blockingGet();
                if (notif.color != null) {
                    notifs.put(notif.packageName, notif.color);
                    Log.d("debug", "notif" + notif.packageName);
                    notifyServiceChange();
                }
            }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn){

        synchronized (notifs)
        {
            String key = sbn.getPackageName();
            if (notifs.containsKey(key)) {
                notifs.remove(key);
                notifyServiceChange();
            }
        }
    }

}
