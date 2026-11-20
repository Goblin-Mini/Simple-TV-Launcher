package com.simpletvlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Выбор клиента VPN и попытка включить/выключить его из лаунчера. */
public final class VpnControl {
    private VpnControl() {}

    private static final String[] HINTS = {"vpn", "amnezia", "happ", "wireguard", "v2ray", "hiddify", "clash", "nekobox",
            "sing-box", "singbox", "openvpn", "outline", "proton", "xray", "shadowsocks", "trojan", "tunnel", "warp",
            "dpi", "zapret", "podkop"};

    private static final java.util.Map<String, Boolean> VPN_SERVICE = new java.util.HashMap<>();

    /**
     * Приложение объявляет системный VPN-сервис (BIND_VPN_SERVICE). Так устроены все клиенты VPN, а также
     * обходы блокировок через локальный VPN (ByeDPI, WG Tunnel и подобные) — независимо от названия.
     */
    private static boolean hasVpnService(Context c, String pkg) {
        Boolean cached = VPN_SERVICE.get(pkg);
        if (cached != null) return cached;
        boolean r = false;
        try {
            android.content.pm.PackageInfo pi = c.getPackageManager().getPackageInfo(pkg, android.content.pm.PackageManager.GET_SERVICES);
            if (pi.services != null) {
                for (android.content.pm.ServiceInfo si : pi.services) {
                    if ("android.permission.BIND_VPN_SERVICE".equals(si.permission)) { r = true; break; }
                }
            }
        } catch (Throwable ignored) {}
        VPN_SERVICE.put(pkg, r);
        return r;
    }

    /** Приложения, похожие на VPN-клиенты или DPI-обходчики: по названию пакета/приложения и по наличию VPN-сервиса. */
    public static List<AppRepo.AppItem> clients(Context c, List<AppRepo.AppItem> all) {
        List<AppRepo.AppItem> res = new ArrayList<>();
        for (AppRepo.AppItem a : all) {
            if (a.pkg.equals(c.getPackageName())) continue;
            String k = (a.pkg + " " + a.label).toLowerCase(Locale.ROOT);
            boolean hit = false;
            for (String h : HINTS) if (k.contains(h)) { hit = true; break; }
            if (hit || hasVpnService(c, a.pkg)) res.add(a);
        }
        return res;
    }

    /**
     * Нажатие на кнопку VPN. Если у клиента есть внешнее управление (v2rayNG, Clash) — переключаем без открытия окна,
     * иначе просто открываем приложение (у Amnezia и Happ внешнего API нет).
     */
    public static void toggle(Context c, String pkg, boolean wantOn) {
        try {
            if (pkg.startsWith("com.v2ray.ang")) {
                Intent i = new Intent("com.v2ray.ang.action.widget.click")
                        .setComponent(new ComponentName(pkg, "com.v2ray.ang.receiver.WidgetProvider"));
                c.sendBroadcast(i);
                return;
            }
            if (pkg.toLowerCase(Locale.ROOT).contains("clash")) {
                Intent i = new Intent(pkg + ".action.TOGGLE_CLASH").setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return;
            }
        } catch (Throwable ignored) {
            // упадём на обычный запуск
        }
        // нет внешнего API: просто открываем клиент (у Amnezia фокус сразу на «Подключиться»)
        launch(c, pkg);
    }

    public static void launch(Context c, String pkg) {
        Intent i = c.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) { UI.start(c, new Intent("android.settings.VPN_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        UI.start(c, i);
    }
}
