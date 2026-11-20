package com.simpletvlauncher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

public final class ColorUtil {
    private ColorUtil() {}

    public static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | ((a & 0xFF) << 24);
    }

    public static int darker(int color, float f) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= f;
        return Color.HSVToColor(hsv);
    }

    /** Насыщенный «средний» цвет иконки для подсветки фона. */
    public static int dominant(Drawable d, int fallback) {
        if (d == null) return fallback;
        try {
            final int n = 24;
            Bitmap b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Drawable.ConstantState cs = d.getConstantState();
            Drawable dd = cs != null ? cs.newDrawable().mutate() : d;
            dd.setBounds(0, 0, n, n);
            dd.draw(c);
            double r = 0, g = 0, bl = 0, wsum = 0;
            float[] hsv = new float[3];
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    int p = b.getPixel(x, y);
                    if (Color.alpha(p) < 120) continue;
                    Color.colorToHSV(p, hsv);
                    double w = hsv[1] * hsv[2] + 0.02;
                    if (hsv[2] < 0.15f) w *= 0.1;
                    r += Color.red(p) * w; g += Color.green(p) * w; bl += Color.blue(p) * w;
                    wsum += w;
                }
            }
            b.recycle();
            if (wsum <= 0) return fallback;
            int col = Color.rgb((int) (r / wsum), (int) (g / wsum), (int) (bl / wsum));
            Color.colorToHSV(col, hsv);
            if (hsv[1] < 0.25f) return fallback;     // серые/белые иконки — тема по умолчанию
            hsv[1] = Math.max(hsv[1], 0.55f);
            hsv[2] = Math.min(0.85f, Math.max(hsv[2], 0.6f));
            return Color.HSVToColor(hsv);
        } catch (Throwable t) {
            return fallback;
        }
    }
}
