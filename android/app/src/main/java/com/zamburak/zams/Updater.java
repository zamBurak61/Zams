package com.zamburak.zams;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.widget.RemoteViews;

import java.util.List;

/** Renders every placed widget and refreshes data from the server. */
final class Updater {
    private Updater() {}

    static final String ACTION_REFRESH = "com.zamburak.zams.REFRESH";

    static int kindOf(Class<?> c) {
        if (c == DayWidget.class) return Render.DAY;
        if (c == WeekWidget.class) return Render.WEEK;
        return Render.LIST;
    }

    static final Class<?>[] PROVIDERS = {ListWidget.class, DayWidget.class, WeekWidget.class};

    static void renderAll(Context ctx) {
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        Data data = Data.parse(Store.data(ctx));
        for (Class<?> cls : PROVIDERS) {
            int[] ids = m.getAppWidgetIds(new ComponentName(ctx, cls));
            for (int id : ids) render(ctx, m, id, kindOf(cls), data);
        }
    }

    static void render(Context ctx, AppWidgetManager m, int id, int kind, Data data) {
        if (data == null) data = Data.parse(Store.data(ctx));
        Bundle o = m.getAppWidgetOptions(id);
        boolean land = ctx.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        int minW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        int maxW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0);
        int minH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
        int maxH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        float w = land ? (maxW > 0 ? maxW : minW) : (minW > 0 ? minW : maxW);
        float h = land ? (minH > 0 ? minH : maxH) : (maxH > 0 ? maxH : minH);
        if (w <= 0) w = kind == Render.WEEK ? 320 : 250;
        if (h <= 0) h = kind == Render.LIST ? 180 : 250;

        boolean dark = (ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        boolean logged = Store.loggedIn(ctx);
        String status = null;
        if (!logged) status = "Zam's Widget uygulamasını açıp Zam's hesabınla giriş yap.";
        else if (Store.data(ctx) == null) status = Store.error(ctx) != null ? Store.error(ctx) : "Yükleniyor…";

        Bitmap bmp = Render.draw(kind, data, status, ctx.getResources().getDisplayMetrics().density, w, h, dark, Store.fetchedAt(ctx), Store.error(ctx));

        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_image);
        rv.setImageViewBitmap(R.id.img, bmp);
        rv.setOnClickPendingIntent(R.id.img, PendingIntent.getActivity(ctx, 100 + kind, logged ? openApp(ctx) : new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        Intent r = new Intent(ctx, RefreshReceiver.class).setAction(ACTION_REFRESH);
        rv.setOnClickPendingIntent(R.id.refresh, PendingIntent.getBroadcast(ctx, 0, r, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        m.updateAppWidget(id, rv);
    }

    /** Opens the installed Zam's web app directly when possible, otherwise the browser. */
    static Intent openApp(Context ctx) {
        Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(Api.APP_URL));
        view.addCategory(Intent.CATEGORY_BROWSABLE);
        view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            List<ResolveInfo> list = ctx.getPackageManager().queryIntentActivities(view, PackageManager.MATCH_ALL);
            for (ResolveInfo ri : list) {
                String pkg = ri.activityInfo != null ? ri.activityInfo.packageName : "";
                if (pkg.startsWith("org.chromium.webapk")) { view.setPackage(pkg); break; }
            }
        } catch (Exception ignored) { }
        return view;
    }

    /** Blocking: fetch from the server, then redraw. */
    static void refresh(Context ctx) {
        if (Store.loggedIn(ctx)) {
            try {
                Api.fetch(ctx);
            } catch (Exception e) {
                Store.setError(ctx, Api.message(e));
            }
        }
        renderAll(ctx);
    }

    static void refreshAsync(Context ctx, BroadcastReceiver.PendingResult pr) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try { refresh(app); } finally { if (pr != null) pr.finish(); }
        }).start();
    }
}
