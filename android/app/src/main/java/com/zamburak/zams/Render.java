package com.zamburak.zams;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Draws each widget as a bitmap, sized to the widget, in the app's colors. */
final class Render {
    static final int LIST = 0, DAY = 1, WEEK = 2;

    private static final String[] CL = {"#2F5BEA", "#D2601A", "#1E8757", "#8740CF", "#C6344A", "#127B89", "#9E7908", "#5B6878"};
    private static final String[] CD = {"#7C9BFF", "#F29A5C", "#5CC795", "#B98AF0", "#F07A8B", "#4FC2D0", "#E2BB4A", "#9AA7B6"};

    private final Data data;
    private final boolean dark;
    private final float d;
    private final int W, H;
    private final Canvas c;
    private final Bitmap bmp;
    private final int bg, ink, muted, line, accent, warn, danger, tint;
    private final LocalDate today;
    private final LocalDateTime now;
    private final int nowM;
    private final long fetchedAt;
    private final String error;

    private Render(Data data, boolean dark, float density, float wDp, float hDp, long fetchedAt, String error) {
        this.data = data;
        this.dark = dark;
        float dd = density;
        float px = wDp * dd * hDp * dd;
        float max = 2_000_000f;
        if (px > max) dd = dd * (float) Math.sqrt(max / px);
        this.d = dd;
        this.W = Math.max(1, Math.round(wDp * dd));
        this.H = Math.max(1, Math.round(hDp * dd));
        this.bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        this.c = new Canvas(bmp);
        this.bg = Color.parseColor(dark ? "#161D26" : "#FFFFFF");
        this.ink = Color.parseColor(dark ? "#E5EAF0" : "#16202B");
        this.muted = Color.parseColor(dark ? "#94A1AF" : "#5D6A78");
        this.line = Color.parseColor(dark ? "#28323E" : "#E3E8EE");
        this.accent = Color.parseColor(dark ? "#7F98FF" : "#2747C9");
        this.warn = Color.parseColor(dark ? "#F0A060" : "#B4541A");
        this.danger = Color.parseColor(dark ? "#F27689" : "#B42339");
        this.tint = mix(accent, bg, dark ? 0.10f : 0.06f);
        this.now = LocalDateTime.now();
        this.today = now.toLocalDate();
        this.nowM = now.getHour() * 60 + now.getMinute();
        this.fetchedAt = fetchedAt;
        this.error = error;
    }

    /** status != null draws a message instead of data (not logged in, first load...). */
    static Bitmap draw(int kind, Data data, String status, float density, float wDp, float hDp, boolean dark, long fetchedAt, String error) {
        Render r = new Render(data, dark, density, wDp, hDp, fetchedAt, error);
        r.frame();
        if (status != null) {
            r.message(kind == WEEK ? "Hafta" : "Bugün", status);
        } else if (kind == LIST) r.list();
        else if (kind == DAY) r.day();
        else r.week();
        return r.bmp;
    }

    // ---------- paint helpers
    private static int mix(int fg, int bg, float t) {
        return Color.rgb(
                Math.round(Color.red(bg) + (Color.red(fg) - Color.red(bg)) * t),
                Math.round(Color.green(bg) + (Color.green(fg) - Color.green(bg)) * t),
                Math.round(Color.blue(bg) + (Color.blue(fg) - Color.blue(bg)) * t));
    }

    private int catColor(String catId) {
        String tok = data.colorOf(catId);
        int i = 7;
        if (tok != null && tok.length() == 2 && tok.charAt(0) == 'c') {
            int n = tok.charAt(1) - '1';
            if (n >= 0 && n < 8) i = n;
        }
        return Color.parseColor(dark ? CD[i] : CL[i]);
    }

    private TextPaint tp(float sizeDp, boolean bold, int color) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(sizeDp * d);
        p.setColor(color);
        p.setTypeface(bold ? Typeface.create(Typeface.DEFAULT, Typeface.BOLD) : Typeface.DEFAULT);
        return p;
    }

    private Paint fill(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        return p;
    }

    private Paint stroke(int color, float wDp) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(wDp * d);
        p.setColor(color);
        return p;
    }

    private static String ell(String s, TextPaint p, float w) {
        if (w <= 0) return "";
        return TextUtils.ellipsize(s, p, w, TextUtils.TruncateAt.END).toString();
    }

    private static String shortTitle(String t) {
        return t == null ? "" : t.replaceFirst("\\.\\d+(?=\\s|$)", "");
    }

    private String place(String loc, String campus, boolean full) {
        String sym = Data.campusSym(campus);
        if (full) {
            String nm = Data.campusName(campus);
            String cp = sym.isEmpty() ? "" : sym + nm;
            if (loc == null || loc.isEmpty()) return cp;
            return cp.isEmpty() ? loc : loc + " " + cp;
        }
        return (loc == null ? "" : loc) + sym;
    }

    // ---------- frame & header
    private void frame() {
        c.drawRoundRect(new RectF(0, 0, W, H), 18 * d, 18 * d, fill(bg));
    }

    private String updatedText() {
        if (error != null) return "güncellenemedi";
        if (fetchedAt <= 0) return "";
        LocalTime t = Instant.ofEpochMilli(fetchedAt).atZone(ZoneId.systemDefault()).toLocalTime();
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    private float header(String title, String sub) {
        float x = 14 * d, y = 25 * d;
        TextPaint tp = tp(15, true, ink);
        c.drawText(title, x, y, tp);
        float tw = tp.measureText(title);
        String up = updatedText();
        String s = sub + (up.isEmpty() ? "" : " · " + up);
        TextPaint sp = tp(11.5f, false, error != null ? warn : muted);
        c.drawText(ell(s, sp, W - (x + tw + 8 * d) - 40 * d), x + tw + 8 * d, y, sp);
        return 38 * d;
    }

    private String todaySub() {
        return Data.DSHORT[today.getDayOfWeek().getValue() - 1] + " " + today.getDayOfMonth() + " " + Data.MSHORT[today.getMonthValue() - 1];
    }

    private void wrapped(String text, TextPaint p, float x, float y, float w, Layout.Alignment al) {
        if (w <= 0) return;
        StaticLayout sl = StaticLayout.Builder.obtain(text, 0, text.length(), p, Math.max(1, (int) w)).setAlignment(al).build();
        c.save();
        c.translate(x, y);
        sl.draw(c);
        c.restore();
    }

    private void centerMsg(String text, float top) {
        TextPaint p = tp(13, false, muted);
        float w = W - 32 * d;
        StaticLayout sl = StaticLayout.Builder.obtain(text, 0, text.length(), p, Math.max(1, (int) w)).setAlignment(Layout.Alignment.ALIGN_CENTER).build();
        float y = top + Math.max(0, (H - top - sl.getHeight()) / 2f - 6 * d);
        c.save();
        c.translate(16 * d, y);
        sl.draw(c);
        c.restore();
    }

    private void message(String title, String msg) {
        float top = header(title, "Zam's");
        centerMsg(msg, top);
    }

    // ---------- LIST: today's tasks
    private void list() {
        float top = header("Bugünün işleri", todaySub());
        List<Data.Todo> ts = data.todayTodos(today, now);
        if (ts.isEmpty()) { centerMsg("Bugüne düşen iş yok.", top); return; }
        float rowH = 40 * d, y = top + 2 * d, bottom = H - 6 * d;
        int fit = Math.max(1, (int) ((bottom - y) / rowH));
        int show = ts.size() <= fit ? ts.size() : Math.max(1, fit - 1);
        for (int i = 0; i < show; i++) { row(ts.get(i), y); y += rowH; }
        if (ts.size() > show) {
            TextPaint p = tp(12, true, accent);
            c.drawText("+" + (ts.size() - show) + " iş daha", 38 * d, y + 18 * d, p);
        }
    }

    private void row(Data.Todo t, float y) {
        int cc = catColor(t.cat);
        String st = Data.state(t, now);
        float cx = 22 * d, cy = y + 19 * d, r = 8 * d;
        if (t.done) {
            c.drawCircle(cx, cy, r + d, fill(cc));
            Path ch = new Path();
            ch.moveTo(cx - 4 * d, cy);
            ch.lineTo(cx - 1 * d, cy + 3 * d);
            ch.lineTo(cx + 4.5f * d, cy - 3.5f * d);
            Paint cp = stroke(bg, 2);
            cp.setStrokeCap(Paint.Cap.ROUND);
            cp.setStrokeJoin(Paint.Join.ROUND);
            c.drawPath(ch, cp);
        } else {
            c.drawCircle(cx, cy, r, stroke("missed".equals(st) ? warn : cc, 2));
        }
        float x = 38 * d, avail = W - x - 12 * d;
        TextPaint tp = tp(13.5f, true, t.done ? muted : ink);
        if (t.done) tp.setFlags(tp.getFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        c.drawText(ell((t.prepId != null ? "↳ " : "") + t.title, tp, avail), x, y + 17 * d, tp);

        String sub;
        int sc = muted;
        String td = today.toString();
        if (t.done) sub = "yapıldı";
        else if ("missed".equals(st)) { sub = "kaçtı · " + Data.dayLabel(t.schedDate, today) + " " + t.schedStart; sc = warn; }
        else if (t.schedDate != null) sub = t.schedStart + "–" + Data.fromMin(Data.toMin(t.schedStart) + (t.dur > 0 ? t.dur : 60)) + (td.equals(t.schedDate) ? "" : " · " + Data.dayLabel(t.schedDate, today));
        else if (t.due != null && t.due.compareTo(td) < 0) { sub = "gecikti · son: " + Data.dayLabel(t.due, today); sc = warn; }
        else sub = "gün içinde";
        String mt = data.mainTitle(t);
        if (mt != null) sub += " · " + mt + " için";
        TextPaint sp = tp(11.5f, false, sc);
        c.drawText(ell(sub, sp, avail), x, y + 32 * d, sp);
    }

    // ---------- DAY: today's timeline
    private void day() {
        float top = header("Bugün", todaySub());
        List<Data.Item> items = data.itemsForDate(today, now);
        List<Data.Todo> un = new ArrayList<>();
        for (Data.Todo t : data.untimedOn(today)) if (!t.done) un.add(t);
        if (H / d < 150) { compact(items, top); return; }
        float fh = un.isEmpty() ? 0 : 24 * d;
        if (items.isEmpty()) {
            centerMsg(un.isEmpty() ? "Bugün takvimde bir şey yok." : "Bugün saatli bir şey yok.", top);
        } else {
            int lo = Integer.MAX_VALUE, hi = 0;
            for (Data.Item it : items) { lo = Math.min(lo, it.s); hi = Math.max(hi, it.e); }
            if (nowM >= 7 * 60 && nowM <= 23 * 60) { lo = Math.min(lo, nowM); hi = Math.max(hi, nowM + 30); }
            int sh = lo / 60, eh = (hi + 59) / 60;
            while (eh - sh < 4) { if (eh < 24) eh++; else if (sh > 0) sh--; else break; }
            float x0 = 12 * d + 30 * d, x1 = W - 10 * d;
            timeline(items, x0, x1, top + 8 * d, H - 10 * d - fh, sh, eh, true, false);
        }
        if (!un.isEmpty()) {
            StringBuilder sb = new StringBuilder("Gün içinde: ");
            for (int i = 0; i < un.size(); i++) { if (i > 0) sb.append(", "); sb.append(un.get(i).title); }
            TextPaint p = tp(11.5f, false, muted);
            c.drawLine(12 * d, H - fh - 2 * d, W - 12 * d, H - fh - 2 * d, stroke(line, 1));
            c.drawText(ell(sb.toString(), p, W - 26 * d), 14 * d, H - 9 * d, p);
        }
    }

    private void compact(List<Data.Item> items, float top) {
        Data.Item cur = null, next = null;
        for (Data.Item it : items) {
            if (it.done) continue;
            if (cur == null && it.s <= nowM && nowM < it.e) cur = it;
            else if (next == null && it.s > nowM) next = it;
        }
        float y = top + 4 * d;
        if (cur == null && next == null) { centerMsg("Bugün başka plan yok.", top); return; }
        if (cur != null) y = card("ŞİMDİ", accent, cur, Data.fromMin(cur.e) + "'e kadar", y);
        if (next != null && y + 46 * d <= H) {
            int mins = next.s - nowM;
            String lbl = "SIRADAKİ · " + (mins < 60 ? mins + " dk sonra" : (mins / 60) + " sa " + (mins % 60 > 0 ? (mins % 60) + " dk " : "") + "sonra");
            card(lbl, mins <= 30 ? warn : muted, next, next.start + "–" + next.end, y);
        }
    }

    private float card(String label, int lc, Data.Item it, String time, float y) {
        float x = 14 * d, w = W - 28 * d;
        TextPaint lp = tp(10, true, lc);
        lp.setLetterSpacing(0.06f);
        c.drawText(ell(label, lp, w), x, y + 11 * d, lp);
        int cc = catColor(it.cat);
        c.drawCircle(x + 4 * d, y + 25 * d, 4 * d, fill(cc));
        TextPaint tp = tp(15, true, ink);
        c.drawText(ell(it.title, tp, w - 14 * d), x + 13 * d, y + 30 * d, tp);
        String pl = place(it.location, it.campus, true);
        TextPaint sp = tp(11.5f, false, muted);
        c.drawText(ell(time + (pl.isEmpty() ? "" : " · " + pl), sp, w), x, y + 45 * d, sp);
        return y + 54 * d;
    }

    // ---------- WEEK: the whole week
    private void week() {
        LocalDate ws = Data.weekStart(today), we = ws.plusDays(6);
        String range = ws.getMonthValue() == we.getMonthValue()
                ? ws.getDayOfMonth() + "–" + we.getDayOfMonth() + " " + Data.MONTHS[we.getMonthValue() - 1]
                : ws.getDayOfMonth() + " " + Data.MSHORT[ws.getMonthValue() - 1] + " – " + we.getDayOfMonth() + " " + Data.MSHORT[we.getMonthValue() - 1];
        float top = header("Hafta", range);
        float lw = 20 * d, x0 = 6 * d + lw, x1 = W - 6 * d, colW = (x1 - x0) / 7f;
        float dh = 28 * d;

        List<List<Data.Item>> days = new ArrayList<>();
        int lo = Integer.MAX_VALUE, hi = 0;
        for (int i = 0; i < 7; i++) {
            List<Data.Item> its = data.itemsForDate(ws.plusDays(i), now);
            days.add(its);
            for (Data.Item it : its) { lo = Math.min(lo, it.s); hi = Math.max(hi, it.e); }
        }
        if (lo == Integer.MAX_VALUE) { lo = 9 * 60; hi = 17 * 60; }
        int sh = lo / 60, eh = (hi + 59) / 60;
        while (eh - sh < 6) { if (eh < 24) eh++; else if (sh > 0) sh--; else break; }

        float gTop = top + dh, gBot = H - 8 * d;
        float hourPx = (gBot - gTop) / (eh - sh);

        // day headers + today tint
        TextPaint dn = tp(9, true, muted);
        dn.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < 7; i++) {
            LocalDate dd = ws.plusDays(i);
            float cx = x0 + colW * i + colW / 2f;
            boolean isToday = dd.equals(today);
            if (isToday) c.drawRect(x0 + colW * i, top, x0 + colW * (i + 1), gBot, fill(tint));
            c.drawText(Data.DSHORT[i], cx, top + 10 * d, dn);
            TextPaint num = tp(13, true, isToday ? accent : ink);
            num.setTextAlign(Paint.Align.CENTER);
            c.drawText(String.valueOf(dd.getDayOfMonth()), cx, top + 24 * d, num);
            int n = 0;
            for (Data.Todo t : data.untimedOn(dd)) if (!t.done) n++;
            if (n > 0) {
                TextPaint b = tp(8.5f, true, accent);
                c.drawText("+" + n, cx + 9 * d + (dd.getDayOfMonth() >= 10 ? 3 * d : 0), top + 23 * d, b);
            }
        }
        // grid
        Paint gl = stroke(line, 1);
        TextPaint hl = tp(8.5f, false, muted);
        hl.setTextAlign(Paint.Align.RIGHT);
        int every = hourPx < 14 * d ? 2 : 1;
        for (int h = sh; h <= eh; h++) {
            float y = gTop + (h - sh) * hourPx;
            c.drawLine(x0, y, x1, y, gl);
            if (h > sh && h < eh && (h - sh) % every == 0) c.drawText(String.format("%02d", h), x0 - 3 * d, y + 3 * d, hl);
        }
        for (int i = 0; i <= 7; i++) c.drawLine(x0 + colW * i, gTop, x0 + colW * i, gBot, gl);

        for (int i = 0; i < 7; i++) {
            List<Data.Item> its = days.get(i);
            Data.lanes(its);
            for (Data.Item it : its) {
                int s = Math.max(it.s, sh * 60), e = Math.min(it.e, eh * 60);
                if (e <= s) continue;
                float lwid = colW / it.lanes;
                float bx = x0 + colW * i + lwid * it.lane + 1 * d;
                float by = gTop + (s - sh * 60) / 60f * hourPx;
                float bh = Math.max((e - s) / 60f * hourPx - 1 * d, 6 * d);
                block(it, bx, by, lwid - 2 * d, bh, true);
            }
            if (ws.plusDays(i).equals(today) && nowM > sh * 60 && nowM < eh * 60) {
                float y = gTop + (nowM - sh * 60) / 60f * hourPx;
                c.drawLine(x0 + colW * i, y, x0 + colW * (i + 1), y, stroke(danger, 1.5f));
                c.drawCircle(x0 + colW * i, y, 2.5f * d, fill(danger));
            }
        }
    }

    // ---------- shared timeline + block drawing
    private void timeline(List<Data.Item> items, float x0, float x1, float gTop, float gBot, int sh, int eh, boolean labels, boolean small) {
        float hourPx = (gBot - gTop) / (eh - sh);
        Paint gl = stroke(line, 1);
        TextPaint hl = tp(9.5f, false, muted);
        hl.setTextAlign(Paint.Align.RIGHT);
        int every = hourPx < 16 * d ? 2 : 1;
        for (int h = sh; h <= eh; h++) {
            float y = gTop + (h - sh) * hourPx;
            c.drawLine(x0, y, x1, y, gl);
            if (labels && (h - sh) % every == 0 && h < 24) c.drawText(String.format("%02d:00", h), x0 - 4 * d, y + 3.5f * d, hl);
        }
        Data.lanes(items);
        float colW = x1 - x0;
        for (Data.Item it : items) {
            int s = Math.max(it.s, sh * 60), e = Math.min(it.e, eh * 60);
            if (e <= s) continue;
            float lwid = colW / it.lanes;
            float bx = x0 + lwid * it.lane + 1 * d;
            float by = gTop + (s - sh * 60) / 60f * hourPx + 0.5f * d;
            float bh = Math.max((e - s) / 60f * hourPx - 1.5f * d, 10 * d);
            block(it, bx, by, lwid - 2 * d, bh, small);
        }
        if (nowM > sh * 60 && nowM < eh * 60) {
            float y = gTop + (nowM - sh * 60) / 60f * hourPx;
            c.drawLine(x0, y, x1, y, stroke(danger, 1.5f));
            c.drawCircle(x0, y, 3 * d, fill(danger));
        }
    }

    private void block(Data.Item it, float x, float y, float w, float h, boolean small) {
        int cc = catColor(it.cat);
        int f, s;
        float sw = 1;
        switch (it.kind) {
            case "event": f = mix(cc, bg, dark ? 0.38f : 0.30f); s = cc; break;
            case "todo": f = mix(cc, bg, dark ? 0.28f : 0.20f); s = cc; sw = 1.5f; break;
            default: f = mix(cc, bg, dark ? 0.20f : 0.13f); s = mix(cc, bg, 0.55f); break;
        }
        boolean done = "todo".equals(it.kind) && it.done;
        if (done) { f = bg; s = mix(muted, bg, 0.5f); }
        if (it.missed && !done) s = warn;
        RectF r = new RectF(x, y, x + w, y + h);
        float rad = (small ? 3 : 5) * d;
        c.drawRoundRect(r, rad, rad, fill(f));
        c.drawRoundRect(r, rad, rad, stroke(s, sw));

        c.save();
        c.clipRect(r);
        float px = (small ? 2.5f : 5) * d;
        String pre = done ? "✓ " : (it.prep ? "↳ " : "");
        TextPaint tp = tp(small ? 9.5f : 12, true, done ? muted : ink);
        if (done) tp.setFlags(tp.getFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        float avail = w - 2 * px;
        if (small) {
            if (h >= 10 * d) c.drawText(pre + shortTitle(it.title), x + px, y + 10 * d, tp);
            if (h > 22 * d) {
                String pl = place(it.location, it.campus, false);
                if (!pl.isEmpty()) c.drawText(pl, x + px, y + 20 * d, tp(8.5f, false, muted));
            }
        } else {
            boolean one = h < 28 * d;
            if (one) {
                String pl = place(it.location, it.campus, false);
                String txt = pre + it.title + "  " + it.start + (pl.isEmpty() ? "" : " · " + pl);
                c.drawText(ell(txt, tp, avail), x + px, y + Math.min(h - 3 * d, 13 * d), tp);
            } else {
                c.drawText(ell(pre + it.title, tp, avail), x + px, y + 14 * d, tp);
                TextPaint sp = tp(10.5f, false, muted);
                String pl = place(it.location, it.campus, true);
                if (h > 42 * d) {
                    c.drawText(ell(it.start + "–" + it.end, sp, avail), x + px, y + 27 * d, sp);
                    if (!pl.isEmpty()) c.drawText(ell(pl, sp, avail), x + px, y + 40 * d, sp);
                } else {
                    c.drawText(ell(it.start + "–" + it.end + (pl.isEmpty() ? "" : " · " + pl), sp, avail), x + px, y + 27 * d, sp);
                }
            }
        }
        c.restore();
    }
}
