package com.simpletvlauncher;

import android.app.Dialog;
import android.content.Context;

/** Родительский контроль: PIN-код из 4 цифр и защита выбранных приложений (задаётся в меню «Все приложения»). */
final class Parental {
    private Parental() {}

    /** Диалог ввода уже заданного PIN-кода; верный код — выполняется ok, неверный — «Неверный PIN-код» и ввод заново. */
    static Dialog askPin(Context c, final Prefs prefs, String title, String hint, Runnable ok) {
        return GlassDialog.pin(c, title, hint, code -> prefs.checkPin(code) ? null : L.t("Неверный PIN-код"), ok);
    }

    /** Если приложение под защитой — возвращает диалог PIN-кода (после верного кода выполнится go), иначе сразу выполняет go и возвращает null. */
    static Dialog guard(Context c, Prefs prefs, String pkg, String label, Runnable go) {
        if (!prefs.isLocked(pkg)) { go.run(); return null; }
        return askPin(c, prefs, label, L.t("Введите PIN-код"), go);
    }

    /** Задать новый PIN-код: ввод и повтор; затем выполняется done. */
    static Dialog createPin(final Context c, final Prefs prefs, final Runnable done) {
        final String[] first = {null};
        return GlassDialog.pin(c, L.t("Новый PIN-код"), L.t("Придумайте 4 цифры"), code -> { first[0] = code; return null; }, () ->
                GlassDialog.pin(c, L.t("Новый PIN-код"), L.t("Повторите PIN-код"),
                        code2 -> code2.equals(first[0]) ? null : L.t("PIN-коды не совпадают"),
                        () -> { prefs.setPin(first[0]); if (done != null) done.run(); }).show());
    }
}
