package com.curtismj.logoplus.persist;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {AppNotification.class,UIState.class, RingColor.class}, version = 4, exportSchema = false)
public abstract class LogoDatabase extends RoomDatabase {
    private  static LogoDatabase INSTANCE;

    public abstract  LogoDao logoDao();

    private static final Object sLock = new Object();

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE `RingColor` (`color` INTEGER NOT NULL, `number` TEXT NOT NULL, `friendlyName` TEXT, PRIMARY KEY(`number`))");
            database.execSQL("ALTER TABLE UIState  ADD COLUMN ringAnimation INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE UIState  ADD COLUMN pocketMode INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE UIState  ADD COLUMN automationAllowed INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static LogoDatabase getInstance(final Context context) {
        synchronized (sLock) {
            if (INSTANCE == null) {
                INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                        LogoDatabase.class, "logo.db")
                        .addMigrations(MIGRATION_1_2)
                        .addMigrations(MIGRATION_2_3)
                        .addMigrations(MIGRATION_3_4)
                        .build();
            }
            return INSTANCE;
        }
    }
}
