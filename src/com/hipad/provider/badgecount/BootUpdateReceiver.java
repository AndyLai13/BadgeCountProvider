package com.hipad.provider.badgecount;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.provider.Settings;
import android.util.Log;

public class BootUpdateReceiver extends BroadcastReceiver {

    private final String TAG = getClass().getSimpleName();
    // Detail of ENABLED_NOTIFICATION_LISTENERS can be found as hide in Settings.Secure
    private static final String ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners";
    private static final String COMPONENT_NAME =
            "com.hipad.provider.badgecount/" +
            "com.hipad.provider.badgecount.BadgeCountListenerService";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onreceive : " + intent);
        getSpecialAccess(context);
        updateUnreadCount(context);
    }

    private void getSpecialAccess(Context context) {
        String enabledList = Settings.Secure.getString(
                context.getContentResolver(), ENABLED_NOTIFICATION_LISTENERS);
        if(enabledList == null || enabledList.isEmpty()) {
            enabledList = COMPONENT_NAME;
        } else {
            if(enabledList.contains(COMPONENT_NAME)) {
                return;
            } else {
                // Notification access list is stored as String and each one is split by colon ":",
                // hence we add colon before allowed component.
                enabledList = enabledList + ":" + COMPONENT_NAME;
            }
        }
        Settings.Secure.putString(context.getContentResolver(), ENABLED_NOTIFICATION_LISTENERS, enabledList);
        Log.d(TAG, "getSpecialAccess: enabledList = " + enabledList);
    }

    private void updateUnreadCount(Context context) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Cursor c = context.getContentResolver().query(UnreadProvider.CONTENT_URI, null,
                        null, null, null);
                Log.d(TAG, "Row count = " + c.getCount());
                try {
                    if (c.getCount() > 0) {
                        c.moveToPosition(-1);
                        while (c.moveToNext()) {
                            int count = c.getInt(c.getColumnIndex(UnreadProvider.COUNT));
                            int userId = c.getInt(c.getColumnIndex(UnreadProvider.USER_ID));
                            //if (userId != Utils.USER_SERIAL_SYSTEM) return;
                            String packageName = c.getString(c.getColumnIndex(UnreadProvider.PACKAGE_NAME));
                            String className = c.getString(c.getColumnIndex(UnreadProvider.CLASS_NAME));
                            Log.d(TAG, "update at first create :" +
                                    "count = "      + count       + " " +
                                    "packagName = " + packageName + " " +
                                    "className = "  + className   + " " +
                                    "userId = "     + userId      + " ");
                            Utils.updateUnreadCount(context, count, packageName, className, userId);
                        }
                    }
                } finally {
                    c.close();
                }
            }
        }).start();
    }
}
