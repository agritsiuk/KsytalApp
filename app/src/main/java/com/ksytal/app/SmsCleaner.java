package com.ksytal.app;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.concurrent.TimeUnit;

public class SmsCleaner {
    private static boolean isCleaning = false;
    private static Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final String TAG = "KsytalCleaner";

    public interface CleanupCallback {
        void onCleanupComplete(int deletedCount);
        void onCleanupError(String error);
    }

    public static void cleanOldMessages(Context context, String phoneNumber, int days, CleanupCallback callback) {
        if (isCleaning) {
            if (callback != null) callback.onCleanupError("Очистка уже выполняется");
            return;
        }
        isCleaning = true;
        Log.d(TAG, "Начало очистки: номер=" + phoneNumber + ", дней=" + days);

        new Thread(() -> {
            try {
                int deletedCount = performCleanup(context, phoneNumber, days);
                Log.d(TAG, "Очистка завершена, удалено: " + deletedCount);
                mainHandler.post(() -> {
                    if (callback != null) callback.onCleanupComplete(deletedCount);
                    isCleaning = false;
                });
            } catch (Exception e) {
                Log.e(TAG, "Ошибка очистки", e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onCleanupError(e.getMessage());
                    isCleaning = false;
                });
            }
        }).start();
    }

    private static int performCleanup(Context context, String phoneNumber, int days) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        Uri smsUri = Uri.parse("content://sms/");
        String selection;
        String[] selectionArgs;

        if (days == 0) {
            selection = "(address LIKE ? OR address LIKE ?)";
            selectionArgs = new String[]{"%" + phoneNumber + "%", "%" + phoneNumber + "%"};
            Log.d(TAG, "Режим: удалить ВСЕ сообщения для номера " + phoneNumber);
        } else {
            long cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);
            selection = "(address LIKE ? OR address LIKE ?) AND date < ?";
            selectionArgs = new String[]{"%" + phoneNumber + "%", "%" + phoneNumber + "%", String.valueOf(cutoffTime)};
            Log.d(TAG, "Режим: удалить сообщения старше " + days + " дней (cutoff=" + cutoffTime + ")");
        }

        Cursor cursor = null;
        int deletedCount = 0;
        try {
            cursor = resolver.query(smsUri, new String[]{"_id", "address", "date"}, selection, selectionArgs, null);
            if (cursor == null) {
                Log.e(TAG, "Курсор вернул null, нет доступа к SMS");
                throw new Exception("Нет доступа к SMS. Возможно, не хватает разрешения READ_SMS.");
            }
            int count = cursor.getCount();
            Log.d(TAG, "Найдено сообщений для удаления: " + count);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
                String address = cursor.getString(cursor.getColumnIndexOrThrow("address"));
                long date = cursor.getLong(cursor.getColumnIndexOrThrow("date"));
                Log.d(TAG, "Попытка удалить ID=" + id + ", адрес=" + address + ", дата=" + date);
                Uri deleteUri = Uri.parse("content://sms/" + id);
                int deleted = resolver.delete(deleteUri, null, null);
                if (deleted > 0) {
                    deletedCount++;
                    Log.d(TAG, "Удалено ID=" + id);
                } else {
                    Log.w(TAG, "Не удалось удалить ID=" + id);
                }
            }
        } catch (SecurityException e) {
            throw new Exception("Не хватает разрешения WRITE_SMS или READ_SMS. Проверьте настройки приложения.", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return deletedCount;
    }
}
