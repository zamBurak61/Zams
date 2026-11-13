package com.zamburak.zams;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Talks to the same Supabase project the Zam's web app uses. */
final class Api {
    private Api() {}

    static final String BASE = "https://qskjkfldmdgjrugfduoh.supabase.co";
    static final String KEY = "sb_publishable_TzaLuT26ZN9TLmE2naxnEw_kal5SnV7";
    static final String APP_URL = "https://zamburak61.github.io/Zams/";

    static final class HttpError extends Exception {
        final int code;
        final String body;
        HttpError(int code, String body) { super("HTTP " + code); this.code = code; this.body = body == null ? "" : body; }
    }

    static final class NotLoggedIn extends Exception {
        NotLoggedIn() { super("not logged in"); }
    }

    private static String read(InputStream is) throws IOException {
        if (is == null) return "";
        try (InputStream in = is; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    private static String http(String method, String path, String body, String bearer) throws IOException, HttpError {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setRequestProperty("apikey", KEY);
            c.setRequestProperty("Accept", "application/json");
            if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = c.getResponseCode();
            String res = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
            if (code >= 400) throw new HttpError(code, res);
            return res;
        } finally {
            c.disconnect();
        }
    }

    static void login(Context ctx, String email, String password) throws Exception {
        JSONObject b = new JSONObject().put("email", email).put("password", password);
        String r = http("POST", "/auth/v1/token?grant_type=password", b.toString(), null);
        Store.saveSession(ctx, new JSONObject(r), email);
    }

    private static String token(Context ctx) throws Exception {
        String access = Store.access(ctx);
        if (access != null && Store.expiresAt(ctx) > System.currentTimeMillis() + 60_000) return access;
        String rt = Store.refresh(ctx);
        if (rt == null) throw new NotLoggedIn();
        try {
            String r = http("POST", "/auth/v1/token?grant_type=refresh_token", new JSONObject().put("refresh_token", rt).toString(), null);
            Store.saveSession(ctx, new JSONObject(r), null);
        } catch (HttpError e) {
            if (e.code == 400 || e.code == 401 || e.code == 403) {
                Store.logout(ctx, true);
                throw new NotLoggedIn();
            }
            throw e;
        }
        return Store.access(ctx);
    }

    /** Downloads all of the user's planner rows and caches them. Blocking: call off the main thread. */
    static void fetch(Context ctx) throws Exception {
        String t = token(ctx);
        String r;
        try {
            r = http("GET", "/rest/v1/planner_docs?select=coll,id,data", null, t);
        } catch (HttpError e) {
            if (e.code == 401) {
                Store.logout(ctx, true);
                throw new NotLoggedIn();
            }
            throw e;
        }
        Store.saveData(ctx, r);
    }

    static String message(Exception e) {
        if (e instanceof NotLoggedIn) return "Oturum kapandı, uygulamadan tekrar giriş yap.";
        if (e instanceof HttpError) {
            HttpError h = (HttpError) e;
            String b = h.body.toLowerCase();
            if (b.contains("invalid login") || b.contains("invalid_credentials") || b.contains("invalid_grant")) return "E-posta ya da şifre yanlış.";
            if (b.contains("not confirmed")) return "E-posta adresin henüz onaylanmamış. Gelen onay bağlantısına tıkla.";
            return "Sunucu hatası (" + h.code + "). Biraz sonra tekrar dene.";
        }
        if (e instanceof IOException) return "Bağlanılamadı. İnternet bağlantını kontrol et.";
        return "Bir şeyler ters gitti: " + e.getMessage();
    }
}
