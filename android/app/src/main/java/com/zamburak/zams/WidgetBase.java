package com.zamburak.zams;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

abstract class WidgetBase extends AppWidgetProvider {
    abstract int kind();

    @Override
    public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
        Data data = Data.parse(Store.data(ctx));
        for (int id : ids) Updater.render(ctx, m, id, kind(), data);
        if (Store.loggedIn(ctx) && System.currentTimeMillis() - Store.fetchedAt(ctx) > 5 * 60_000L) {
            Updater.refreshAsync(ctx, goAsync());
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager m, int id, Bundle newOptions) {
        Updater.render(ctx, m, id, kind(), null);
    }
}
