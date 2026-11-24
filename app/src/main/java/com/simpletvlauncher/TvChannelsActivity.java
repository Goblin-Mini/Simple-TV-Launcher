package com.simpletvlauncher;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** TV-channel browser and per-launcher visibility manager. */
public final class TvChannelsActivity extends Activity {
    private static final int REQUEST_TV_LISTINGS = 4107;
    private float scale;
    private LinearLayout rows;
    private TextView subtitle;
    private List<TvChannels.Channel> channels = new ArrayList<>();
    private TvChannels.Channel opened;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        scale = UI.scale(this);
        buildShell();
        if (Build.VERSION.SDK_INT >= 26 && !TvChannels.hasPermission(this)) {
            if (Build.VERSION.SDK_INT >= 23) requestPermissions(
                    new String[]{Manifest.permission.READ_TV_LISTINGS}, REQUEST_TV_LISTINGS);
        } else refresh();
    }

    private void buildShell() {
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        FrameLayout root = new FrameLayout(this);
        root.addView(new BgView(this, new Prefs(this).settingsAccent()),
                new FrameLayout.LayoutParams(-1, -1));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(Math.round(70 * scale), Math.round(36 * scale),
                Math.round(70 * scale), Math.round(30 * scale));
        TextView title = UI.text(this, L.t("Каналы Android TV"), 42 * scale,
                Color.WHITE, GlassView.MED);
        page.addView(title, new LinearLayout.LayoutParams(-1, Math.round(62 * scale)));
        subtitle = UI.text(this, "", 19 * scale, 0xBFFFFFFF, GlassView.REG);
        page.addView(subtitle, new LinearLayout.LayoutParams(-1, Math.round(42 * scale)));
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView back = UI.text(this, L.t("Назад"), 20 * scale, Color.WHITE, GlassView.MED);
        back.setGravity(Gravity.CENTER);
        back.setFocusable(true);
        back.setOnClickListener(v -> {
            if (opened != null) { opened = null; refresh(); }
            else finish();
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                Math.round(180 * scale), Math.round(54 * scale));
        bp.topMargin = Math.round(12 * scale);
        page.addView(back, bp);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    private void refresh() {
        if (Build.VERSION.SDK_INT < 26) {
            subtitle.setText(L.t("Каналы Android TV доступны начиная с Android 8.0."));
            rows.removeAllViews();
            return;
        }
        if (!TvChannels.hasPermission(this)) {
            subtitle.setText(L.t("Нет разрешения на чтение списка каналов."));
            rows.removeAllViews();
            addMessage(L.t("Разрешите доступ к телепрограмме в настройках приложения и вернитесь сюда."));
            return;
        }
        channels = TvChannels.load(this, false);
        render();
    }

    private void render() {
        rows.removeAllViews();
        if (opened != null) {
            subtitle.setText(opened.appName + " · " + opened.name);
            addMessage(L.t("Программы канала"));
            if (opened.programs.isEmpty()) addMessage(L.t("Приложение пока не опубликовало программы."));
            for (TvChannels.Program p : opened.programs) {
                TextView item = row(p.title + (p.description.isEmpty() ? "" : "\n" + p.description),
                        opened.appName, true);
                item.setOnClickListener(v -> launchProgram(p));
                rows.addView(item);
            }
            return;
        }
        int visible = 0;
        for (TvChannels.Channel c : channels) if (!TvChannels.isHidden(this, c)) visible++;
        subtitle.setText(L.t("Найдено каналов: ") + channels.size()
                + L.t(" · показывается: ") + visible);
        if (channels.isEmpty()) {
            addMessage(L.t("Установленные приложения пока не опубликовали каналы Android TV."));
            return;
        }
        for (TvChannels.Channel c : channels) {
            LinearLayout wrap = new LinearLayout(this);
            wrap.setOrientation(LinearLayout.HORIZONTAL);
            wrap.setGravity(Gravity.CENTER_VERTICAL);
            TextView info = row(c.name, c.appName, true);
            info.setOnClickListener(v -> { opened = c; render(); });
            wrap.addView(info, new LinearLayout.LayoutParams(0, Math.round(78 * scale), 1f));
            CheckBox visibleBox = new CheckBox(this);
            visibleBox.setText(L.t("Показывать"));
            visibleBox.setTextColor(Color.WHITE);
            visibleBox.setTextSize(16 * scale);
            visibleBox.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFFE4C78B));
            visibleBox.setChecked(!TvChannels.isHidden(this, c));
            visibleBox.setOnCheckedChangeListener((button, checked) -> {
                TvChannels.setHidden(this, c, !checked);
                int oldFocus = rows.indexOfChild(wrap);
                refresh();
                if (oldFocus >= 0 && oldFocus < rows.getChildCount()) rows.getChildAt(oldFocus).requestFocus();
            });
            wrap.addView(visibleBox);
            rows.addView(wrap);
        }
    }

    private TextView row(String title, String detail, boolean focusable) {
        TextView v = UI.text(this, title + "\n" + detail, 20 * scale, Color.WHITE, GlassView.MED);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(Math.round(18 * scale), 0, Math.round(18 * scale), 0);
        v.setBackgroundColor(0x24FFFFFF);
        v.setFocusable(focusable);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.round(78 * scale));
        lp.bottomMargin = Math.round(8 * scale);
        v.setLayoutParams(lp);
        return v;
    }

    private void addMessage(String message) {
        TextView v = row(message, "", false);
        rows.addView(v);
    }

    private void launchProgram(TvChannels.Program p) {
        if (p.intentUri.isEmpty()) {
            Toast.makeText(this, L.t("У программы нет ссылки для запуска."), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = Intent.parseUri(p.intentUri, Intent.URI_INTENT_SCHEME);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(p.intentUri))); }
            catch (Exception ignored) {
                Toast.makeText(this, L.t("Не удалось открыть программу."), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_TV_LISTINGS) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) refresh();
            else {
                subtitle.setText(L.t("Доступ к каналам не предоставлен."));
                addMessage(L.t("Без разрешения Android не позволяет получить список каналов других приложений."));
            }
        }
    }

    @Override public void onBackPressed() {
        if (opened != null) { opened = null; render(); }
        else super.onBackPressed();
    }
}
