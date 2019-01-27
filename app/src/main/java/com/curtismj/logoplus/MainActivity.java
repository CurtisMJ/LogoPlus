package com.curtismj.logoplus;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Message;
import android.support.v7.widget.DrawableUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;

import com.rarepebble.colorpicker.ColorPickerView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    Switch serviceStatusSwitch;
    Intent serviceStartIntent;
    SharedPreferences settings;
    ListView appList;
    CheckBox showSystem;
    ProgressBar listSpinner;
    private PackageManager packageManager = null;
    private ApplicationAdapter listAdapter = null;
    private SeekBar brightness;
    int iconWidth;
    private BroadcastReceiver statusReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        serviceStartIntent = new Intent(this,LogoPlusService.class);

        IntentFilter intentFilter = new IntentFilter(LogoPlusService.START_BROADCAST);
        intentFilter.addAction(LogoPlusService.START_FAIL_BROADCAST);
        statusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(LogoPlusService.START_BROADCAST)) {
                    serviceStatusSwitch.setEnabled(true);
                } else if (intent.getAction().equals(LogoPlusService.START_FAIL_BROADCAST)) {
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putBoolean("ServiceEnabled", false);
                    editor.apply();
                    AlertDialog.Builder errorBuilder = new AlertDialog.Builder(MainActivity.this);
                    errorBuilder.setTitle("Failed");
                    errorBuilder.setMessage("The service could not be started. Please ensure root access is available and that the request is accepted.");
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

        settings = getSharedPreferences(BuildConfig.APPLICATION_ID + ".prefs", Context.MODE_PRIVATE);
        serviceStatusSwitch = (Switch)findViewById(R.id.service_status_switch);
        serviceStatusSwitch.setEnabled(true);
        serviceStatusSwitch.setChecked(settings.getBoolean("ServiceEnabled", false));
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
                picker.setColor(settings.getInt("COLOR:" + info.info.packageName, Color.GREEN));
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
                                SharedPreferences.Editor  edit = settings.edit();
                                edit.putInt("COLOR:" + info.info.packageName, color);
                                edit.apply();
                                listAdapter.appsList.get(position).color =  color;
                                listAdapter.notifyDataSetChanged();
                            }
                        })
                        .setNeutralButton("Remove Effect", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                SharedPreferences.Editor  edit = settings.edit();
                                edit.remove("COLOR:" + info.info.packageName);
                                edit.apply();
                                listAdapter.appsList.get(position).color = null;
                                listAdapter.notifyDataSetChanged();
                            }
                        })
                        .setNegativeButton("Cancel", null);
                AlertDialog pickerDialog = pickerBuilder.create();
                pickerDialog.show();
            }
        });

        packageManager = getPackageManager();
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        listSpinner = findViewById(R.id.progressBar_cyclic);
        iconWidth =  (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30f, metrics);
        showSystem = findViewById(R.id.systemAppsChk);
        showSystem.setChecked(settings.getBoolean("ShowSysetmApps", false));
        showSystem.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                new LoadApplications().execute(isChecked);
                SharedPreferences.Editor  edit = settings.edit();
                edit.putBoolean("ShowSysetmApps", isChecked);
                edit.apply();
            }
        });
        new LoadApplications().execute(showSystem.isChecked());

        brightness = findViewById(R.id.brightnessBar);
        brightness.setProgress(settings.getInt("Brightness", 128));
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                SharedPreferences.Editor  edit = settings.edit();
                edit.putInt("Brightness", progress);
                edit.apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(statusReceiver);
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
        } else if (id == R.id.ossItem)
        {
            startActivity(new Intent(this, OssLicensesMenuActivity.class));
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

    private class LoadApplications extends AsyncTask<Boolean, Void, Void> {
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
                info.color = settings.contains("COLOR:" + info.info.packageName) ? settings.getInt("COLOR:" + info.info.packageName, 0) : null;
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
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean("ServiceEnabled", true);
            editor.commit();
            startService(serviceStartIntent);
        }
        else if (!status)
        {
            stopService(serviceStartIntent);
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean("ServiceEnabled", false);
            editor.apply();
        }
    }

}
