package com.curtismj.logoplus;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.ArrayMap;
import android.util.Log;

import com.curtismj.logoplus.fsm.BaseLogoMachine;
import com.curtismj.logoplus.fsm.RootLogoMachine;
import com.curtismj.logoplus.fsm.StateMachine;
import com.curtismj.logoplus.persist.LogoDao;
import com.curtismj.logoplus.persist.LogoDatabase;
import com.curtismj.logoplus.persist.RingColor;
import com.curtismj.logoplus.persist.UIState;

import java.io.IOException;
import java.util.Locale;

public class LogoPlusService extends Service {
    public static final int SERVICE_START = 0;
    public static final int NOTIF_PUSH = 1;
    public static final int SCREENON = 2;
    public static final int SCREENOFF = 3;
    public static final int APPLY_EFFECT_MSG = 4;
    public static final int START_BOUNCE = 5;
    public static final int VIS_START = 6;
    public static final int VIS_STOP = 7;
    public static final int PHONE_STATE = 8;

    public static  final  String START_BROADCAST = BuildConfig.APPLICATION_ID + ".ServiceAlive";
    public static  final  String START_FAIL_BROADCAST = BuildConfig.APPLICATION_ID + ".ServiceFailedStart";
    public static  final  String APPLY_EFFECT = BuildConfig.APPLICATION_ID + ".ApplyEffect";

    public static final int EFFECT_NONE = 0;
    public static final int EFFECT_STATIC= 1;
    public static final int EFFECT_PULSE = 2;
    public static final int EFFECT_RAINBOW = 3;
    public static final int EFFECT_PINWHEEL = 4;

    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;
    private Messenger mMessenger;
    private  BroadcastReceiver offReceiver;
    private LogoDatabase db;
    private LogoDao dao;
    private UIState state;
    private StateMachine fsm;
    private  boolean phoneStateListening = false;
    private ArrayMap<String, Integer> ringAnimCache;

    private  void buildFSM() {
        //fsm = new ThsLogoMachine(state, this);
        try {
            fsm = new RootLogoMachine(state, this);

        } catch (IOException e) {
            e.printStackTrace();
            fsm = null;
            failOut();
        } catch (RootDeniedException e) {
            e.printStackTrace();
            fsm = null;
            failOut();
        }
    }

    private void failOut()
    {
        try
        {
            Log.d("debug", "failed start service");
            Intent broadCastIntent = new Intent();
            broadCastIntent.setAction(START_FAIL_BROADCAST);
            sendBroadcast(broadCastIntent);
            stopSelf();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    private void notifyStarted()
    {
        Intent broadCastIntent = new Intent();
        broadCastIntent.setAction(START_BROADCAST);
        sendBroadcast(broadCastIntent);
    }

    private void buildReceiver()
    {
        if (offReceiver != null) {
            unregisterReceiver(offReceiver);
            offReceiver = null;
            Log.d("debug", "Rebuild receiver");
        }
        Log.d("debug", "Build receiver");
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(Intent.ACTION_USER_PRESENT);
        intentFilter.addAction(APPLY_EFFECT);
        intentFilter.addAction(LogoPlusNotificationListener.START_BROADCAST);
        if (state.ringAnimation) {
            Log.d("debug", "Subscribing to phone state");
            intentFilter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        }
        offReceiver = new LogoBroadcastReceiver(mServiceHandler);
        registerReceiver(offReceiver, intentFilter);
        phoneStateListening = state.ringAnimation;
    }

    private void cacheRebuild()
    {
        if (state.ringAnimation)
        {
            ringAnimCache = new ArrayMap<>();
            RingColor[] ringColors = dao.getRingColors();
            for (RingColor ringColor : ringColors){
                if (ringColor.number.equals(""))
                {
                    ringAnimCache.put("", ringColor.color);
                }
                else {
                    ringAnimCache.put(PhoneNumberUtils.formatNumber(ringColor.number, Locale.getDefault().getCountry()), ringColor.color);
                }
            }
        }
        else
            ringAnimCache = null;
    }

    private void init() {
        Log.d(BuildConfig.APPLICATION_ID, "Service Starting");
        db = LogoDatabase.getInstance(getApplicationContext());
        dao = db.logoDao();
        state = dao.getUIState();
        if (state == null || !state.serviceEnabled) {
            Log.d(BuildConfig.APPLICATION_ID, "Service not enabled. Goodbye");
            stopSelf();
            return;
        }

        buildFSM();

        if (fsm == null) return;

        cacheRebuild();

        buildReceiver();

        notifyStarted();

    }

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (fsm == null && msg.what != SERVICE_START) return;
            switch (msg.what) {
                case SERVICE_START:
                    init();
                    break;

                case NOTIF_PUSH:
                    fsm.Event(BaseLogoMachine.EVENT_NOTIF_UPDATE, msg.getData().getIntArray("colors"));
                    break;

                case SCREENON:
                    fsm.Event(BaseLogoMachine.EVENT_SCREENON);
                    break;

                case SCREENOFF:
                    fsm.Event(BaseLogoMachine.EVENT_SCREENOFF);
                    break;

                case APPLY_EFFECT_MSG:
                    state = dao.getUIState();

                    cacheRebuild();

                    if (state.ringAnimation != phoneStateListening) {
                        buildReceiver();
                    }

                    fsm.Event(BaseLogoMachine.EVENT_STATE_UPDATE, state);
                    break;

                case VIS_START:
                    Log.d("debug", "vis test");
                    fsm.Event(BaseLogoMachine.EVENT_ENTER_VISUALIZER);
                    break;

                case VIS_STOP:
                    Log.d("debug", "vis stop");
                    fsm.Event(BaseLogoMachine.EVENT_EXIT_VISUALIZER);
                    break;

                case START_BOUNCE:
                    notifyStarted();
                    break;

                case PHONE_STATE:
                    // always good to double check...
                    if (state.ringAnimation) {
                        Bundle data = msg.getData();
                        if (!data.containsKey(TelephonyManager.EXTRA_INCOMING_NUMBER)) break;

                        String state = data.getString(TelephonyManager.EXTRA_STATE);
                        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                            String number = PhoneNumberUtils.formatNumber(data.getString(TelephonyManager.EXTRA_INCOMING_NUMBER), Locale.getDefault().getCountry());
                            if (ringAnimCache.containsKey(number))
                                fsm.Event(BaseLogoMachine.EVENT_RING, ringAnimCache.get(number));
                            else if (ringAnimCache.containsKey(""))
                                fsm.Event(BaseLogoMachine.EVENT_RING, ringAnimCache.get(""));
                        } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state) || TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
                            fsm.Event(BaseLogoMachine.EVENT_STOP_RING);
                        }
                    }
                    break;
            }
        }
    }

    private static class LogoBroadcastReceiver extends   BroadcastReceiver {
        private  Handler serviceHandler;

        public LogoBroadcastReceiver(Handler handler)
        {
            serviceHandler = handler;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Message msg;
            switch (intent.getAction()) {
                case Intent.ACTION_SCREEN_OFF:
                    Log.d("debug", "screen off, request service resume");
                     msg = serviceHandler.obtainMessage(SCREENOFF);
                    serviceHandler.sendMessage(msg);
                    break;
                case Intent.ACTION_USER_PRESENT:
                    Log.d("debug", "user present, request service to idle");
                    msg = serviceHandler.obtainMessage(SCREENON);
                    serviceHandler.sendMessage(msg);
                    break;
                case APPLY_EFFECT:
                    Log.d("debug", "effect update requested");
                    msg = serviceHandler.obtainMessage(APPLY_EFFECT_MSG);
                    serviceHandler.sendMessage(msg);
                    break;
                case LogoPlusNotificationListener.START_BROADCAST:
                    Log.d("debug", "listener alive, echo");
                    msg = serviceHandler.obtainMessage(START_BOUNCE);
                    serviceHandler.sendMessage(msg);
                    break;
                case TelephonyManager.ACTION_PHONE_STATE_CHANGED:
                    Log.d("debug", "phone state changed");
                    msg = serviceHandler.obtainMessage(PHONE_STATE);
                    msg.setData(intent.getExtras());
                    serviceHandler.sendMessage(msg);
                    break;
            }
        }
    }

    @Override
    public void onCreate() {
        HandlerThread thread = new HandlerThread("ServiceStartArguments",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
        mMessenger = new Messenger(mServiceHandler);

        Message msg = mServiceHandler.obtainMessage(SERVICE_START);
        mServiceHandler.sendMessage(msg);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger != null ? mMessenger.getBinder() : null;
    }

    @Override
    public void onDestroy() {
        if (mServiceHandler != null) mServiceLooper.quitSafely();
        if (fsm != null) fsm.cleanup();
        if (offReceiver != null) unregisterReceiver(offReceiver);
        dao = null;
        db = null;
        fsm = null;
    }
}
