package com.zamburak.zams;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The planner model, ported from the web app's itemsForDate / todoState logic. */
final class Data {

    static final class Cat { String id, name, color; }
    static final class Tpl { String id, title, start, end, location, campus, cat; int day; }
    static final class Ov { boolean cancelled; Integer day; String start, end, location, campus; }
    static final class Ev { String id, title, date, start, end, location, campus, cat; }
    static final class Todo {
        String id, title, cat, due, schedDate, schedStart, prepColl, prepId;
        boolean done; long doneAt, created; int dur;
    }
    static final class Item {
        String kind, id, title, start, end, location, campus, cat;
        boolean done, missed, prep;
        int s, e, lane, lanes = 1;
    }

    final List<Cat> cats = new ArrayList<>();
    final List<Tpl> tpls = new ArrayList<>();
    final Map<String, Ov> ovs = new HashMap<>();
    final List<Ev> events = new ArrayList<>();
    final List<Todo> todos = new ArrayList<>();

    static final String[] DSHORT = {"Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz"};
    static final String[] MSHORT = {"Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara"};
    static final String[] MONTHS = {"Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"};

    private static String str(JSONObject o, String k) {
        return (o.has(k) && !o.isNull(k)) ? o.optString(k, "") : null;
    }
    private static String s0(JSONObject o, String k) { String v = str(o, k); return v == null ? "" : v; }

    static Data parse(String json) {
        Data d = new Data();
        if (json == null) return d;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject row = arr.getJSONObject(i);
                String coll = row.optString("coll"), id = row.optString("id");
                JSONObject x = row.optJSONObject("data");
                if (x == null) continue;
                switch (coll) {
                    case "meta": {
                        JSONArray cs = x.optJSONArray("categories");
                        if (cs != null) for (int j = 0; j < cs.length(); j++) {
                            JSONObject c = cs.optJSONObject(j);
                            if (c == null) continue;
                            Cat cat = new Cat(); cat.id = s0(c, "id"); cat.name = s0(c, "name"); cat.color = s0(c, "color");
                            d.cats.add(cat);
                        }
                        break;
                    }
                    case "templates": {
                        Tpl t = new Tpl(); t.id = id; t.title = s0(x, "title"); t.day = x.optInt("day", 0);
                        t.start = s0(x, "start"); t.end = s0(x, "end"); t.location = s0(x, "location");
                        t.campus = s0(x, "campus"); t.cat = s0(x, "cat");
                        d.tpls.add(t);
                        break;
                    }
                    case "overrides": {
                        Ov o = new Ov(); o.cancelled = x.optBoolean("cancelled", false);
                        o.day = (x.has("day") && !x.isNull("day")) ? x.optInt("day") : null;
                        o.start = str(x, "start"); o.end = str(x, "end"); o.location = str(x, "location"); o.campus = str(x, "campus");
                        d.ovs.put(id, o);
                        break;
                    }
                    case "events": {
                        Ev e = new Ev(); e.id = id; e.title = s0(x, "title"); e.date = s0(x, "date");
                        e.start = s0(x, "start"); e.end = s0(x, "end"); e.location = s0(x, "location");
                        e.campus = s0(x, "campus"); e.cat = s0(x, "cat");
                        d.events.add(e);
                        break;
                    }
                    case "todos": {
                        Todo t = new Todo(); t.id = id; t.title = s0(x, "title"); t.cat = s0(x, "cat");
                        t.done = x.optBoolean("done", false); t.doneAt = x.optLong("doneAt", 0); t.created = x.optLong("created", 0);
                        t.dur = x.optInt("dur", 0); t.due = str(x, "due");
                        JSONObject sc = x.optJSONObject("sched");
                        if (sc != null) { t.schedDate = s0(sc, "date"); t.schedStart = s0(sc, "start"); if (t.schedDate.isEmpty() || t.schedStart.isEmpty()) { t.schedDate = null; t.schedStart = null; } }
                        JSONObject pf = x.optJSONObject("prepFor");
                        if (pf != null) { t.prepColl = s0(pf, "coll"); t.prepId = s0(pf, "id"); }
                        if (t.due != null && t.due.isEmpty()) t.due = null;
                        d.todos.add(t);
                        break;
                    }
                    default: break;
                }
            }
        } catch (Exception ignored) { }
        return d;
    }

    // ---------- time helpers
    static int toMin(String t) {
        if (t == null || t.isEmpty()) return 0;
        String[] p = t.split(":");
        try { return Integer.parseInt(p[0]) * 60 + (p.length > 1 ? Integer.parseInt(p[1]) : 0); } catch (Exception e) { return 0; }
    }
    static String fromMin(int m) { m = Math.max(0, Math.min(m, 24 * 60 - 1)); return String.format("%02d:%02d", m / 60, m % 60); }
    static LocalDate weekStart(LocalDate d) { return d.minusDays(d.getDayOfWeek().getValue() - 1); }
    static String dayLabel(LocalDate d, LocalDate today) {
        if (d.equals(today)) return "Bugün";
        if (d.equals(today.plusDays(1))) return "Yarın";
        if (d.equals(today.minusDays(1))) return "Dün";
        return DSHORT[d.getDayOfWeek().getValue() - 1] + " " + d.getDayOfMonth() + " " + MSHORT[d.getMonthValue() - 1];
    }
    static String dayLabel(String ds, LocalDate today) {
        try { return dayLabel(LocalDate.parse(ds), today); } catch (Exception e) { return ds; }
    }
    static String campusSym(String c) { return "G".equals(c) ? "↓" : "K".equals(c) ? "↑" : "H".equals(c) ? "–" : ""; }
    static String campusName(String c) { return "G".equals(c) ? "Güney" : "K".equals(c) ? "Kuzey" : "H".equals(c) ? "Hisar" : ""; }

    String colorOf(String catId) {
        for (Cat c : cats) if (c.id.equals(catId)) return c.color;
        return cats.isEmpty() ? "c8" : cats.get(0).color;
    }

    Todo todo(String id) { for (Todo t : todos) if (t.id.equals(id)) return t; return null; }

    String mainTitle(Todo t) {
        if (t.prepId == null) return null;
        if ("events".equals(t.prepColl)) { for (Ev e : events) if (e.id.equals(t.prepId)) return e.title; return null; }
        Todo m = todo(t.prepId);
        return m == null ? null : m.title;
    }

    static String state(Todo t, LocalDateTime now) {
        if (t.done) return "done";
        if (t.schedDate != null) {
            try {
                LocalDateTime end = LocalDate.parse(t.schedDate).atStartOfDay().plusMinutes(toMin(t.schedStart) + (t.dur > 0 ? t.dur : 60));
                return end.isBefore(now) ? "missed" : "planned";
            } catch (Exception e) { return "planned"; }
        }
        return "open";
    }

    private static String pick(String o, String t) { return o != null ? o : t; }

    /** Everything with a time on this date: fixed classes (minus cancelled), events, scheduled todos. */
    List<Item> itemsForDate(LocalDate d, LocalDateTime now) {
        List<Item> out = new ArrayList<>();
        String wk = weekStart(d).toString();
        int di = d.getDayOfWeek().getValue() - 1;
        String ds = d.toString();
        for (Tpl t : tpls) {
            Ov o = ovs.get(wk + "_" + t.id);
            if (o != null && o.cancelled) continue;
            int day = (o != null && o.day != null) ? o.day : t.day;
            if (day != di) continue;
            Item it = new Item();
            it.kind = "fixed"; it.id = t.id; it.title = t.title; it.cat = t.cat;
            it.start = o != null ? pick(o.start, t.start) : t.start;
            it.end = o != null ? pick(o.end, t.end) : t.end;
            it.location = o != null ? pick(o.location, t.location) : t.location;
            it.campus = o != null ? pick(o.campus, t.campus) : t.campus;
            out.add(it);
        }
        for (Ev e : events) {
            if (!ds.equals(e.date)) continue;
            Item it = new Item();
            it.kind = "event"; it.id = e.id; it.title = e.title; it.cat = e.cat;
            it.start = e.start; it.end = e.end; it.location = e.location; it.campus = e.campus;
            out.add(it);
        }
        for (Todo t : todos) {
            if (t.schedDate == null || !ds.equals(t.schedDate)) continue;
            Item it = new Item();
            it.kind = "todo"; it.id = t.id; it.title = t.title; it.cat = t.cat;
            it.start = t.schedStart;
            it.end = fromMin(Math.min(toMin(t.schedStart) + (t.dur > 0 ? t.dur : 60), 24 * 60 - 1));
            it.location = ""; it.campus = "";
            it.done = t.done; it.missed = "missed".equals(state(t, now)); it.prep = t.prepId != null;
            out.add(it);
        }
        for (Item it : out) { it.s = toMin(it.start); it.e = Math.max(toMin(it.end), it.s + 15); }
        Collections.sort(out, (a, b) -> a.s != b.s ? Integer.compare(a.s, b.s) : Integer.compare(a.e, b.e));
        return out;
    }

    /** Todos with only a date (no time) on this day. */
    List<Todo> untimedOn(LocalDate d) {
        String ds = d.toString();
        List<Todo> out = new ArrayList<>();
        for (Todo t : todos) if (t.schedDate == null && ds.equals(t.due)) out.add(t);
        Collections.sort(out, (a, b) -> a.done != b.done ? (a.done ? 1 : -1) : Long.compare(a.created, b.created));
        return out;
    }

    /** Same selection as the web app's "Bugünün işleri". */
    List<Todo> todayTodos(LocalDate today, LocalDateTime now) {
        String td = today.toString();
        List<Todo> out = new ArrayList<>();
        for (Todo t : todos) {
            String st = state(t, now);
            boolean keep;
            if (st.equals("done")) keep = t.doneAt > 0 && java.time.Instant.ofEpochMilli(t.doneAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().equals(today);
            else if (st.equals("missed")) keep = true;
            else if (t.schedDate != null && td.equals(t.schedDate)) keep = true;
            else keep = t.due != null && t.due.compareTo(td) <= 0;
            if (keep) out.add(t);
        }
        Collections.sort(out, (a, b) -> {
            int ra = rank(state(a, now)), rb = rank(state(b, now));
            if (ra != rb) return Integer.compare(ra, rb);
            String ka = a.schedDate != null ? a.schedDate + a.schedStart : (a.due != null ? a.due : "9999");
            String kb = b.schedDate != null ? b.schedDate + b.schedStart : (b.due != null ? b.due : "9999");
            int c = ka.compareTo(kb);
            return c != 0 ? c : Long.compare(a.created, b.created);
        });
        return out;
    }

    private static int rank(String s) {
        switch (s) { case "missed": return 0; case "open": return 1; case "planned": return 2; default: return 3; }
    }

    /** Side-by-side columns for overlapping items (same idea as the web calendar). */
    static void lanes(List<Item> items) {
        List<Item> sorted = new ArrayList<>(items);
        Collections.sort(sorted, (a, b) -> a.s != b.s ? Integer.compare(a.s, b.s) : Integer.compare(b.e, a.e));
        List<Item> cluster = new ArrayList<>();
        int end = -1;
        for (Item it : sorted) {
            if (!cluster.isEmpty() && it.s >= end) { flush(cluster); end = -1; }
            cluster.add(it);
            end = Math.max(end, it.e);
        }
        if (!cluster.isEmpty()) flush(cluster);
    }

    private static void flush(List<Item> cl) {
        List<Integer> ends = new ArrayList<>();
        for (Item it : cl) {
            int lane = -1;
            for (int i = 0; i < ends.size(); i++) if (ends.get(i) <= it.s) { lane = i; break; }
            if (lane < 0) { lane = ends.size(); ends.add(it.e); } else ends.set(lane, it.e);
            it.lane = lane;
        }
        for (Item it : cl) it.lanes = ends.size();
        cl.clear();
    }
}
