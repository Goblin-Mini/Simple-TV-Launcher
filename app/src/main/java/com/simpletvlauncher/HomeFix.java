package com.simpletvlauncher;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.widget.Toast;

/** Фикс кнопки «Домой» и автозапуск при включении ТВ: нужен доступ к специальным возможностям. */
final class HomeFix {
    private HomeFix() {}

    /** Служба «кнопка Домой» включена в «Специальных возможностях». */
    static boolean isOn(Context c) {
        try {
            String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return s != null && s.contains(c.getPackageName() + "/");
        } catch (Throwable e) { return false; }
    }

    /** Доступ нужен (включён хотя бы один из зависящих от него пунктов), но не получен. */
    static boolean needed(Context c) {
        Prefs p = new Prefs(c);
        return !isOn(c) && (p.homeKey() || p.bootHome());
    }

    static void showHelp(final Context c, final Runnable onChanged) {
        String text = L.t("Чтобы кнопка «Домой» вела в этот лаунчер, а ТВ при включении сразу открывал его, включите службу ")
                + L.q(c.getString(R.string.home_key_label)) + L.t(" в разделе «Специальные возможности».\n\n")
                + L.t("Внимание: если приложение установлено не через ADB, система может сама отключать этот переключатель. ")
                + L.t("Чтобы доступ не слетал, установите приложение через ADB с помощью:\n")
                + L.t("•  atvTools — вариант для смартфона;\n")
                + L.t("•  ADB App Control — вариант для ПК.");
        String[] btns = isOn(c) ? new String[]{L.t("Открыть «Специальные возможности»"), L.t("Закрыть")}
                : new String[]{L.t("Открыть «Специальные возможности»"), L.t("Скрыть это уведомление"), L.t("Закрыть")};
        GlassDialog.info(c, L.t("Доступ к спецвозможностям"), text, btns, i -> {
            if (btns.length == 3 && i == 1) {       // пользователь отказался: больше не напоминаем, зависящие от доступа пункты выключаем
                Prefs p = new Prefs(c);
                p.setHomeKey(false);
                p.setBootHome(false);
                if (onChanged != null) onChanged.run();
                return;
            }
            if (i != 0) return;
            try {
                Intent in = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                if (!(c instanceof android.app.Activity)) in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(in);
                Toast.makeText(c, L.t("Включите ") + L.q(c.getString(R.string.home_key_label)), Toast.LENGTH_LONG).show();
            } catch (Throwable e) { Toast.makeText(c, L.t("Не удалось открыть настройки"), Toast.LENGTH_SHORT).show(); }
        }).show();
    }
}
