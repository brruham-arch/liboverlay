package com.brruham.overlay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;

public class OverlayBridge {

    // ── Native callbacks ke C++ ───────────────────────────────
    public static native void nativeOnButton(String id);
    public static native void nativeOnToggle(String id, boolean value);
    public static native void nativeOnSlider(String id, float value);
    public static native void nativeOnText(String id, String value);
    public static native void nativeOnCheckbox(String id, boolean value);

    // ── State ─────────────────────────────────────────────────
    private static WindowManager wm;
    private static View          fabView;
    private static View          panelView;
    private static boolean       panelVisible = false;
    private static boolean       minimized    = false;
    private static Handler       uiHandler    = new Handler(Looper.getMainLooper());

    // ── Helpers ───────────────────────────────────────────────
    private static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v,
            c.getResources().getDisplayMetrics()));
    }
    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    // ── Colors ────────────────────────────────────────────────
    static final int C_BG    = Color.parseColor("#F2121218");
    static final int C_HDR   = Color.parseColor("#FF0A0A10");
    static final int C_CYAN  = Color.parseColor("#FF00E5D8");
    static final int C_CYAN2 = Color.parseColor("#FF00A898");
    static final int C_GREEN = Color.parseColor("#FF1AF072");
    static final int C_RED   = Color.parseColor("#FFF24040");
    static final int C_TEXT  = Color.parseColor("#FFE5E5F0");
    static final int C_DIM   = Color.parseColor("#FF808099");
    static final int C_FRAME = Color.parseColor("#FF141420");
    static final int C_BDR   = Color.parseColor("#FF303048");

    private static GradientDrawable rr(Context c, int color, float r) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(c, r));
        d.setColor(color);
        return d;
    }
    private static GradientDrawable rrb(Context c, int color, int b, float r) {
        GradientDrawable d = rr(c, color, r);
        d.setStroke(dp(c, b), C_BDR);
        return d;
    }

    // ── Entry point dari C++ ──────────────────────────────────
    public static void initSimple(final Context ctx) {
        uiHandler.post(new Runnable() {
            public void run() {
                wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
                buildFAB(ctx);
                buildPanel(ctx);
            }
        });
    }

    public static void destroy() {
        uiHandler.post(new Runnable() {
            public void run() {
                try { if (fabView   != null) wm.removeView(fabView);   } catch (Exception e) {}
                try { if (panelView != null) wm.removeView(panelView); } catch (Exception e) {}
            }
        });
    }

    // ── FAB ──────────────────────────────────────────────────
    private static void buildFAB(final Context ctx) {
        int sz = dp(ctx, 50);
        final TextView tv = new TextView(ctx);
        tv.setText("VFX");
        tv.setTextColor(C_CYAN);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(rrb(ctx, C_HDR, 1, 14));

        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            sz, sz,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = dp(ctx, 8);
        lp.y = dp(ctx, 200);

        tv.setOnTouchListener(new Drag(lp, wm) {
            void tap() {
                panelVisible = !panelVisible;
                if (panelView != null)
                    panelView.setVisibility(panelVisible ? View.VISIBLE : View.GONE);
            }
        });
        wm.addView(tv, lp);
        fabView = tv;
    }

    // ── Main Panel ───────────────────────────────────────────
    private static void buildPanel(final Context ctx) {
        int W = dp(ctx, 300);
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(rrb(ctx, C_BG, 1, 16));

        // Header
        LinearLayout hdr = new LinearLayout(ctx);
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        hdr.setBackground(rr(ctx, C_HDR, 16));
        hdr.setPadding(dp(ctx,10), dp(ctx,8), dp(ctx,10), dp(ctx,8));
        hdr.setGravity(Gravity.CENTER_VERTICAL);

        TextView bX = mkBtn(ctx, "x", C_RED, dp(ctx,28), dp(ctx,28), 8);
        bX.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                panelVisible = false;
                root.setVisibility(View.GONE);
            }
        });
        hdr.addView(bX);

        final TextView bMin = mkBtn(ctx, "_", C_DIM, dp(ctx,28), dp(ctx,28), 8);
        bMin.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                minimized = !minimized;
                for (int i = 1; i < root.getChildCount(); i++)
                    root.getChildAt(i).setVisibility(minimized ? View.GONE : View.VISIBLE);
                bMin.setText(minimized ? "+" : "_");
            }
        });
        hdr.addView(bMin);

        TextView ttl = new TextView(ctx);
        ttl.setText("  OVERLAY v1");
        ttl.setTextColor(C_CYAN);
        ttl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        hdr.addView(ttl, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(hdr, matchWrap());

        // Body
        ScrollView sv = new ScrollView(ctx);
        sv.setVerticalScrollBarEnabled(false);
        final LinearLayout body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(ctx,12), dp(ctx,8), dp(ctx,12), dp(ctx,8));

        // Buttons
        sec(ctx, body, "BUTTONS");
        LinearLayout brow = new LinearLayout(ctx);
        brow.setOrientation(LinearLayout.HORIZONTAL);

        TextView b1 = mkBtn(ctx, "Action 1", C_CYAN2, 0, dp(ctx,36), 8);
        b1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { nativeOnButton("action1"); }
        });
        LinearLayout.LayoutParams b1p = new LinearLayout.LayoutParams(0, dp(ctx,36), 1f);
        b1p.setMarginEnd(dp(ctx, 4));
        brow.addView(b1, b1p);

        TextView b2 = mkBtn(ctx, "Action 2", C_CYAN2, 0, dp(ctx,36), 8);
        b2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { nativeOnButton("action2"); }
        });
        brow.addView(b2, new LinearLayout.LayoutParams(0, dp(ctx,36), 1f));
        body.addView(brow, matchWrap());
        gap(ctx, body, 8);

        // Toggles
        sec(ctx, body, "TOGGLES");
        body.addView(toggleRow(ctx, "toggle1", "Feature A", false), matchWrap());
        gap(ctx, body, 4);
        body.addView(toggleRow(ctx, "toggle2", "Feature B", true), matchWrap());
        gap(ctx, body, 8);

        // Checkboxes
        sec(ctx, body, "CHECKBOXES");
        body.addView(checkRow(ctx, "chk1", "Option 1", false), matchWrap());
        gap(ctx, body, 4);
        body.addView(checkRow(ctx, "chk2", "Option 2", true), matchWrap());
        gap(ctx, body, 8);

        // Sliders
        sec(ctx, body, "SLIDERS");
        body.addView(sliderRow(ctx, "slider1", "Pitch",   0, 400, 100), matchWrap());
        gap(ctx, body, 4);
        body.addView(sliderRow(ctx, "slider2", "Formant", 0, 400, 100), matchWrap());
        gap(ctx, body, 8);

        // Input
        sec(ctx, body, "INPUT TEXT");
        body.addView(inputRow(ctx, "input1", "Ketik sesuatu..."), matchWrap());
        gap(ctx, body, 8);

        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 400)));

        // WindowManager params
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            W, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            android.graphics.PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(ctx, 20);
        lp.y = dp(ctx, 80);

        hdr.setOnTouchListener(new Drag(lp, wm) {
            void tap() {}
        });

        root.setVisibility(View.GONE);
        wm.addView(root, lp);
        panelView = root;
    }

    // ── Widgets ───────────────────────────────────────────────
    static TextView mkBtn(Context c, String txt, int col, int w, int h, float r) {
        TextView tv = new TextView(c);
        tv.setText(txt);
        tv.setTextColor(Color.BLACK);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(rr(c, col, r));
        if (w > 0) tv.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        return tv;
    }

    static LinearLayout toggleRow(Context c, final String id, String lbl, boolean def) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(rr(c, C_FRAME, 8));
        row.setPadding(dp(c,10), dp(c,6), dp(c,10), dp(c,6));

        TextView lv = new TextView(c);
        lv.setText(lbl);
        lv.setTextColor(C_TEXT);
        lv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        row.addView(lv, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final ToggleButton tb = new ToggleButton(c);
        tb.setTextOn("ON"); tb.setTextOff("OFF");
        tb.setChecked(def);
        tb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        tb.setTextColor(def ? C_GREEN : C_DIM);
        tb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton v, boolean on) {
                tb.setTextColor(on ? C_GREEN : C_DIM);
                nativeOnToggle(id, on);
            }
        });
        row.addView(tb, new LinearLayout.LayoutParams(dp(c,60), dp(c,30)));
        return row;
    }

    static LinearLayout checkRow(Context c, final String id, String lbl, boolean def) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(rr(c, C_FRAME, 8));
        row.setPadding(dp(c,10), dp(c,6), dp(c,10), dp(c,6));

        TextView lv = new TextView(c);
        lv.setText(lbl);
        lv.setTextColor(C_TEXT);
        lv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        row.addView(lv, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        CheckBox chk = new CheckBox(c);
        chk.setChecked(def);
        chk.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton v, boolean on) {
                nativeOnCheckbox(id, on);
            }
        });
        row.addView(chk);
        return row;
    }

    static LinearLayout sliderRow(Context c, final String id,
            String lbl, int min, int max, int def) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackground(rr(c, C_FRAME, 8));
        col.setPadding(dp(c,10), dp(c,6), dp(c,10), dp(c,6));

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);

        TextView lv = new TextView(c);
        lv.setText(lbl);
        lv.setTextColor(C_DIM);
        lv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        top.addView(lv, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final TextView vt = new TextView(c);
        final int maxF = max > 0 ? max : 1;
        float init = (float)def / (float)maxF * 4.0f;
        vt.setText(String.format("%.2f", init));
        vt.setTextColor(C_CYAN);
        vt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        top.addView(vt);
        col.addView(top, matchWrap());

        SeekBar sb = new SeekBar(c);
        sb.setMax(max);
        sb.setProgress(def);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                float v = (float)p / (float)maxF * 4.0f;
                vt.setText(String.format("%.2f", v));
                nativeOnSlider(id, v);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        col.addView(sb, matchWrap());
        return col;
    }

    static LinearLayout inputRow(Context c, final String id, String hint) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(rr(c, C_FRAME, 8));
        row.setPadding(dp(c,8), dp(c,4), dp(c,8), dp(c,4));
        row.setGravity(Gravity.CENTER_VERTICAL);

        final EditText et = new EditText(c);
        et.setHint(hint);
        et.setHintTextColor(C_DIM);
        et.setTextColor(C_TEXT);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        et.setBackground(null);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        row.addView(et, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView ok = mkBtn(c, "OK", C_CYAN2, dp(c,36), dp(c,28), 6);
        ok.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                nativeOnText(id, et.getText().toString());
                et.setText("");
            }
        });
        row.addView(ok);
        return row;
    }

    static void sec(Context c, LinearLayout p, String t) {
        TextView tv = new TextView(c);
        tv.setText(t);
        tv.setTextColor(C_DIM);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        tv.setPadding(0, 0, 0, dp(c, 4));
        p.addView(tv, matchWrap());
    }

    static void gap(Context c, LinearLayout p, int d) {
        View v = new View(c);
        p.addView(v, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(c, d)));
    }

    // ── Drag touch ───────────────────────────────────────────
    static abstract class Drag implements View.OnTouchListener {
        final WindowManager.LayoutParams lp;
        final WindowManager wm;
        int ix, iy, tx, ty;
        boolean drag;

        Drag(WindowManager.LayoutParams lp, WindowManager wm) {
            this.lp = lp; this.wm = wm;
        }
        abstract void tap();

        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    ix = lp.x; iy = lp.y;
                    tx = (int)e.getRawX(); ty = (int)e.getRawY();
                    drag = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int)e.getRawX() - tx;
                    int dy = (int)e.getRawY() - ty;
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) drag = true;
                    if (drag) {
                        lp.x = ix + dx; lp.y = iy + dy;
                        try { wm.updateViewLayout(v, lp); } catch (Exception ex) {}
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!drag) tap();
                    return true;
            }
            return false;
        }
    }
}
