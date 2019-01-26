package com.curtismj.logoplus;

import java.util.List;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class ApplicationAdapter extends ArrayAdapter<ApplicationAdapter.AppInfoWrap> {
    public static class AppInfoWrap
    {
        public ApplicationInfo info;
        public String label;
        public Integer color;
        public Bitmap icon;
    }

    public List<AppInfoWrap> appsList;
    LayoutInflater layoutInflater;

    public ApplicationAdapter(Context context, int textViewResourceId,
                              List<AppInfoWrap> appsList) {
        super(context, textViewResourceId, appsList);
        this.appsList = appsList;
        layoutInflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return ((null != appsList) ? appsList.size() : 0);
    }

    @Override
    public AppInfoWrap getItem(int position) {
        return ((null != appsList) ? appsList.get(position) : null);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (null == view) {
            view = layoutInflater.inflate(R.layout.app_row, null);
        }

        AppInfoWrap applicationInfo = appsList.get(position);
        if (null != applicationInfo) {
            TextView appName = (TextView) view.findViewById(R.id.app_name);
            TextView packageName = (TextView) view.findViewById(R.id.app_paackage);
            ImageView iconview = (ImageView) view.findViewById(R.id.app_icon);
            View rect = view.findViewById(R.id.color_rect);
            Integer color = applicationInfo.color;
            if (color == null) rect.setVisibility(View.INVISIBLE);
            else {
                rect.setVisibility(View.VISIBLE);
                rect.setBackgroundColor(color);
            }

            appName.setText(applicationInfo.label);
            packageName.setText(applicationInfo.info.packageName);

            iconview.setImageBitmap(applicationInfo.icon);
        }

        return view;
    }

};