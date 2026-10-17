package dev.example.ebookviewer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Platform-only Java: a separate test-APK process cannot use the target APK's Kotlin runtime. */
public class DocumentFixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    private File fixture() { return new File(getContext().getCacheDir(), "provider-fixture.pdf"); }

    @Override public synchronized Bundle call(String method, String arg, Bundle extras) {
        Context owner = getContext();
        String target = "dev.example.ebookviewer.p0";
        try {
            if (Binder.getCallingUid() != owner.getPackageManager().getApplicationInfo(target, 0).uid) {
                throw new SecurityException();
            }
        } catch (android.content.pm.PackageManager.NameNotFoundException error) {
            throw new SecurityException();
        }
        Uri uri = Uri.parse("content://dev.example.ebookviewer.p0.test.documents/fixture");
        if (method.equals("grant")) {
            if (!fixture().exists()) {
                PdfDocument pdf = new PdfDocument();
                try {
                    for (int index = 0; index < 100; index++) {
                        PdfDocument.Page page = pdf.startPage(
                                new PdfDocument.PageInfo.Builder(600, 800, index + 1).create());
                        page.getCanvas().drawColor(android.graphics.Color.WHITE);
                        pdf.finishPage(page);
                    }
                    try (FileOutputStream output = new FileOutputStream(fixture())) {
                        pdf.writeTo(output);
                    }
                } catch (IOException error) {
                    throw new IllegalStateException("Fixture setup failed");
                } finally { pdf.close(); }
            }
            owner.grantUriPermission(target, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        } else if (method.equals("revoke") || method.equals("cleanup")) {
            owner.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (method.equals("cleanup")) fixture().delete();
        } else {
            throw new IllegalArgumentException("Unknown fixture operation");
        }
        Bundle result = new Bundle();
        result.putString("uri", uri.toString());
        return result;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(
                new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[] {"Synthetic provider PDF", fixture().length()});
        return cursor;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!uri.getLastPathSegment().equals("fixture") || !mode.equals("r")) {
            throw new FileNotFoundException();
        }
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createReliablePipe();
            new Thread(() -> {
                try (ParcelFileDescriptor.AutoCloseOutputStream output =
                             new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]);
                     FileInputStream input = new FileInputStream(fixture())) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                } catch (IOException cancelled) {
                    // Reader cancellation is expected. Resources close without logging document data.
                }
            }).start();
            return pipe[0];
        } catch (IOException error) { throw new FileNotFoundException(); }
    }

    @Override public String getType(Uri uri) { return "application/pdf"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
}
