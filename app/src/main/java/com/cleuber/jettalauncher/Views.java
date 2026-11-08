package com.cleuber.jettalauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

/** Fundo em fibra de carbono com faixas inclinadas na cor de destaque. */
class CarbonView extends View {
    private final Paint carbon = new Paint();
    private final Paint stripe = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int accent = Ui.DEFAULT_ACCENT;

    CarbonView(Context c) {
        super(c);
        int t = Math.max(4, Ui.s(6));
        Bitmap b = Bitmap.createBitmap(t, t, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        cv.drawColor(Ui.BG);
        Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG);
        lp.setStrokeWidth(Math.max(1f, Ui.sf(2)));
        lp.setColor(0x0AFFFFFF);
        cv.drawLine(0, t, t, 0, lp);
        cv.drawLine(-t, t, t, -t, lp);
        cv.drawLine(0, 2 * t, 2 * t, 0, lp);
        lp.setColor(0x55000000);
        cv.drawLine(0, 0, t, t, lp);
        cv.drawLine(-t, 0, t, 2 * t, lp);
        cv.drawLine(0, -t, 2 * t, t, lp);
        carbon.setShader(new BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
    }

    void setAccent(int a) {
        accent = a;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawRect(0, 0, w, h, carbon);
        stripe.setColor(Ui.alpha(accent, 0.10f));
        skew(c, -Ui.sf(60), h - Ui.sf(30), Ui.sf(420), Ui.sf(70));
        stripe.setColor(Ui.alpha(accent, 0.08f));
        skew(c, -Ui.sf(40), h - Ui.sf(62), Ui.sf(300), Ui.sf(18));
    }

    private void skew(Canvas c, float left, float top, float w, float h) {
        float k = 0.7f * h / 2f;
        path.reset();
        path.moveTo(left + k, top);
        path.lineTo(left + w + k, top);
        path.lineTo(left + w - k, top + h);
        path.lineTo(left - k, top + h);
        path.close();
        c.drawPath(path, stripe);
    }
}

/** Sequência de segmentos inclinados: usada no "///" da marca e nas luzes de troca de marcha. */
class SegmentsView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int[] colors = new int[0];
    private final float segW, segH, gap, skew;

    SegmentsView(Context c, float segW, float segH, float gap, float skew) {
        super(c);
        this.segW = segW;
        this.segH = segH;
        this.gap = gap;
        this.skew = skew;
    }

    void setColors(int[] colors) {
        this.colors = colors;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int n = colors.length;
        int w = (int) Math.ceil(n * segW + Math.max(0, n - 1) * gap + skew * segH) + 1;
        setMeasuredDimension(w, (int) Math.ceil(segH));
    }

    @Override
    protected void onDraw(Canvas c) {
        float off = skew * segH;
        for (int i = 0; i < colors.length; i++) {
            float x = i * (segW + gap);
            path.reset();
            path.moveTo(x + off, 0);
            path.lineTo(x + segW + off, 0);
            path.lineTo(x + segW, segH);
            path.lineTo(x, segH);
            path.close();
            p.setColor(colors[i]);
            c.drawPath(path, p);
        }
    }
}

/** Linha vertical tracejada, lembrando a costura vermelha do volante. */
class DashLineView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    DashLineView(Context c) {
        super(c);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, Ui.sf(2)));
        p.setPathEffect(new DashPathEffect(new float[]{Ui.sf(6), Ui.sf(5)}, 0));
    }

    void setAccent(int a) {
        p.setColor(Ui.alpha(a, 0.55f));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float x = getWidth() / 2f;
        c.drawLine(x, 0, x, getHeight(), p);
    }
}

/**
 * Fundo com canto superior direito chanfrado.
 * mode 0 = só o fundo; 1 = faixa inferior com listras (blocos); 2 = barra lateral esquerda (botões).
 */
class ChamferDrawable extends Drawable {
    static final int PLAIN = 0, TILE = 1, LEFT_BAR = 2;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint acc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path tmp = new Path();
    private final int top, bottom, mode;
    private final float cut;
    private int accent = Ui.DEFAULT_ACCENT;
    private int borderColor = 0;

    ChamferDrawable(int top, int bottom, float cut, int mode) {
        this.top = top;
        this.bottom = bottom;
        this.cut = cut;
        this.mode = mode;
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(Math.max(1f, Ui.sf(1)));
    }

    void setAccent(int a) {
        accent = a;
        invalidateSelf();
    }

    void setBorder(int color) {
        borderColor = color;
        invalidateSelf();
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float l = b.left, t = b.top, r = b.right, bo = b.bottom;
        path.reset();
        path.moveTo(l, t);
        path.lineTo(r - cut, t);
        path.lineTo(r, t + cut);
        path.lineTo(r, bo);
        path.lineTo(l, bo);
        path.close();
        fill.setShader(new LinearGradient(l, t, l + b.width() * 0.35f, bo, top, bottom, Shader.TileMode.CLAMP));
        c.drawPath(path, fill);
        int save = c.save();
        c.clipPath(path);
        acc.setColor(accent);
        if (mode == TILE) {
            float h = Ui.sf(5), w = b.width() * 0.46f;
            c.drawRect(l, bo - h, l + w, bo, acc);
            acc.setColor(Ui.alpha(accent, 0.55f));
            bar(c, l + w + Ui.sf(8), bo - h, Ui.sf(14), h);
            acc.setColor(Ui.alpha(accent, 0.30f));
            bar(c, l + w + Ui.sf(28), bo - h, Ui.sf(14), h);
        } else if (mode == LEFT_BAR) {
            c.drawRect(l, t, l + Ui.sf(5), bo, acc);
        }
        c.restoreToCount(save);
        if (borderColor != 0) {
            border.setColor(borderColor);
            c.drawPath(path, border);
        }
    }

    private void bar(Canvas c, float x, float y, float w, float h) {
        tmp.reset();
        tmp.moveTo(x + h, y);
        tmp.lineTo(x + w + h, y);
        tmp.lineTo(x + w, y + h);
        tmp.lineTo(x, y + h);
        tmp.close();
        c.drawPath(tmp, acc);
    }

    @Override
    public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        fill.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}

/** Escurece levemente qualquer view enquanto ela é pressionada. */
class PressFeedback implements View.OnTouchListener {
    static final PressFeedback INSTANCE = new PressFeedback();

    @Override
    public boolean onTouch(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                break;
            default:
                break;
        }
        return false;
    }
}
