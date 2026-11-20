package com.simpletvlauncher;

import android.content.Context;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Язык интерфейса. Исходные тексты в коде — русские; для английского языка каждая фраза ищется в словаре (LDict).
 * Новая фраза: пишем по-русски, оборачиваем в L.t("…") и добавляем перевод в LDict.
 * Язык выбирается автоматически по системе (русский — русский, любой другой — английский) или вручную в настройках.
 */
public final class L {
    private L() {}

    /** Текущий язык: "ru" или "en". */
    public static volatile String code = "ru";
    /** Растёт при каждой смене языка: экраны, построенные раньше, по нему понимают, что пора перестроиться. */
    public static volatile int version = 0;

    private static final Map<String, String> EN = new HashMap<>();
    private static boolean loaded;

    /** Определяет язык по настройке лаунчера и системе. */
    public static void init(Context c) {
        String pref = new Prefs(c).lang();
        String nc;
        if ("ru".equals(pref) || "en".equals(pref)) nc = pref;
        else nc = "ru".equalsIgnoreCase(Locale.getDefault().getLanguage()) ? "ru" : "en";
        if (!nc.equals(code)) { code = nc; version++; }
    }

    public static boolean en() { return "en".equals(code); }

    public static Locale locale() { return en() ? Locale.ENGLISH : new Locale("ru"); }

    /** Перевод фразы (на русском возвращает её же). Нет перевода — тоже исходный текст. */
    public static String t(String ru) {
        if (ru == null || !en()) return ru;
        if (!loaded) { LDict.fill(EN); loaded = true; }
        String r = EN.get(ru);
        return r == null ? ru : r;
    }

    /** Название в кавычках: «…» по-русски, “…” по-английски. */
    public static String q(String s) { return en() ? "“" + s + "”" : "«" + s + "»"; }

    /** Описание погоды из кэша (оно сохранено по-русски): переводится при показе. */
    public static String desc(String ru) {
        if (ru == null || !en()) return ru;
        String direct = t(ru);
        if (!direct.equals(ru)) return direct;
        String s = ru;
        String suffix = "";
        if (s.endsWith(" с грозой")) { suffix = " with thunderstorm"; s = s.substring(0, s.length() - " с грозой".length()); }
        String prefix = "";
        if (s.startsWith("Небольшой ")) { prefix = "Light "; s = s.substring("Небольшой ".length()); }
        else if (s.startsWith("Сильный ")) { prefix = "Heavy "; s = s.substring("Сильный ".length()); }
        String base;
        switch (s.toLowerCase(new Locale("ru"))) {
            case "дождь со снегом": base = "sleet"; break;
            case "снег": base = "snow"; break;
            case "дождь": base = "rain"; break;
            default: return ru;
        }
        String r = prefix.isEmpty() ? Character.toUpperCase(base.charAt(0)) + base.substring(1) : prefix + base;
        return r + suffix;
    }
}
