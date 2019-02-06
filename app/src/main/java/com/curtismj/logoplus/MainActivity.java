package com.curtismj.logoplus;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ThsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;

import com.curtismj.logoplus.persist.AppNotification;
import com.curtismj.logoplus.persist.LogoDao;
import com.curtismj.logoplus.persist.LogoDatabase;
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
import android.os.Process;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.ViewFlipper;

import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;
import com.rarepebble.colorpicker.ColorPickerView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    Switch serviceStatusSwitch;

    ListView appList;
    CheckBox showSystem;
    ProgressBar listSpinner;
    private PackageManager packageManager = null;
    private ApplicationAdapter listAdapter = null;
    private SeekBar brightness;
    int iconWidth;
    private BroadcastReceiver statusReceiver;
    ViewFlipper mainSwitcher;
    MenuItem notifItem, effectsItem;
    RadioGroup passiveGrp;
    View effectColor;
    SeekBar effecLengthBar;
    EditText effectLengthIndicator;
    LogoDatabase db;
    LogoDao dao;
    UIState state;
    Intent serviceStartIntent;

    private  static final int UPDATE_UI_STATE = 0;
    private  static final int ADD_NOTIF = 1;
    private  static final int DELETE_NOTIF = 2;
    private  static final int START_SERVICE = 3;

    private  final class DbHandler extends Handler {

        DbHandler(Looper looper, LogoDao myDao) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what)
            {
                case UPDATE_UI_STATE:
                    dao.saveUIState((UIState)msg.obj);
                    break;

                case ADD_NOTIF:
                    dao.addAppNotification((AppNotification)msg.obj);
                    break;

                case DELETE_NOTIF:
                    dao.deleteAppNotification((String)msg.obj);
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
            dbHandler = new DbHandler(thread.getLooper(), dao);

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
                    AlertDialog.Builder errorBuilder = new AlertDialog.Builder(MainActivity.this);
                    errorBuilder.setTitle(R.string.failed);
                    errorBuilder.setMessage(R.string.failed_start);
                    errorBuilder.setNeutralButton(R.string.ok_text, new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    AlertDialog errorDialog = errorBuilder.create();
                    errorDialog.show();
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

        appList = findViewById(R.id.appList);
        appList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                final ColorPickerView picker = new ColorPickerView(MainActivity.this);
                final ApplicationAdapter.AppInfoWrap info = listAdapter.appsList.get(position);
                picker.setColor(info.color == null ? Color.GREEN : info.color);
                picker.showAlpha(false);
                picker.showHex(true);
                picker.showPreview(true);
                AlertDialog.Builder pickerBuilder = new AlertDialog.Builder(MainActivity.this);
                pickerBuilder
                        .setTitle(null)
                        .setView(picker)
                        .setPositiveButton(R.string.ok_text, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                AppNotification notif = new AppNotification(info.info.packageName);
                                notif.color = picker.getColor();
                                Message msg = dbHandler.obtainMessage(ADD_NOTIF, notif);
                                dbHandler.sendMessage(msg);
                                listAdapter.appsList.get(position).color =  notif.color ;
                                listAdapter.notifyDataSetChanged();
                            }
                        })
                        .setNeutralButton(R.string.remove_effect, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Message msg = dbHandler.obtainMessage(DELETE_NOTIF, info.info.packageName);
                                dbHandler.sendMessage(msg);
                                listAdapter.appsList.get(position).color = null;
                                listAdapter.notifyDataSetChanged();
                            }
                        })
                        .setNegativeButton(R.string.cancel, null);
                AlertDialog pickerDialog = pickerBuilder.create();
                pickerDialog.show();
            }
        });

        packageManager = getPackageManager();
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        listSpinner = findViewById(R.id.progressBar_cyclic);
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
                int pref = LogoPlusService.EFFECT_NONE;
                switch (checkedId) {
                    case R.id.noneRadio: pref = LogoPlusService.EFFECT_NONE; break;
                    case R.id.staticRadio: pref = LogoPlusService.EFFECT_STATIC; break;
                    case R.id.pulsingRadio: pref = LogoPlusService.EFFECT_PULSE; break;
                    case R.id.rainbowRadio: pref = LogoPlusService.EFFECT_RAINBOW; break;
                    case R.id.pinWheelRadio: pref = LogoPlusService.EFFECT_PINWHEEL; break;
                }
                state.passiveEffect = pref;
                syncUIState();
            }
        });
        RadioButton selectedButton = passiveGrp.findViewById(R.id.noneRadio);
        switch (state.passiveEffect) {
            case LogoPlusService.EFFECT_STATIC: selectedButton = passiveGrp.findViewById(R.id.staticRadio); break;
            case LogoPlusService.EFFECT_PULSE: selectedButton = passiveGrp.findViewById(R.id.pulsingRadio); break;
            case LogoPlusService.EFFECT_RAINBOW: selectedButton = passiveGrp.findViewById(R.id.rainbowRadio); break;
            case LogoPlusService.EFFECT_PINWHEEL: selectedButton = passiveGrp.findViewById(R.id.pinWheelRadio); break;
        }
        selectedButton.setChecked(true);
        effectColor = findViewById(R.id.effectColorPick);
        effectColor.setBackgroundColor(state.passiveColor);
        effectColor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final ColorPickerView picker = new ColorPickerView(MainActivity.this);
                picker.setColor(state.passiveColor);
                picker.showAlpha(false);
                picker.showHex(true);
                picker.showPreview(true);
                AlertDialog.Builder pickerBuilder = new AlertDialog.Builder(MainActivity.this);
                pickerBuilder
                        .setTitle(null)
                        .setView(picker)
                        .setPositiveButton(R.string.ok_text, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                final int color = picker.getColor();
                                state.passiveColor = color;
                                syncUIState();
                                effectColor.setBackgroundColor(color);
                            }
                        })
                        .setNegativeButton(R.string.cancel, null);
                AlertDialog pickerDialog = pickerBuilder.create();
                pickerDialog.show();
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

        Button applyBtn = findViewById(R.id.applyBtn);
        applyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent broadCastIntent = new Intent();
                broadCastIntent.setAction(LogoPlusService.APPLY_EFFECT);
                sendBroadcast(broadCastIntent);
            }
        });

        askPermission();
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
                break;

            case 1:
                effectsItem.setChecked(true);
                notifItem.setChecked(false);
                break;
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

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

    List<String> permissions = new ArrayList<String>();

    private boolean askPermission() {


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            int RECORD_AUDIO = checkSelfPermission(Manifest.permission.RECORD_AUDIO );

            if (RECORD_AUDIO != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.RECORD_AUDIO);
            }


            if (!permissions.isEmpty()) {
                requestPermissions(permissions.toArray(new String[permissions.size()]), 1);
            } else
                return false;
        } else
            return false;
        return true;

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 1) {

            boolean result = true;
            for (int i = 0; i < permissions.length; i++) {
                result = result && grantResults[i] == PackageManager.PERMISSION_GRANTED;
            }
            if (!result) {

                // askPermission();
            } else {
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
