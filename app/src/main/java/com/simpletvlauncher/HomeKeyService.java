package com.simpletvlauncher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Необязательная служба для телевизоров, где системная кнопка «Домой» всегда ведёт в стандартный лаунчер (Xiaomi, Philips и др.),
 * даже если по умолчанию выбран другой. Включается вручную в «Специальных возможностях»:
 *  - перехватывает клавишу Home и открывает Simple TV Launcher;
 *  - после включения ТВ (система сама поднимает службу при загрузке) один раз открывает лаунчер, чтобы не оставаться на HDMI/ТВ-входе.
 */
public class HomeKeyService extends AccessibilityService {
    private static final long BOOT_WINDOW_MS = 4 * 60 * 1000L;      // «свежая» загрузка: служба поднялась не позже, чем через 4 минуты после старта системы
    private static final long POLL_MS = 500;            // как часто проверяем, что сверху лаунчер
    private static final long MAX_MS = 30000;           // предохранитель

    private final Handler h = new Handler(Looper.getMainLooper());
    private String fg = "";
    private boolean bootRun, polling;
    private long pollStart;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!polling) return;
            if (SystemClock.elapsedRealtime() - pollStart > MAX_MS) { polling = false; return; }
            if (getPackageName().equals(fg)) { polling = false; return; }      // лаунчер на экране — задача выполнена
            openHome();
            h.postDelayed(this, POLL_MS);
        }
    };

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && e.getPackageName() != null) {
            String p = e.getPackageName().toString();
            if (!p.startsWith("com.android.systemui")) fg = p;
        }
    }

    @Override public void onInterrupt() {}

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        try {
            Prefs prefs = new Prefs(this);
            if (!prefs.bootHome() || bootRun) return;
            if (SystemClock.elapsedRealtime() > BOOT_WINDOW_MS) return;
            int boot = -1;
            try { boot = Settings.Global.getInt(getContentResolver(), "boot_count", -1); } catch (Throwable ignored) {}
            if (boot != -1 && prefs.raw().getInt("homeBootDone", -2) == boot) return;      // на этой загрузке уже открывали
            prefs.raw().edit().putInt("homeBootDone", boot).apply();
            bootRun = true;
            polling = true; pollStart = SystemClock.elapsedRealtime();
            h.post(poll);
        } catch (Throwable ignored) {}
    }

    @Override public boolean onUnbind(Intent i) { h.removeCallbacksAndMessages(null); return super.onUnbind(i); }

    private void openHome() {
        try {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } catch (Throwable ignored) {}
    }

    @Override protected boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_HOME) { polling = false; return false; }      // пользователь сам взялся за пульт — не мешаем
        if (!new Prefs(this).homeKey()) return false;         // перехват «Домой» выключен в настройках
        if (e.getAction() == KeyEvent.ACTION_UP) openHome();
        return true;        // нажатие поглощаем, чтобы система не открыла свой лаунчер
    }
}
