package com.simpletvlauncher;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Set;

/**
 * Полный список приложений.
 * manage = true: OK скрывает/показывает приложение; manage = false: OK запускает.
 * Удерживайте OK — меню (открыть, скрыть/показать, удалить, о приложении).
 */
public class ManageActivity extends Activity {
    private Prefs prefs;
    private float s;
    private boolean manage;
    private LinearLayout col;
    private ScrollView sv;
    private TextView sub;
    private List<AppRepo.AppItem> all;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        s = UI.scale(this);
        manage = getIntent().getBooleanExtra("manage", false);
        int W = getResources().getDisplayMetrics().widthPixels, H = getResources().getDisplayMetrics().heightPixels;

        FrameLayout root = new FrameLayout(this);
        root.addView(new BgView(this, prefs.settingsAccent()), new FrameLayout.LayoutParams(-1, -1));

        TextView title = UI.text(this, L.t("Все приложения"), 58 * s, 0xFFFFFFFF, GlassView.MED);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(Math.round(W - 192 * s), Math.round(80 * s));
        tl.leftMargin = Math.round(96 * s);
        tl.topMargin = Math.round(50 * s);
        root.addView(title, tl);

        sub = UI.text(this, "", 26 * s, 0xA6FFFFFF, GlassView.REG);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(Math.round(W - 192 * s), Math.round(40 * s));
        sl.leftMargin = Math.round(96 * s);
        sl.topMargin = Math.round(132 * s);
        root.addView(sub, sl);

        sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        sv.addView(col, new ViewGroup.LayoutParams(-1, -2));
        FrameLayout.LayoutParams svl = new FrameLayout.LayoutParams(Math.round(W - 192 * s), H - Math.round(210 * s));
        svl.leftMargin = Math.round(96 * s);
        svl.topMargin = Math.round(190 * s);
        root.addView(sv, svl);
        setContentView(root);
    }

    private int langV = L.version;

    @Override protected void onResume() {
        super.onResume();
        if (L.version != langV) { recreate(); return; }      // язык сменили, пока экран был закрыт
        Weather.IO.execute(() -> {
            final List<AppRepo.AppItem> l = AppRepo.loadAll(this);
            runOnUiThread(() -> { all = l; populate(); });
        });
    }

    private void updateSub() {
        int hidden = 0;
        Set<String> hs = prefs.hidden();
        for (AppRepo.AppItem a : all) if (hs.contains(a.pkg)) hidden++;
        sub.setText(L.t("Всего: ") + all.size() + L.t(" · скрыто: ") + hidden);
    }

    private void populate() {
        int focusIdx = 0;
        View f = getCurrentFocus();
        if (f != null) focusIdx = Math.max(0, col.indexOfChild(f));
        col.removeAllViews();
        for (final AppRepo.AppItem a : all) {
            final GlassView r = new GlassView(this);
            r.left = true;
            r.label = a.label;
            r.appIcon = a.icon;
            r.radius = 22 * s;
            r.focusScale = 1.02f;
            style(r, a);
            r.setOnClickListener(v -> {
                menu(r, a);     // OK: открыть, показать/скрыть, удалить, о приложении
            });
            r.setOnLongClickListener(v -> { menu(r, a); return true; });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.round(88 * s));
            lp.bottomMargin = Math.round(10 * s);
            lp.leftMargin = Math.round(12 * s);
            lp.rightMargin = Math.round(12 * s);
            col.addView(r, lp);
        }
        updateSub();
        if (col.getChildCount() > 0) col.getChildAt(Math.min(focusIdx, col.getChildCount() - 1)).requestFocus();
    }

    private void style(GlassView r, AppRepo.AppItem a) {
        boolean hid = prefs.hidden().contains(a.pkg);
        r.dim = hid;
        r.lock = prefs.isLocked(a.pkg);
        r.value = hid ? L.t("Скрыто") : L.t("Показано");
        r.invalidate();
    }

    private void toggle(GlassView r, AppRepo.AppItem a) {
        Set<String> hs = prefs.hidden();
        if (!hs.remove(a.pkg)) {
            hs.add(a.pkg);
        } else {
            // вернувшееся приложение ставим в конец списка
            java.util.List<String> o = prefs.order();
            o.remove(a.pkg);
            o.add(a.pkg);
            prefs.setOrder(o);
        }
        prefs.setHidden(hs);
        style(r, a);
        updateSub();
    }

    private void launch(final AppRepo.AppItem a) {
        android.app.Dialog d = Parental.guard(this, prefs, a.pkg, a.label, () -> {
            Intent i = new Intent(Intent.ACTION_MAIN).setComponent(a.component())
                    .addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            UI.start(this, i);
        });
        if (d != null) d.show();
    }

    /** Защита приложения PIN-кодом: первый раз попросим придумать код, снять защиту можно только зная код. */
    private void toggleLock(final GlassView r, final AppRepo.AppItem a) {
        if (prefs.isLocked(a.pkg)) {
            Parental.askPin(this, prefs, a.label, L.t("Введите PIN-код, чтобы снять защиту"), () -> {
                java.util.Set<String> ls = prefs.locked();
                ls.remove(a.pkg);
                prefs.setLocked(ls);
                style(r, a);
            }).show();
            return;
        }
        final Runnable lockIt = () -> {
            java.util.Set<String> ls = prefs.locked();
            ls.add(a.pkg);
            prefs.setLocked(ls);
            style(r, a);
        };
        if (prefs.hasPin()) lockIt.run();
        else Parental.createPin(this, prefs, lockIt).show();
    }

    private void menu(final GlassView r, final AppRepo.AppItem a) {
        boolean hid = prefs.hidden().contains(a.pkg);
        boolean lk = prefs.isLocked(a.pkg);
        String[] items = {L.t("Открыть"), hid ? L.t("Показать на рабочем столе") : L.t("Скрыть с рабочего стола"),
                lk ? L.t("Снять защиту PIN-кодом") : L.t("Защитить PIN-кодом"), L.t("Удалить"), L.t("О приложении")};
        GlassDialog.menu(this, a.label, items, i -> {
            switch (i) {
                case 0: launch(a); break;
                case 1: toggle(r, a); break;
                case 2: toggleLock(r, a); break;
                case 3: UI.start(this, new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg))); break;
                case 4: UI.start(this, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg))); break;
            }
        }).show();
    }
}
