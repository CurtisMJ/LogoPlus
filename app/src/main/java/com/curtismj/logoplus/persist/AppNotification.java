package com.curtismj.logoplus.persist;

import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class AppNotification {
    @PrimaryKey
    @NonNull
    public String packageName;

    public Integer color;

    public  AppNotification()
    {
        color = null;
    }

    public  AppNotification(String pkgName)
    {
        packageName = pkgName;
        color = Color.GREEN;
    }
}
