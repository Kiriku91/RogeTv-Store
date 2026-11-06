package com.roge.store;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

public class ApkFileProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = new File(getContext().getExternalCacheDir(), uri.getLastPathSegment());
        if (!file.exists()) {
            file = new File(getContext().getCacheDir(), uri.getLastPathSegment());
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String s, String[] as1, String s1) {
        File file = new File(getContext().getExternalCacheDir(), uri.getLastPathSegment());
        if (!file.exists()) {
            file = new File(getContext().getCacheDir(), uri.getLastPathSegment());
        }
        if (projection == null) {
            projection = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        }
        MatrixCursor cursor = new MatrixCursor(projection, 1);
        Object[] values = new Object[projection.length];
        for (int i = 0; i < projection.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(projection[i])) {
                values[i] = file.getName();
            } else if (OpenableColumns.SIZE.equals(projection[i])) {
                values[i] = file.length();
            }
        }
        cursor.addRow(values);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentValues) { return null; }

    @Override
    public int delete(Uri uri, String s, String[] strings) { return 0; }

    @Override
    public int update(Uri uri, ContentValues contentValues, String s, String[] strings) { return 0; }
}
