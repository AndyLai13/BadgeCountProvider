package com.hipad.provider.badgecount;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;


public class UnreadProvider extends ContentProvider {

    private final String TAG = getClass().getSimpleName();

    public static final String AUTHORITY = "com.hipad.provider.badgecount";
    public static final String TABLE_NAME = "unread_counts";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + TABLE_NAME);

    // Database column
    public static final String _ID = "_id";
    public static final String USER_ID = "user_id";
    public static final String PACKAGE_NAME = "package_name";
    public static final String CLASS_NAME = "class_name";
    public static final String COUNT = "count";
    public static final String HAS_INTENT = "has_intent";

    // Database specific constant declarations
    private SQLiteDatabase mDatabase;
    private static final String DATABASE_NAME = "unread_badge_count";
    private static final int DATABASE_VERSION = 1;
    private static final String CREATE_DB_TABLE =
            " CREATE TABLE " + TABLE_NAME + " (" +
                    _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    USER_ID + " INTEGER, " +
                    PACKAGE_NAME + " TEXT, " +
                    CLASS_NAME + " TEXT, " +
                    COUNT + " INTEGER, " +
                    HAS_INTENT + " INTEGER DEFAULT 0 " +
                    ");";

    /**
     * Helper class that actually creates and manages
     * the provider's underlying data repository.
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context){
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_DB_TABLE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
            onCreate(db);
        }
    }

    @Override
    public boolean onCreate() {
        Context context = getContext();
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        /**
         * Create a writeable database which will trigger its
         * creation if it doesn't already exist.
         */
        mDatabase = dbHelper.getWritableDatabase();
        return (mDatabase != null);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count = mDatabase.delete(TABLE_NAME, selection, selectionArgs);
        if (count > 0)
            getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        long rowID = mDatabase.insert(TABLE_NAME, null, values);
        if (rowID > 0) {
            Uri _uri = ContentUris.withAppendedId(CONTENT_URI, rowID);
            getContext().getContentResolver().notifyChange(_uri, null);
            return _uri;
        } else {
            throw new SQLException("Failed to add a record into " + uri);
        }
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        int numInserted = 0;

        mDatabase.beginTransaction();
        try {
            for (ContentValues cv : values) {
                long newID = mDatabase.insertOrThrow(TABLE_NAME, null, cv);
                if (newID <= 0) {
                    throw new SQLException("Failed to insert row into " + uri);
                }
            }
            mDatabase.setTransactionSuccessful();
            getContext().getContentResolver().notifyChange(uri, null);
            numInserted = values.length;
        } finally {
            mDatabase.endTransaction();
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return numInserted;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(TABLE_NAME);

        if (sortOrder == null || sortOrder == "") {
            // By default sort on package names
            sortOrder = PACKAGE_NAME;
        }

        Cursor c = qb.query(mDatabase, projection, selection, selectionArgs,
                null, null, sortOrder);
        // register to watch a content URI for changes
        c.setNotificationUri(getContext().getContentResolver(), uri);

        return c;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count = mDatabase.update(TABLE_NAME, values, selection, selectionArgs);
        if (count > 0 )
            getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
}