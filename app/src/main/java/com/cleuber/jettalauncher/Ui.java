package com.cleuber.jettalauncher;

import android.content.Context;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.widget.TextView;

/** Cores, fontes e escala do tema "escuro esportivo". O layout foi desenhado para 1024x600. */
final class Ui {
    static final int BG = 0xFF0A0B0D;
    static final int PANEL = 0xFF12161B;
    static final int PANEL2 = 0xFF1A1F25;
    static final int BUTTON = 0xFF15191F;
    static final int LINE = 0xFF2A3038;
    static final int TEXT = 0xFFF2F4F7;
    static final int TEXT2 = 0xFFC5CBD3;
    static final int MUTED = 0xFF9AA3AE;
    static final int MUTED2 = 0xFF8A939E;
    static final int DEFAULT_ACCENT = 0xFFE10600;

    static final int[] PALETTE = {0xFFE10600, 0xFFFF6A00, 0xFFE8B500, 0xFF22C55E,
            0xFF00B4D8, 0xFF3B82F6, 0xFFA855F7, 0xFFF2F4F7};
    static final String[] PALETTE_NAMES = {"Vermelho", "Laranja", "Amarelo", "Verde",
            "Ciano", "Azul", "Roxo", "Branco"};

    static float scale = 1f;
    static Typeface cond, condBold, body, bodyMed;

    private Ui() {}

    static void init(Context c) {
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        int w = Math.max(dm.widthPixels, dm.heightPixels);
        int h = Math.min(dm.widthPixels, dm.heightPixels);
        scale = Math.min(w / 1024f, h / 600f);
        if (scale <= 0f) scale = 1f;
        if (cond == null) {
            cond = load(c, "fonts/BarlowCondensed-SemiBoldItalic.ttf",
                    Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
            condBold = load(c, "fonts/BarlowCondensed-BoldItalic.ttf",
                    Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
            body = load(c, "fonts/Barlow-Regular.ttf", Typeface.create("sans-serif", Typeface.NORMAL));
            bodyMed = load(c, "fonts/Barlow-Medium.ttf", Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
    }

    private static Typeface load(Context c, String path, Typeface fallback) {
        try {
            return Typeface.createFromAsset(c.getAssets(), path);
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Converte um tamanho do desenho (px em 1024x600) para pixels reais da tela. */
    static int s(float v) {
        return Math.max(1, Math.round(v * scale));
    }

    static float sf(float v) {
        return v * scale;
    }

    static int alpha(int color, float a) {
        return (color & 0x00FFFFFF) | (Math.round(Math.max(0f, Math.min(1f, a)) * 255f) << 24);
    }

    static TextView text(Context c, String s, Typeface tf, float size, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTypeface(tf);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sf(size));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }
}
