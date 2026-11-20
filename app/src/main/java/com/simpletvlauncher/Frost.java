package com.simpletvlauncher;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Единое «матовое стекло» главного экрана: небольшое размытие фона под элементом и одинаковая лёгкая высветленность.
 * Одинаково для виджета погоды, капсулы значков, поля с приложениями и трёх нижних карточек.
 */
final class Frost {
    private Frost() {}

    /** Верх/низ высветленности — единые для всех элементов. */
    static final int TINT_TOP = 0x26FFFFFF, TINT_BOT = 0x16FFFFFF;

    static BgView bg;
    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint BP = new Paint(Paint.FILTER_BITMAP_FLAG);
    private static final RectF R = new RectF();
    private static final Path PATH = new Path();
    private static final int[] A = new int[2], B = new int[2];

    static void draw(View v, Canvas c, float w, float h, float radius) {
        R.set(0, 0, w, h);
        BgView g = bg;
        android.graphics.Bitmap bl = (g != null && g.getWidth() > 0) ? g.blurred() : null;
        if (bl != null) {
            v.getLocationInWindow(A);
            g.getLocationInWindow(B);
            float dx = A[0] - B[0], dy = A[1] - B[1];
            PATH.reset();
            PATH.addRoundRect(R, radius, radius, Path.Direction.CW);
            int sc = c.save();
            c.clipPath(PATH);
            c.translate(-dx, -dy);
            c.drawBitmap(bl, null, new RectF(0, 0, g.getWidth(), g.getHeight()), BP);
            c.restoreToCount(sc);
        }
        P.setStyle(Paint.Style.FILL);
        P.setShader(new LinearGradient(0, 0, 0, h, TINT_TOP, TINT_BOT, Shader.TileMode.CLAMP));
        c.drawRoundRect(R, radius, radius, P);
        P.setShader(null);
    }
}