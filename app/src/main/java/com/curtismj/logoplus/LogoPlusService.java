package com.curtismj.logoplus;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.Process;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.ArrayMap;
import android.util.Log;

import com.curtismj.logoplus.automation.ActionFireReceiver;
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
    public static final int PHONE_STATE = 8;
    public static final int FINAL_POCKET_CHECK = 9;
    public static final int SCREENON2 = 10;
    public static final int REGISTER_LIGHT_SENSOR = 11;
    public static final int AUTOMATION = 12;

    public static  final  String START_BROADCAST = BuildConfig.APPLICATION_ID + ".ServiceAlive";
    public static  final  String START_FAIL_BROADCAST = BuildConfig.APPLICATION_ID + ".ServiceFailedStart";
    public static  final  String APPLY_EFFECT = BuildConfig.APPLICATION_ID + ".ApplyEffect";
    public static  final  String FIRE_AUTOMATION = BuildConfig.APPLICATION_ID + ".FireAutomation";

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
    private  SensorManager mSensorManager;
    private Sensor accelSensor;
    private Sensor lightSensor;
    private Sensor proximitySensor;
    private  AccelListener accelListener;
    private  LightListener lightListener;
    private  ProximityListener proximityListener;
    private float[] accel = new float[3];
    private  float lux;
    private  float proximity;
    private  boolean sensorSatisfactory = false;
    private boolean lightDataReceived = false;
    private boolean proximityDataReceived = false;
    private boolean pocketModeWakelockHeld = false;
    private boolean pocketModeEnabled = false;
    private  int sensorBounces = 0;

    private PowerManager pm;
    private PowerManager.WakeLock pocketModeWakelock;

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
        if (state.pocketMode) {
            Log.d("debug", "Subscribing to screen on");
            intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        }
        intentFilter.addAction(APPLY_EFFECT);
        intentFilter.addAction(LogoPlusNotificationListener.START_BROADCAST);
        intentFilter.addAction(FIRE_AUTOMATION);
        if (state.ringAnimation) {
            Log.d("debug", "Subscribing to phone state");
            intentFilter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        }
        offReceiver = new LogoBroadcastReceiver();
        registerReceiver(offReceiver, intentFilter);
        phoneStateListening = state.ringAnimation;
        pocketModeEnabled = state.pocketMode;
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

    private  final class AccelListener implements SensorEventListener {
        @Override
        public void onSensorChanged(SensorEvent event) {
            accel[0] = event.values[0];
            accel[1] = event.values[1];
            accel[2] = event.values[2];
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            if (accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM)
            {
                sensorSatisfactory = true;
                Log.d("debug", "sensor reading is satisfactory");
            }
        }
    }

    private  final class LightListener implements SensorEventListener {
        @Override
        public void onSensorChanged(SensorEvent event) {
            lux = event.values[0];
            lightDataReceived = true;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    }

    private  final class ProximityListener implements SensorEventListener {
        @Override
        public void onSensorChanged(SensorEvent event) {
            proximity = event.values[0];
            proximityDataReceived = true;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
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

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        lightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        proximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        accelListener = new AccelListener();
        lightListener = new LightListener();
        proximityListener = new ProximityListener();

        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        pocketModeWakelock  = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, BuildConfig.APPLICATION_ID + ":PocketModeWorker");

        notifyStarted();
    }

    private void finishPocketMode() {
        mSensorManager.unregisterListener(accelListener);
        mSensorManager.unregisterListener(lightListener);
        mSensorManager.unregisterListener(proximityListener);
        if (pocketModeWakelockHeld) {
            pocketModeWakelock.release();
            pocketModeWakelockHeld = false;
        }
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

                case SCREENON2:
                    mServiceHandler.removeMessages(FINAL_POCKET_CHECK);
                    mServiceHandler.removeMessages(REGISTER_LIGHT_SENSOR);
                    finishPocketMode();
                    break;

                case SCREENOFF:
                    fsm.Event(BaseLogoMachine.EVENT_SCREENOFF);

                    if (state.pocketMode) {
                    /*
                        We need to give the accelerometer time to calibrate. Also we cant really check immediately after the
                        screen goes off as the user is probably still about to put the device into their pocket
                     */
                        mSensorManager.registerListener(accelListener, accelSensor, SensorManager.SENSOR_DELAY_NORMAL);
                        if (!pocketModeWakelockHeld) {
                            pocketModeWakelock.acquire(20000);
                            pocketModeWakelockHeld = true;
                        }
                        sensorSatisfactory = false;
                        lightDataReceived = false;
                        proximityDataReceived = false;
                        sensorBounces = 0;
                        msg = mServiceHandler.obtainMessage(REGISTER_LIGHT_SENSOR);
                        mServiceHandler.sendMessageDelayed(msg, 3500);
                        msg = mServiceHandler.obtainMessage(FINAL_POCKET_CHECK);
                        mServiceHandler.sendMessageDelayed(msg, 5000);
                    }

                    break;

                case REGISTER_LIGHT_SENSOR:
                    mSensorManager.registerListener(lightListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
                    break;

                case FINAL_POCKET_CHECK:
                    if (!state.pocketMode) break;

                    if (sensorBounces < 3 && (!sensorSatisfactory || !lightDataReceived || !proximityDataReceived)) {
                        // keep going
                        Log.d("Debug", "pocket mode not enough data, waiting...");
                        msg = mServiceHandler.obtainMessage(FINAL_POCKET_CHECK);
                        mServiceHandler.sendMessageDelayed(msg, 5000);
                        sensorBounces++;
                    }
                    else
                    {
                        finishPocketMode();
                        /*
                        Angles:
                        0: Parallel to face of device (lying flat "twist") A = atan(y/x)
                        1: "Forward" tilt along centre line of device, perpendicular to face A = atan(y/z)
                        */
                        float[] magnitudes = new float[2];
                        magnitudes[0] = (float)Math.sqrt(Math.pow(accel[0], 2) + Math.pow(accel[1], 2));
                        magnitudes[1] = (float)Math.sqrt(Math.pow(accel[2], 2) + Math.pow(accel[1], 2));
                        Log.d("Debug", "final magnitudes " + magnitudes[0] + ","  + magnitudes[1]);
                        Log.d("Debug", "final lux " + lux);
                        Log.d("Debug", "final proximity " + proximity);
                        boolean passed;
                        //  Are we flat on a surface?
                        if  (magnitudes[0] > magnitudes[1])
                        {
                            passed = true;
                        }
                        else
                        {
                            float angle = Math.abs((float)Math.toDegrees(Math.atan2(accel[1], accel[2])));
                            Log.d("Debug", "final angle " + angle);
                            passed = (angle > 30f && angle < 150f);
                        }
                        // under 5 lux light?
                        passed = passed && lux < 5f;
                        // less than 1cm proximity (binary sensor but whatevs)
                        passed = passed && proximity < 1f;

                        if (passed)
                        {
                            Log.d("debug", "pocket mode checks passed!");
                            fsm.Event(BaseLogoMachine.EVENT_POCKET_MODE);
                        }
                    }
                    break;

                case APPLY_EFFECT_MSG:
                    updateGlobalState();
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

                case AUTOMATION:
                    if (state.automationAllowed) {
                        Bundle data = msg.getData();
                        boolean updateUIState = false;
                        if (data.containsKey(ActionFireReceiver.KEY_PASSIVE_EFFECT))
                        {
                            try {
                                int effect = Integer.parseInt(data.getString(ActionFireReceiver.KEY_PASSIVE_EFFECT));
                                if (BaseLogoMachine.ValidateEffectNo(effect)) {
                                    state.passiveEffect = effect;
                                    updateUIState = true;
                                }
                            }
                            catch (NumberFormatException ex) {}
                        }
                        if (data.containsKey(ActionFireReceiver.KEY_PASSIVE_COLOR))
                        {
                            try {
                                state.passiveColor = Integer.parseInt(data.getString(ActionFireReceiver.KEY_PASSIVE_COLOR));
                                updateUIState = true;
                            }
                            catch (NumberFormatException ex) {}
                        }
                        if (data.containsKey(ActionFireReceiver.KEY_PASSIVE_LEN))
                        {
                            try {
                                state.effectLength = Integer.parseInt(data.getString(ActionFireReceiver.KEY_PASSIVE_LEN));
                                updateUIState = true;
                            }
                            catch (NumberFormatException ex) {}
                        }
                        if (data.containsKey(ActionFireReceiver.KEY_PASSIVE_LOCK))
                        {
                            try {
                                state.powerSave = (Integer.parseInt(data.getString(ActionFireReceiver.KEY_PASSIVE_LOCK)) != 0);
                                updateUIState = true;
                            }
                            catch (NumberFormatException ex) {}
                        }
                        if (data.containsKey(ActionFireReceiver.KEY_BRIGHTNESS))
                        {
                            try {
                                int bright = Integer.parseInt(data.getString(ActionFireReceiver.KEY_BRIGHTNESS));
                                if (bright >= 0 && bright <= 255) {
                                    state.brightness = bright;
                                    updateUIState = true;
                                }
                            }
                            catch (NumberFormatException ex) {}
                        }

                        if (updateUIState)
                        {
                            dao.saveUIState(state);
                            fsm.Event(BaseLogoMachine.EVENT_STATE_UPDATE, state);
                        }

                    }

                    break;

            }
        }
    }

    private void updateGlobalState() {
        state = dao.getUIState();

        cacheRebuild();

        if (state.ringAnimation != phoneStateListening || state.pocketMode != pocketModeEnabled) {
            buildReceiver();
        }

        if (!state.pocketMode)
        {
            mServiceHandler.removeMessages(FINAL_POCKET_CHECK);
            mServiceHandler.removeMessages(REGISTER_LIGHT_SENSOR);
            finishPocketMode();
        }

        fsm.Event(BaseLogoMachine.EVENT_STATE_UPDATE, state);
    }


    private final class LogoBroadcastReceiver extends   BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Message msg;
            switch (intent.getAction()) {
                case Intent.ACTION_SCREEN_OFF:
                    Log.d("debug", "screen off, request service resume");
                    /* Proximity sensor needs to be registered earlier to keep the sensor awake for a moment */
                    if (state.pocketMode) mSensorManager.registerListener(proximityListener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
                     msg = mServiceHandler.obtainMessage(SCREENOFF);
                    mServiceHandler.sendMessage(msg);
                    break;
                case Intent.ACTION_USER_PRESENT:
                    Log.d("debug", "user present, request service to idle");
                    msg = mServiceHandler.obtainMessage(SCREENON);
                    mServiceHandler.sendMessage(msg);
                    break;
                case Intent.ACTION_SCREEN_ON:
                    Log.d("debug", "screen on");
                    msg = mServiceHandler.obtainMessage(SCREENON2);
                    mServiceHandler.sendMessage(msg);
                    break;
                case APPLY_EFFECT:
                    Log.d("debug", "effect update requested");
                    msg = mServiceHandler.obtainMessage(APPLY_EFFECT_MSG);
                    mServiceHandler.sendMessage(msg);
                    break;
                case LogoPlusNotificationListener.START_BROADCAST:
                    Log.d("debug", "listener alive, echo");
                    msg = mServiceHandler.obtainMessage(START_BOUNCE);
                    mServiceHandler.sendMessage(msg);
                    break;
                case TelephonyManager.ACTION_PHONE_STATE_CHANGED:
                    Log.d("debug", "phone state changed");
                    msg = mServiceHandler.obtainMessage(PHONE_STATE);
                    msg.setData(intent.getExtras());
                    mServiceHandler.sendMessage(msg);
                    break;
                case FIRE_AUTOMATION:
                    if (state.automationAllowed) {
                        Log.d("debug", "automation update");
                        msg = mServiceHandler.obtainMessage(AUTOMATION);
                        msg.setData(intent.getExtras());
                        mServiceHandler.sendMessage(msg);
                    }
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
