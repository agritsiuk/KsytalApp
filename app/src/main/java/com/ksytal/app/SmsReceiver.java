package com.ksytal.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.widget.Toast;

public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            // Приложение больше не обрабатывает входящие SMS автоматически
            // Для обновления статуса используйте кнопку "Запросить статус" на экране Управление
        }
    }
}
