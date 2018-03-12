package com.hipad.provider.badgecount;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class BadgeCountListenerService extends NotificationListenerService {

    private final String TAG = getClass().getSimpleName();
    private static final boolean DEBUG = true;
    private static final String[] PROJECTION = {
            UnreadProvider.USER_ID,
            UnreadProvider.PACKAGE_NAME,
            UnreadProvider.CLASS_NAME,
            UnreadProvider.COUNT,
            UnreadProvider.HAS_INTENT};
    private static final int COLUMN_USER_ID      = 0;
    private static final int COLUMN_PACKAGE_NAME = 1;
    private static final int COLUMN_CLASS_NAME   = 2;
    private static final int COLUMN_COUNT        = 3;
    private static final int COLUMN_HAS_INTENT   = 4;
    // if app is able to send intent to notify the unread count,
    // we should resolve intent to get data rather than by listening notification.
    private static final int HAS_INTENT = 1;
    private static final int HAS_NO_INTENT = 0;

    private Intent mBadgeIntent;
    private boolean mIsAppEnableSentIntent = false;

    @Override
    public void onCreate() {
        super.onCreate();
        LogTag("onCreate");
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        LogTag("onListenerConnected");
        IntentFilter filter = new IntentFilter("android.intent.action.BADGE_COUNT_UPDATE");
        registerReceiver(mUnreadReceiver, filter);
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        LogTag("onListenerDisconnected");
        unregisterReceiver(mUnreadReceiver);
    }

    private final BroadcastReceiver mUnreadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if("android.intent.action.BADGE_COUNT_UPDATE".equals(action)) {
                LogTag("mUnreadReceiver: receive intent "
                        + "\"android.intent.action.BADGE_COUNT_UPDATE action intent\"");
                mBadgeIntent = intent;
                mIsAppEnableSentIntent = true;
                retrieveData(mBadgeIntent, Utils.getUserSerialSystem(), true);
            }
        }
    };

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();
        LogTag("onNotificationPosted: userHandle = " + sbn.getUser()
                + ", packageName = " + packageName);
        Set<String> excludedPackages = new HashSet<>(
                Arrays.asList(getResources().getStringArray(R.array.excluded_package)));
        for (String excludePackage: excludedPackages) {
            if (packageName.equals(excludePackage)) return;
        }
        // Special case for Gmail
        if (Utils.PACKAGENAME_GMAIL.equals(packageName)&&
                (sbn.getNotification().defaults & (
                        Notification.DEFAULT_SOUND   |
                        Notification.DEFAULT_VIBRATE |
                        Notification.DEFAULT_LIGHTS)) == 0) {
            return;
        }
        if (!sbn.isOngoing() || sbn.getUserId() != Utils.USER_ALL) {
            LogTag("enable to send Intent: " + mIsAppEnableSentIntent);
            if (!mIsAppEnableSentIntent &&
                    !getAppEnableSentIntentFromDB(packageName, sbn.getUserId())) {
                retrieveData(sbn, Utils.UNDEFINE, false);
            }
            mIsAppEnableSentIntent = false;
        }
    }

    private boolean getAppEnableSentIntentFromDB(String packageName, int userId) {
        boolean hasIntent = false;
        Cursor c = queryRowUsingPkgNameAndUserId(packageName, userId);
        if (c != null && c.getCount() > 0 && c.moveToFirst()) {
            hasIntent = c.getInt(COLUMN_HAS_INTENT) == HAS_INTENT;
        }
        return hasIntent;
    }

    @Override
    public void onNotificationRemoved(final StatusBarNotification sbn) {
        LogTag("deleting " + sbn.getPackageName());
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!sbn.isOngoing() &&
                        !getAppEnableSentIntentFromDB(sbn.getPackageName(), sbn.getUserId())) {
                    retrieveData(sbn, 0, false);
                }
            }
        }).start();
    }

    private Cursor queryRowUsingPkgNameAndUserId(String packageName, int userId) {
        String selections = UnreadProvider.PACKAGE_NAME + "= '" + packageName +
                "') AND (" + UnreadProvider.USER_ID + "=" + userId;
        boolean debug = false;
        if (debug) Log.d(TAG, selections);
        return getContentResolver().query(UnreadProvider.CONTENT_URI, PROJECTION, selections, null , null);
    }

    private void retrieveData(Intent badgeIntent, int userId, boolean hasIntent) {
        processData(new ResolvedPackageInfo("intent", badgeIntent, userId, hasIntent));
    }

    private void retrieveData(StatusBarNotification sbn, int count, boolean hasIntent) {
        processData(new ResolvedPackageInfo("sbn", sbn, count, this, hasIntent));
    }

    private void processData(ResolvedPackageInfo info) {
        Utils.updateUnreadCount(this, info);
        // type is saved to database to detect type on next time using this package/class
        // but do nothing for updating count on app icon.
        saveDataToDB(info);
    }

    private synchronized void saveDataToDB(ResolvedPackageInfo info) {
        Log.d(TAG, "save data to DB: " +
                " userId = "       + info.userId +
                ", type = "        + info.type +
                ", count = "       + info.count  +
                ", packageName = " + info.packageName +
                ", className = "   + info.className);
        new Thread(new Runnable() {
            @Override
            public void run() {
                Cursor c = queryRowUsingPkgNameAndUserId(info.packageName, info.userId);
                if (c != null && c.getCount() > 0 && c.moveToFirst()) {
                    updateCountUsingPkgNameAndUserId(info, -1);
                }  else {
                    insertNewData(info);
                }
                c.close();
            }
        }).start();
    }

    private void updateCountUsingPkgNameAndUserId(ResolvedPackageInfo info, int count) {
        if(count == -1) count = info.count;
        LogTag("update count: count = " + count);
        ContentValues values = new ContentValues();
        values.put(UnreadProvider.COUNT, count);
        String selections = UnreadProvider.PACKAGE_NAME + "= '" + info.packageName +
                "' AND " + UnreadProvider.USER_ID + "=" + info.userId;
        getContentResolver().update(UnreadProvider.CONTENT_URI, values, selections, null);
    }

    private void insertNewData(ResolvedPackageInfo info) {
        ContentValues values = new ContentValues();
        values.put(UnreadProvider.USER_ID,      info.userId);
        values.put(UnreadProvider.COUNT,        info.count);
        values.put(UnreadProvider.PACKAGE_NAME, info.packageName);
        values.put(UnreadProvider.CLASS_NAME,   info.className);
        values.put(UnreadProvider.HAS_INTENT,   info.hasIntent);
        Uri uri = getContentResolver().insert(UnreadProvider.CONTENT_URI, values);
        LogTag("insert new data: uri = " + uri.toString());
    }

    private void LogTag(String info) {
        if (DEBUG)
            Log.d(TAG, info);
    }
}