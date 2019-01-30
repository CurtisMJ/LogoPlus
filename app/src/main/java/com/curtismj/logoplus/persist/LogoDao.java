package com.curtismj.logoplus.persist;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import io.reactivex.Single;

@Dao
public interface LogoDao {
    @Query("SELECT * FROM AppNotification WHERE packageName = :pkgName LIMIT 1")
    Single<AppNotification> getAppNotification(String pkgName);

    @Query("SELECT * FROM AppNotification")
    AppNotification[] getAppNotifications();

    @Insert (onConflict = OnConflictStrategy.REPLACE)
    void addAppNotification(AppNotification... notifs);

    @Delete
    void deleteAppNotification(AppNotification... notifs);

    @Query("SELECT * FROM UIState LIMIT 1")
    UIState getUIState();

    @Insert (onConflict = OnConflictStrategy.REPLACE)
    void saveUIState(UIState... state);
}
