package com.curtismj.logoplus;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import com.curtismj.logoplus.fsm.BaseLogoMachine;
import com.curtismj.logoplus.persist.AppNotification;
import com.curtismj.logoplus.persist.LogoDao;
import com.curtismj.logoplus.persist.LogoDatabase;
import com.curtismj.logoplus.persist.RingColor;
import com.curtismj.logoplus.persist.UIState;
import com.google.android.material.navigation.NavigationView;

import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.ViewFlipper;

import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;
import com.rarepebble.colorpicker.ColorObserver;
import com.rarepebble.colorpicker.ObservableColor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final int RESULT_PICK_CONTACT = 1;
    private static final int RESULT_LOAD_CUSTOM = 2;
    Switch serviceStatusSwitch;
    Switch automationSwitch;
    Switch ringEffectSwtich;
    Switch visSwtich;

    ListView appList, numberList;
    CheckBox showSystem;
    ProgressBar listSpinner;
    ProgressBar ringListSpinner;
    private PackageManager packageManager = null;
    private ApplicationAdapter listAdapter = null;
    private RingColorAdapter ringColorAdapter = null;
    private SeekBar brightness;
    int iconWidth;
    private BroadcastReceiver statusReceiver;
    ViewFlipper mainSwitcher;
    MenuItem notifItem, effectsItem, ringItem;
    RadioGroup passiveGrp;
    View effectColor;
    SeekBar effecLengthBar;
    EditText effectLengthIndicator;
    LogoDatabase db;
    LogoDao dao;
    UIState state;
    Intent serviceStartIntent;
    Button addNumButton;

    private  static final int UPDATE_UI_STATE = 0;
    private  static final int ADD_NOTIF = 1;
    private  static final int DELETE_NOTIF = 2;
    private  static final int START_SERVICE = 3;
    private  static final int ADD_RING_COLOR = 4;
    private  static final int ADD_RING_COLOR_ADD = 5;
    private  static final int DELETE_RING_COLOR = 6;
    private  static final int PREVIEW_NOTIF = 7;

    private  final class DbHandler extends Handler {

        DbHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Intent broadCastIntent;
            switch (msg.what)
            {
                case UPDATE_UI_STATE:
                    dao.saveUIState((UIState)msg.obj);
                    break;

                case ADD_NOTIF:
                    dao.addAppNotification((AppNotification)msg.obj);
                    broadCastIntent = new Intent();
                    broadCastIntent.setAction(LogoPlusService.PREVIEW_NOTIF);
                    broadCastIntent.putExtra("previewMode", false);
                    sendBroadcast(broadCastIntent);
                    break;

                case DELETE_NOTIF:
                    dao.deleteAppNotification((String)msg.obj);
                    broadCastIntent = new Intent();
                    broadCastIntent.setAction(LogoPlusService.PREVIEW_NOTIF);
                    broadCastIntent.putExtra("previewMode", false);
                    sendBroadcast(broadCastIntent);
                    break;

                case PREVIEW_NOTIF:
                    broadCastIntent = new Intent();
                    broadCastIntent.setAction(LogoPlusService.PREVIEW_NOTIF);
                    if (msg.obj != null) {
                        broadCastIntent.putExtra("previewMode", true);
                        broadCastIntent.putExtra("preview", (Integer) msg.obj);
                    }
                    else
                        broadCastIntent.putExtra("previewMode", false);
                    sendBroadcast(broadCastIntent);
                    break;

                case ADD_RING_COLOR:
                case ADD_RING_COLOR_ADD:
                    dao.addRingColor((RingColor) msg.obj);
                    if (msg.what == ADD_RING_COLOR_ADD)
                    {
                        MainActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                new LoadRingColors().execute();
                            }
                        });
                    }
                    break;

                case DELETE_RING_COLOR:
                    dao.deleteRingColor((String) msg.obj);
                    break;

                case START_SERVICE:
                    state.serviceEnabled = true;
                    dao.saveUIState(state);
                    startService(serviceStartIntent);
                    break;
            }
        }
    }

    Handler dbHandler;

    private void syncUIState()
    {
        Message msg = dbHandler.obtainMessage(UPDATE_UI_STATE, state);
        dbHandler.sendMessage(msg);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        new LoadTask().execute();
    }

    private final class LoadTask extends  AsyncTask<Void, Void, Void>
    {
        @Override
        protected Void doInBackground(Void... voids) {
            db = LogoDatabase.getInstance(getApplicationContext());
            dao = db.logoDao();

            HandlerThread thread = new HandlerThread("",
                    Process.THREAD_PRIORITY_BACKGROUND);
            thread.start();
            dbHandler = new DbHandler(thread.getLooper());

            state = dao.getUIState();
            if (state == null) {
                state = new UIState();
                dao.saveUIState(state);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            init();
        }
    }

    protected void init() {

        setContentView(R.layout.activity_main);

        mainSwitcher = findViewById(R.id.mainSwitcher);
        serviceStartIntent = new Intent(this,LogoPlusService.class);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        Menu menu = navigationView.getMenu();
        notifItem = menu.findItem(R.id.notifItem);
        effectsItem = menu.findItem(R.id.effectsItem);
        ringItem = menu.findItem(R.id.ringItem);
        navigationView.setNavigationItemSelectedListener(this);

        IntentFilter intentFilter = new IntentFilter(LogoPlusService.START_BROADCAST);
        intentFilter.addAction(LogoPlusService.START_FAIL_BROADCAST);
        statusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(LogoPlusService.START_BROADCAST)) {
                    serviceStatusSwitch.setEnabled(true);
                    serviceStatusSwitch.setChecked(true);
                } else if (intent.getAction().equals(LogoPlusService.START_FAIL_BROADCAST)) {
                    state.serviceEnabled = false;
                    syncUIState();
                    CommonUtils.genericDialog(MainActivity.this, R.string.failed, R.string.failed_start);
                    serviceStatusSwitch.setEnabled(true);
                    serviceStatusSwitch.setChecked(false);
                }
            }
        };
        registerReceiver(statusReceiver, intentFilter);

        MenuItem serviceSwitchItem = menu.findItem(R.id.service_status_switch);
        serviceStatusSwitch = serviceSwitchItem.getActionView().findViewWithTag("innerSwitch");
        serviceStatusSwitch.setEnabled(true);
        serviceStatusSwitch.setChecked(state.serviceEnabled);
        serviceStatusSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setServiceStatus(isChecked);
            }
        });

        if (state.serviceEnabled) startService(serviceStartIntent);


        appList = findViewById(R.id.appList);
        appList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                final ApplicationAdapter.AppInfoWrap info = listAdapter.appsList.get(position);
                CommonUtils.colorPickDialog(MainActivity.this, info.color == null ? Color.GREEN : info.color, new CommonUtils.ColorPickCallback() {
                    @Override
                    public void run(int color) {
                        AppNotification notif = new AppNotification(info.info.packageName);
                        notif.color = color;
                        Message msg = dbHandler.obtainMessage(ADD_NOTIF, notif);
                        dbHandler.sendMessage(msg);
                        listAdapter.appsList.get(position).color = notif.color;
                        listAdapter.notifyDataSetChanged();
                    }
                }, new CommonUtils.ColorPickCallback() {
                    @Override
                    public void run(int color) {
                        Message msg = dbHandler.obtainMessage(DELETE_NOTIF, info.info.packageName);
                        dbHandler.sendMessage(msg);
                        listAdapter.appsList.get(position).color = null;
                        listAdapter.notifyDataSetChanged();
                    }
                }, new ColorObserver() {
                    @Override
                    public void updateColor(ObservableColor observableColor) {
                        dbHandler.removeMessages(PREVIEW_NOTIF);
                        Message msg = dbHandler.obtainMessage(PREVIEW_NOTIF, observableColor.getColor());
                        dbHandler.sendMessageDelayed(msg, 1000);
                    }
                }, new CommonUtils.BlankCallback() {
                    @Override
                    public void run() {
                        dbHandler.removeMessages(PREVIEW_NOTIF);
                        Message msg = dbHandler.obtainMessage(PREVIEW_NOTIF, null);
                        dbHandler.sendMessage(msg);
                    }
                });
            }
        });

        numberList = findViewById(R.id.numberList);
        numberList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final RingColor ringColor = ringColorAdapter.colList.get(position);
                CommonUtils.colorPickDialog(MainActivity.this, ringColor.color, new CommonUtils.ColorPickCallback() {
                    @Override
                    public void run(int color) {
                        ringColor.color = color;
                        ringColorAdapter.notifyDataSetChanged();
                        Message msg = dbHandler.obtainMessage(ADD_RING_COLOR, ringColor);
                        dbHandler.sendMessage(msg);
                    }
                }, new CommonUtils.ColorPickCallback() {
                    @Override
                    public void run(int color) {
                        if (ringColor.number.equals(""))
                        {
                            ringColor.color = Color.BLACK;
                            ringColorAdapter.notifyDataSetChanged();
                            Message msg = dbHandler.obtainMessage(ADD_RING_COLOR, ringColor);
                            dbHandler.sendMessage(msg);
                        }
                        else {
                            ringColorAdapter.remove(ringColor);
                            Message msg = dbHandler.obtainMessage(DELETE_RING_COLOR, ringColor.number);
                            dbHandler.sendMessage(msg);
                        }
                    }
                }, null, null);
            }
        });

        packageManager = getPackageManager();
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        listSpinner = findViewById(R.id.progressBar_cyclic);
        ringListSpinner = findViewById(R.id.ring_progressBar_cyclic);
        iconWidth =  (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30f, metrics);
        showSystem = findViewById(R.id.systemAppsChk);
        showSystem.setChecked(state.showSystemApps);
        showSystem.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                new LoadApplications().execute(isChecked);
                state.showSystemApps = isChecked;
                syncUIState();
            }
        });
        new LoadApplications().execute(showSystem.isChecked());
        new LoadRingColors().execute();

        brightness = findViewById(R.id.brightnessBar);
        brightness.setProgress(state.brightness);
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                state.brightness = seekBar.getProgress();
                syncUIState();
            }
        });

        viewSwitch(state.currentView);

        passiveGrp = findViewById(R.id.passiveGroup);
        passiveGrp.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                int pref = BaseLogoMachine.EFFECT_NONE;
                switch (checkedId) {
                    case R.id.noneRadio: pref = BaseLogoMachine.EFFECT_NONE; break;
                    case R.id.staticRadio: pref = BaseLogoMachine.EFFECT_STATIC; break;
                    case R.id.pulsingRadio: pref = BaseLogoMachine.EFFECT_PULSE; break;
                    case R.id.rainbowRadio: pref = BaseLogoMachine.EFFECT_RAINBOW; break;
                    case R.id.pinWheelRadio: pref = BaseLogoMachine.EFFECT_PINWHEEL; break;
                    case R.id.rollRadio: pref = BaseLogoMachine.EFFECT_ROLL; break;
                    case R.id.customRadio: pref = BaseLogoMachine.EFFECT_CUSTOM; break;
                }
                state.passiveEffect = pref;
                syncUIState();
            }
        });
        RadioButton selectedButton = passiveGrp.findViewById(R.id.noneRadio);
        switch (state.passiveEffect) {
            case BaseLogoMachine.EFFECT_STATIC: selectedButton = passiveGrp.findViewById(R.id.staticRadio); break;
            case BaseLogoMachine.EFFECT_PULSE: selectedButton = passiveGrp.findViewById(R.id.pulsingRadio); break;
            case BaseLogoMachine.EFFECT_RAINBOW: selectedButton = passiveGrp.findViewById(R.id.rainbowRadio); break;
            case BaseLogoMachine.EFFECT_PINWHEEL: selectedButton = passiveGrp.findViewById(R.id.pinWheelRadio); break;
            case BaseLogoMachine.EFFECT_ROLL: selectedButton = passiveGrp.findViewById(R.id.rollRadio); break;
            case BaseLogoMachine.EFFECT_CUSTOM: selectedButton = passiveGrp.findViewById(R.id.customRadio); break;
        }
        selectedButton.setChecked(true);
        effectColor = findViewById(R.id.effectColorPick);
        effectColor.setBackgroundColor(state.passiveColor);
        effectColor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CommonUtils.colorPickDialog(MainActivity.this, state.passiveColor, new CommonUtils.ColorPickCallback() {
                    @Override
                    public void run(int color) {
                        state.passiveColor = color;
                        syncUIState();
                        effectColor.setBackgroundColor(color);
                    }
                }, null, null, null);
            }
        });

        effecLengthBar = findViewById(R.id.effectLengthBar);
        effectLengthIndicator = findViewById(R.id.effectLengthIndicator);
        effecLengthBar.setProgress((int)state.effectLength);
        effectLengthIndicator.setText(Integer.toString(effecLengthBar.getProgress()));
        effecLengthBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
               if (fromUser) effectLengthIndicator.setText(Integer.toString(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                state.effectLength = (float)seekBar.getProgress();
                syncUIState();
            }
        });
        effectLengthIndicator.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    effecLengthBar.setProgress(Integer.parseInt(s.toString()));
                } catch (NumberFormatException e)  {
                    // well, that's awkward
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                state.effectLength = (float)effecLengthBar.getProgress();
                syncUIState();
            }
        });

        Switch pwrSaveSwitch = findViewById(R.id.powerSaveSwitch);
        pwrSaveSwitch.setChecked(state.powerSave);
        pwrSaveSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                state.powerSave = isChecked;
                syncUIState();
            }
        });

        Switch pocketSwitch = findViewById(R.id.pocketModeSwitch);
        pocketSwitch.setChecked(state.pocketMode);
        pocketSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                state.pocketMode = isChecked;
                syncUIState();
            }
        });

        Switch battSwitch = findViewById(R.id.batteryAnimationSwitch);
        battSwitch.setChecked(state.batteryAnimation);
        battSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                state.batteryAnimation = isChecked;
                syncUIState();
            }
        });

        ImageView info = findViewById(R.id.powerSaveInfo);
        info.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CommonUtils.genericDialog(MainActivity.this, R.string.powerSave, R.string.powerSaveDesc);
            }
        });

        info = findViewById(R.id.pocketModeInfo);
        info.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CommonUtils.genericDialog(MainActivity.this, R.string.pocketMode, R.string.pocketModeDesc);
            }
        });

        info = findViewById(R.id.batteryAnimationInfo);
        info.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CommonUtils.genericDialog(MainActivity.this, R.string.batteryAnimationInfo, R.string.batteryAnimationDesc);
            }
        });

        Button applyBtn = findViewById(R.id.applyBtn);
        applyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent broadCastIntent = new Intent();
                broadCastIntent.setAction(LogoPlusService.APPLY_EFFECT);
                sendBroadcast(broadCastIntent);
            }
        });

        ringEffectSwtich = findViewById(R.id.ringEffectSwitch);
        ringEffectSwtich.setChecked(state.ringAnimation);
        ringEffectSwtich.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                state.ringAnimation = isChecked;
                syncUIState();
                if (isChecked) askPermission();
            }
        });

        addNumButton = findViewById(R.id.addRingColorButton);
        addNumButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent contactPickerIntent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
                startActivityForResult(contactPickerIntent, RESULT_PICK_CONTACT);
            }
        });

        MenuItem autoSwitchItem = menu.findItem(R.id.automation_switch);
        automationSwitch = autoSwitchItem.getActionView().findViewWithTag("innerSwitch");
        automationSwitch.setEnabled(true);
        automationSwitch.setChecked(state.automationAllowed);
        automationSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                state.automationAllowed = isChecked;
                syncUIState();
            }
        });

        MenuItem visSwitchItem = menu.findItem(R.id.vis_switch);
        visSwtich = visSwitchItem.getActionView().findViewWithTag("innerSwitch");
        visSwtich.setEnabled(true);
        visSwtich.setChecked(state.visualizer);
        visSwtich.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                state.visualizer = isChecked;
                syncUIState();
            }
        });

        Button loadCustom = findViewById(R.id.loadBtn);
        loadCustom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("text/plain");
                startActivityForResult(intent,RESULT_LOAD_CUSTOM);
            }
        });

    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(statusReceiver);
        if (dbHandler != null) dbHandler.getLooper().quitSafely();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Intent broadCastIntent = new Intent();
        broadCastIntent.setAction(LogoPlusService.APPLY_EFFECT);
        sendBroadcast(broadCastIntent);
    }

    public void viewSwitch(int id)
    {
       state.currentView = id;
        syncUIState();
        mainSwitcher.setDisplayedChild(id);
        switch (id)
        {
            case 0:
                notifItem.setChecked(true);
                effectsItem.setChecked(false);
                ringItem.setChecked(false);
                break;

            case 1:
                effectsItem.setChecked(true);
                notifItem.setChecked(false);
                ringItem.setChecked(false);
                break;

            case 2:
                effectsItem.setChecked(false);
                notifItem.setChecked(false);
                ringItem.setChecked(true);
                break;
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();
        Intent broadCastIntent;
        if (id == R.id.aboutItem) {
            AlertDialog.Builder aboutBuilder = new AlertDialog.Builder(this);
            aboutBuilder.setTitle(R.string.about_title);
            aboutBuilder.setMessage(getResources().getString(R.string.about_text,  getResources().getString(R.string.app_name), BuildConfig.VERSION_NAME, getResources().getString(R.string.app_blurb) ,BuildConfig.APPLICATION_ID, BuildConfig.BUILD_TYPE));
            aboutBuilder.setNeutralButton(R.string.ok_text, new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            AlertDialog aboutDialog = aboutBuilder.create();
            aboutDialog.show();
        }
        else if (id == R.id.ossItem)
        {
            startActivity(new Intent(this, OssLicensesMenuActivity.class));
        }
        else if (id == R.id.notifItem)
        {
            viewSwitch(0);
        }
        else if (id == R.id.effectsItem)
        {
            viewSwitch(1);
        }
        else if (id == R.id.ringItem)
        {
            viewSwitch(2);
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private boolean isSystemPackage(ApplicationInfo pkgInfo) {
        return ((pkgInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
    }

    private void filterAppList(List<ApplicationInfo> list, List<ApplicationAdapter.AppInfoWrap> applist, boolean systemApps) {
        applist.clear();
        for (ApplicationInfo info : list) {
            try {
                if ((null != packageManager.getLaunchIntentForPackage(info.packageName)) && (systemApps || !isSystemPackage(info))) {
                    ApplicationAdapter.AppInfoWrap wrap = new ApplicationAdapter.AppInfoWrap();
                    wrap.info = info;
                    applist.add(wrap);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private final class LoadApplications extends AsyncTask<Boolean, Void, Void> {
        @Override
        protected Void doInBackground(Boolean... showSystem) {
            if (listAdapter == null) {
                List<ApplicationAdapter.AppInfoWrap> emptyList = new ArrayList<>();
                filterAppList(packageManager.getInstalledApplications(PackageManager.GET_META_DATA), emptyList, showSystem[0]);
                listAdapter = new ApplicationAdapter(MainActivity.this, R.layout.app_row, emptyList);
            }
            else {
                filterAppList(packageManager.getInstalledApplications(PackageManager.GET_META_DATA), listAdapter.appsList, showSystem[0]);
            }

            // Memory efficiency and smoothness for a fraction of a second impact on load time
            // Scale down all icons. This also means we dont have to worry much
            // about keeping many icons in memory. Not tooo much anyways...
            Bitmap bitmap = Bitmap.createBitmap( iconWidth, iconWidth, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            for (ApplicationAdapter.AppInfoWrap info : listAdapter.appsList) {
                info.color = dao.getAppNotification(info.info.packageName).onErrorReturnItem(new AppNotification()).blockingGet().color;
                Drawable icon = info.info.loadIcon(packageManager);
                icon.setBounds(0, 0, iconWidth, iconWidth);
                canvas.drawColor(Color.WHITE, PorterDuff.Mode.CLEAR);
                icon.draw(canvas);
                info.icon = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                info.label = info.info.loadLabel(packageManager).toString();
            }
            bitmap.recycle();
            listAdapter.appsList.sort(new Comparator<ApplicationAdapter.AppInfoWrap>() {
                @Override
                public int compare(ApplicationAdapter.AppInfoWrap o1, ApplicationAdapter.AppInfoWrap o2) {
                    if (o1.color != null && o2.color == null) return -1;
                    if (o2.color != null && o1.color == null) return 1;
                    return o1.label.compareTo(o2.label);
                }
            });

            return null;
        }

        @Override
        protected void onPostExecute(Void nothing) {
            appList.setAdapter(listAdapter);
            showSystem.setEnabled(true);
            listSpinner.setVisibility(View.GONE);
        }

        @Override
        protected void onPreExecute() {
            showSystem.setEnabled(false);
            appList.setAdapter(null);
            listSpinner.setVisibility(View.VISIBLE);
        }
    }

    private final class LoadRingColors extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... nothing) {
            if (ringColorAdapter == null) {
                List<RingColor> emptyList = new ArrayList<>();
                ringColorAdapter = new RingColorAdapter(MainActivity.this, R.layout.ring_num_row, emptyList);
            }
            else {
                ringColorAdapter.colList.clear();
            }

            ringColorAdapter.colList.addAll(Arrays.asList(dao.getRingColors()));

            boolean defaultAdded = false;
            for (RingColor ringColor : ringColorAdapter.colList)
            {
                if (ringColor.number.equals(""))
                {
                    defaultAdded = true;
                    break;
                }
            }
            if (!defaultAdded)
            {
                RingColor def = new RingColor("", Color.GREEN, "Default");
                ringColorAdapter.colList.add(def);
                Message msg = dbHandler.obtainMessage(ADD_RING_COLOR, def);
                dbHandler.sendMessage(msg);
            }

            ringColorAdapter.colList.sort(new Comparator<RingColor>() {
                @Override
                public int compare(RingColor o1, RingColor o2) {
                    if (o1.number.equals("")) return -1;
                    if (o2.number.equals("")) return 1;
                    return o1.friendlyName.compareTo(o2.friendlyName);
                }
            });

            return null;
        }

        @Override
        protected void onPostExecute(Void nothing) {
            numberList.setAdapter(ringColorAdapter);
            ringListSpinner.setVisibility(View.GONE);
        }

        @Override
        protected void onPreExecute() {
            numberList.setAdapter(null);
            ringListSpinner.setVisibility(View.VISIBLE);
        }
    }

    private  void setServiceStatus(boolean status)
    {
        if (status)
        {
            serviceStatusSwitch.setEnabled(false);
            Message msg = dbHandler.obtainMessage(START_SERVICE);
            dbHandler.sendMessage(msg);
        }
        else if (!status)
        {
            stopService(serviceStartIntent);
            state.serviceEnabled = false;
            syncUIState();
        }
    }

    private void askPermission() {

        //int RECORD_AUDIO = checkSelfPermission(Manifest.permission.RECORD_AUDIO);
        //
        //if (RECORD_AUDIO != PackageManager.PERMISSION_GRANTED) {
        //    permissions.add(Manifest.permission.RECORD_AUDIO);
        //}
        final List<String> permissions = new ArrayList<String>();
        int READ_PHONE_STATE = checkSelfPermission(Manifest.permission.READ_PHONE_STATE);

        if (READ_PHONE_STATE != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_PHONE_STATE);
        }

        int READ_CALL_LOG = checkSelfPermission(Manifest.permission.READ_CALL_LOG);

        if (READ_CALL_LOG != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_CALL_LOG);
        }

        if (!permissions.isEmpty()) {
            AlertDialog.Builder aboutBuilder = new AlertDialog.Builder(this);
            aboutBuilder.setTitle(R.string.phonePermTitle);
            aboutBuilder.setMessage(R.string.phonePermDesc);
            aboutBuilder.setNeutralButton(R.string.ok_text, new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    requestPermissions(permissions.toArray(new String[permissions.size()]), 1);
                }
            });
            aboutBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    ringEffectSwtich.setChecked(false);
                }
            });
            AlertDialog aboutDialog = aboutBuilder.create();
            aboutDialog.show();


        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 1) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED)
                {
                    ringEffectSwtich.setChecked(false);
                    return;
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case RESULT_PICK_CONTACT:
                    Cursor cursor;
                    try {
                        String phoneNo;
                        String name;

                        Uri uri = data.getData();
                        cursor = getContentResolver().query(uri, null, null, null, null);
                        cursor.moveToFirst();
                        int  phoneIndex =cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                        int  nameIndex =cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                        phoneNo = cursor.getString(phoneIndex);
                        name = cursor.getString(nameIndex);

                        RingColor ringColor = new RingColor(phoneNo, Color.GREEN, name);
                        Message msg = dbHandler.obtainMessage(ADD_RING_COLOR_ADD, ringColor);
                        dbHandler.sendMessage(msg);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case RESULT_LOAD_CUSTOM:


                    try {
                        InputStream inputStream = getContentResolver().openInputStream(data.getData());
                        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
                        String line;
                        int lineCount = 0;
                        String program = "";
                        String[] offsets = new String[3];
                        boolean finished = true;
                        while ((line = br.readLine()) != null) {
                            if (lineCount < 12)
                            {
                                line = line.replace(" ", "").trim();
                                if (line.length() != 32)
                                {
                                    CommonUtils.genericDialog(this, R.string.customErrTitle, R.string.customErrMsg);
                                    finished = false;
                                    break;
                                }
                                program += line;
                            }
                            else
                            {
                                String[] split = line.split(" ");
                                if (split.length < 3 || !split[0].equals("@")) {
                                    CommonUtils.genericDialog(this, R.string.customErrTitle, R.string.customErrMsg);
                                    finished = false;
                                    break;
                                }
                                else if (split[2].equals("program1")) offsets[0] = split[1];
                                else if (split[2].equals("program2")) offsets[1] = split[1];
                                else if (split[2].equals("program3")) offsets[2] = split[1];
                                else
                                {
                                    CommonUtils.genericDialog(this, R.string.customErrTitle, R.string.customErrMsg);
                                    finished = false;
                                    break;
                                }
                            }
                            lineCount++;
                            if (lineCount >= 15) break;
                        }
                        br.close();
                        if (finished) {
                            if (lineCount < 15 || !MicroCodeManager.validateProgram(new String[]{offsets[0], offsets[0], offsets[0], program})) {
                                CommonUtils.genericDialog(this, R.string.customErrTitle, R.string.customErrMsg);
                                break;
                            }

                            state.customProgram = offsets[0] + "," + offsets[1] + ","  + offsets[2] + "," + program;
                            syncUIState();
                            CommonUtils.genericDialog(this, R.string.customSuccTitle, R.string.customSuccMsg);
                        }

                    }
                    catch (IOException e) {
                        // dont load
                        CommonUtils.genericDialog(this, R.string.customErrTitle, R.string.customErrMsg);
                    }
                    break;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
