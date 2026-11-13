package com.zamburak.zams;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/** Small persistent store: login session, cached planner data, last error. */
final class Store {
    private Store() {}

    private static SharedPreferences p(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences("zams", Context.MODE_PRIVATE);
    }

    static synchronized void saveSession(Context ctx, JSONObject r, String email) {
        long exp = System.currentTimeMillis() + r.optLong("expires_in", 3600) * 1000L;
        SharedPreferences.Editor e = p(ctx).edit()
                .putString("access", r.optString("access_token", null))
                .putString("refresh", r.optString("refresh_token", null))
                .putLong("exp", exp)
                .remove("err");
        if (email != null) e.putString("email", email);
        e.apply();
    }

    static synchronized String access(Context ctx) { return p(ctx).getString("access", null); }
    static synchronized String refresh(Context ctx) { return p(ctx).getString("refresh", null); }
    static synchronized long expiresAt(Context ctx) { return p(ctx).getLong("exp", 0); }
    static synchronized String email(Context ctx) { return p(ctx).getString("email", ""); }
    static synchronized boolean loggedIn(Context ctx) { return p(ctx).getString("refresh", null) != null; }

    static synchronized void logout(Context ctx, boolean keepData) {
        SharedPreferences.Editor e = p(ctx).edit().remove("access").remove("refresh").remove("exp");
        if (!keepData) e.remove("data").remove("fetchedAt").remove("err");
        e.apply();
    }

    static synchronized void saveData(Context ctx, String json) {
        p(ctx).edit().putString("data", json).putLong("fetchedAt", System.currentTimeMillis()).remove("err").apply();
    }

    static synchronized String data(Context ctx) { return p(ctx).getString("data", null); }
    static synchronized long fetchedAt(Context ctx) { return p(ctx).getLong("fetchedAt", 0); }

    static synchronized void setError(Context ctx, String msg) { p(ctx).edit().putString("err", msg).apply(); }
    static synchronized String error(Context ctx) { return p(ctx).getString("err", null); }
}
