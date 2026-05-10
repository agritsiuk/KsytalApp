package com.ksytal.app;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSIONS_REQUEST_CODE = 100;
    private static final String[] REQUIRED_PERMISSIONS = {
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
        "android.permission.WRITE_SMS",
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_PHONE_STATE
    };

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private SmsReceiver smsReceiver;
    private final boolean smsReadable = Build.VERSION.SDK_INT < 34; // Android 14 = API 34

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);

        setupTabs();
        checkPermissionsAndInit();
    }

    private void setupTabs() {
        List<String> tabTitles = new ArrayList<>();
        List<Class<? extends androidx.fragment.app.Fragment>> fragments = new ArrayList<>();

        if (smsReadable) {
            tabTitles.add("Статус");
            fragments.add(StatusFragment.class);
        }
        tabTitles.add("Управление");
        fragments.add(ControlFragment.class);
        tabTitles.add("Настройки");
        fragments.add(SettingsFragment.class);

        ViewPagerAdapter adapter = new ViewPagerAdapter(this, fragments);
        viewPager.setAdapter(adapter);
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> tab.setText(tabTitles.get(position))).attach();
    }

    private void checkPermissionsAndInit() {
        boolean missing = false;
        for (String p : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                missing = true;
                break;
            }
        }
        if (missing) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE);
        } else {
            onPermissionsReady();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    Toast.makeText(this, "Разрешение " + permissions[i] + " не предоставлено", Toast.LENGTH_LONG).show();
                }
            }
            if (allGranted) onPermissionsReady();
            else Toast.makeText(this, "Некоторые разрешения не получены.", Toast.LENGTH_LONG).show();
        }
    }

    private void onPermissionsReady() {
        if (smsReadable) {
            SmsService.readAllSms(this, () -> runOnUiThread(() -> {
                if (StatusFragment.instance != null) StatusFragment.instance.onInitialSmsComplete();
            }));
            registerSmsReceiver();
        }
        // Если нет номера или пароля – переключаем на вкладку настроек (последнюю)
        if (PreferencesManager.getPhoneNumber(this).isEmpty() || PreferencesManager.getPassword(this).isEmpty()) {
            viewPager.setCurrentItem(viewPager.getAdapter().getItemCount() - 1);
        }
    }

    private void registerSmsReceiver() {
        if (!smsReadable) return;
        if (smsReceiver == null) smsReceiver = new SmsReceiver();
        IntentFilter filter = new IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
        registerReceiver(smsReceiver, filter);
    }

    private void unregisterSmsReceiver() {
        if (smsReceiver != null) {
            try { unregisterReceiver(smsReceiver); } catch (IllegalArgumentException ignored) {}
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (smsReadable && hasPermissions()) {
            SmsService.readAllSms(this, () -> runOnUiThread(() -> {
                if (StatusFragment.instance != null) StatusFragment.instance.onInitialSmsComplete();
            }));
        }
        registerSmsReceiver();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterSmsReceiver();
    }

    private boolean hasPermissions() {
        for (String p : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    private class SmsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!smsReadable) return;
            if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
                SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
                if (messages != null) {
                    for (SmsMessage msg : messages) {
                        SmsService.processNewSms(context, msg.getMessageBody(), msg.getDisplayOriginatingAddress(), msg.getTimestampMillis());
                    }
                }
            }
        }
    }
}
