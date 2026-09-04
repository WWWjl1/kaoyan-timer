package com.screenguard.timer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * 今日使用分布柱状图（可横向滚动）：
 * 横轴 00:00–24:00（每 1 小时一段），纵轴 0–60 分钟。
 * 学习=蓝、娱乐=橙（叠加），柱顶显示该小时总分钟数。
 * 宽度按 48dp/小时 计算，24 小时总宽超出屏幕时可左右滑动查看。
 */
public class HourBarView extends View {

    private long[] study = new long[24];
    private long[] fun = new long[24];
    private int cell;
    private int labelW;

    private final Paint pStudy = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFun = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pAxis = new Paint(Paint.ANTI_ALIAS_FLAG);

    public HourBarView(Context c) {
        this(c, null);
    }

    public HourBarView(Context c, AttributeSet a) {
        super(c, a);
        float d = getResources().getDisplayMetrics().density;
        cell = (int) (48 * d);
        labelW = (int) (40 * d);
        pStudy.setColor(0xFF2196F3);
        pFun.setColor(0xFFFF9800);
        pAxis.setColor(0xFFBDBDBD);
        pText.setColor(0xFF9E9E9E);
        pText.setTextSize(getResources().getDisplayMetrics().scaledDensity * 10f);
        pText.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(long[] s, long[] f) {
        if (s != null) study = s;
        if (f != null) fun = f;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = labelW + cell * 24;
        int h = MeasureSpec.getSize(heightMeasureSpec);
        if (h == 0) h = (int) (220 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = labelW, right = getWidth() - 4f;
        float top = 20f, bottom = getHeight() - 26f;
        float areaW = right - left, areaH = bottom - top;
        int maxY = 60;

        // 纵轴刻度
        for (int v = 0; v <= maxY; v += 15) {
            float y = bottom - (v / (float) maxY) * areaH;
            pAxis.setStrokeWidth(1f);
            canvas.drawLine(left, y, right, y, pAxis);
            pText.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(String.valueOf(v), left - 4, y + 4, pText);
        }
        pText.setTextAlign(Paint.Align.CENTER);

        // 每根柱占 50%（左右留空隙）
        float barW = cell * 0.5f;
        for (int i = 0; i < 24; i++) {
            float cx = left + i * cell + cell / 2f;
            float sx = cx - barW / 2f;
            float minStudy = study[i] / 60000f;
            float minFun = fun[i] / 60000f;
            float total = minStudy + minFun;
            float scale = (total > maxY) ? (maxY / total) : 1f;
            float hs = (minStudy * scale / (float) maxY) * areaH;
            float hf = (minFun * scale / (float) maxY) * areaH;

            if (hs > 0.5f) {
                float yb = bottom, yt = yb - hs;
                canvas.drawRect(sx, yt, sx + barW, yb, pStudy);
            }
            if (hf > 0.5f) {
                float yb = bottom - hs, yt = yb - hf;
                canvas.drawRect(sx, yt, sx + barW, yb, pFun);
            }
            if (total > 0.05f) {
                canvas.drawText(String.valueOf(Math.round(total)), cx,
                        Math.max(12f, bottom - hs - hf - 4f), pText);
            }
        }

        // 横轴标签：每 4 小时
        for (int i = 0; i <= 24; i += 4) {
            float x = left + i * cell;
            if (x > right) x = right;
            canvas.drawText((i == 24 ? "24" : String.valueOf(i)) + ":00", x, getHeight() - 8, pText);
        }
    }
}
