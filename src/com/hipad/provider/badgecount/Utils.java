package com.hipad.provider.badgecount;

import java.util.List;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

public class Utils {

    public static final String PACKAGENAME_GMAIL = "com.google.android.gm";

    public static final int UNDEFINE = -1;

    public static void updateUnreadCount(Context context,
            int count, String packageName, String className, int userId) {
         updateUnreadCount(context, new ResolvedPackageInfo(userId, count, packageName, className));
     }
    /*
     * UserIds are used as follows:
     * -1 : USER_ALL
     *  0 : USER_SERIAL_SYSTEM
     * 11 : SecureBox
     * 10 : Second OS
     */
    public static final int USER_ALL = -1;
    public static final int USER_SERIAL_SYSTEM = 0;

    public static void updateUnreadCount(Context context, ResolvedPackageInfo info) {
        Intent intent = new Intent("com.android.launcher.action.UNREAD_CHANGED");
        intent.putExtra("user_id", info.userId);
        intent.putExtra("unread_number", info.count);
        intent.putExtra("component_name", new ComponentName(info.packageName, info.className));
        context.sendBroadcast(intent);
    }

    public static int getUserSerialSystem() {
        return getUserIdFromUserHandleAsString("0");
    }

    public static int getUserIdFromUserHandleAsString(String userAsString) {
        if (!userAsString.isEmpty()) {
            if (userAsString.equals("0")) {
                return USER_SERIAL_SYSTEM;
            } else if (userAsString.contains("UserHandle{")) {
                userAsString = userAsString.substring(
                        userAsString.indexOf("{") + 1,
                        userAsString.indexOf("}"));
                return Integer.parseInt(userAsString);
            }
        }
        return USER_SERIAL_SYSTEM;
    }

    public static String getLauncherActivity(PackageManager pm, String packageName) {
        final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        mainIntent.setPackage(packageName);
        List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0 /* flags */);
        return (apps != null && apps.size() > 0) ? apps.get(0).activityInfo.name : "";
    }
}
