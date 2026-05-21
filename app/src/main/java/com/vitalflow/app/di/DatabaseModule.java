package com.vitalflow.app.di;

import android.content.Context;

import androidx.room.Room;

import com.vitalflow.app.data.AppDatabase;
import com.vitalflow.app.data.Migrations;
import com.vitalflow.app.data.dao.AppDao;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public class DatabaseModule {

    @Provides
    @Singleton
    public AppDatabase provideDatabase(@ApplicationContext Context context) {
        return Room.databaseBuilder(
                        context.getApplicationContext(),
                        AppDatabase.class,
                        "vitalflow_db"
                )
                .addMigrations(
                        Migrations.MIGRATION_8_9,
                        Migrations.MIGRATION_9_10
                )
                .fallbackToDestructiveMigration(false) // Crash > silent data wipe
                .build();
    }

    @Provides
    public AppDao provideAppDao(AppDatabase database) {
        return database.appDao();
    }
}