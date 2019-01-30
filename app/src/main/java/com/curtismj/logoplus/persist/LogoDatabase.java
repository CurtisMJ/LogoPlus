package com.curtismj.logoplus.persist;

import android.content.Context;

import java.util.concurrent.Executors;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {AppNotification.class,UIState.class}, version = 1, exportSchema = false)
public abstract class LogoDatabase extends RoomDatabase {
    private  static LogoDatabase INSTANCE;

    public abstract  LogoDao logoDao();

    private static final Object sLock = new Object();

    public static LogoDatabase getInstance(final Context context) {
        synchronized (sLock) {
            if (INSTANCE == null) {
                INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                        LogoDatabase.class, "logo.db")
                        .allowMainThreadQueries()
                        .build();
            }
            return INSTANCE;
        }
    }
}
