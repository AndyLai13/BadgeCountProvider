package com.hipad.provider.badgecount;

import android.content.Context;
import android.content.Intent;
import android.service.notification.StatusBarNotification;

public class ResolvedPackageInfo {
    public int userId;
    public int count; // unread count
    public String type;
    public String packageName;
    public String className;
    public boolean hasIntent;

    public ResolvedPackageInfo(String type, Intent infoIntent,
            int userId, boolean hasIntent) {
        // userId is kept in sbn once notification fired.
        this.userId = userId;
        this.count = infoIntent.getIntExtra("badge_count", -1);
        this.type = type;
        this.packageName = infoIntent.getStringExtra("badge_count_package_name");
        this.className = infoIntent.getStringExtra("badge_count_class_name");
        this.hasIntent = hasIntent;
    }

    public ResolvedPackageInfo(String type, StatusBarNotification sbn, int count,
            Context context, boolean hasIntent) {
        this.userId = sbn.getUserId();
        // it is a regular notification. We can set it to 1
        if (count == Utils.UNDEFINE) {
            this.count = sbn.getNotification().number == 0 ? 1 : sbn.getNotification().number;
        } else {
            this.count = count;
        }
        this.type = type;
        this.packageName = sbn.getPackageName();
        this.className = Utils.getLauncherActivity(
                context.getPackageManager(), sbn.getPackageName());
        this.hasIntent = hasIntent;
    }

    public ResolvedPackageInfo(int userId, int count, String packageName, String className) {
        this.userId = userId;
        this.count = count;
        this.packageName = packageName;
        this.className = className;
    }
}