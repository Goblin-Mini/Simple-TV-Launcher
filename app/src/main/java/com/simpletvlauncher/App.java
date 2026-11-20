package com.simpletvlauncher;

import android.app.Application;

/** Определяет язык интерфейса до создания любого экрана. */
public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        L.init(this);
    }
}
