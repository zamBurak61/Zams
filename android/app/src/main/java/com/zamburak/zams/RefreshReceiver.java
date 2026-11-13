package com.zamburak.zams;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class RefreshReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Updater.ACTION_REFRESH.equals(intent.getAction())) return;
        Toast.makeText(ctx, "Zam's yenileniyor…", Toast.LENGTH_SHORT).show();
        Updater.refreshAsync(ctx, goAsync());
    }
}
