package com.simpletvlauncher;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/** Запуск приложения автозапуска: при включении устройства и (по желанию) после выхода из сна. Общая защита от двойного запуска. */
final class Wake {
    private Wake() {}

    /** Когда устройство проснулось (elapsedRealtime); 0 — не просыпалось или уже обработано. */
    static long wokeAt;
    private static long last;       // когда приложение открывали в последний раз

    /** Устройство только что включили: загрузку обрабатывает отдельная логика, а сон сразу после неё считать не нужно. */
    static boolean afterBoot() { return SystemClock.elapsedRealtime() < 90000L; }

    /** Открывает приложение. Повтор в течение 20 секунд игнорируется (будят сразу и служба, и главный экран). */
    static synchronized boolean openAuto(Context c, String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        long now = SystemClock.elapsedRealtime();
        if (last != 0 && now - last < 20000L) return false;
        try {
            Intent i = c.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = c.getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) return false;
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            last = now;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
