package com.ksytal.app;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.os.Handler;
import android.os.Looper;
import android.database.Cursor;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class SmsService {
    private static DataModels.DeviceStatus currentStatus = new DataModels.DeviceStatus();
    private static List<OnStatusChangeListener> listeners = new ArrayList<>();
    private static boolean isReadingSms = false;
    private static Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final boolean IS_SMS_READABLE = Build.VERSION.SDK_INT < 34; // Android 14 = API 34

    public interface OnStatusChangeListener {
        void onStatusChanged(DataModels.DeviceStatus status);
    }

    public static void addStatusListener(OnStatusChangeListener listener) {
        listeners.add(listener);
        listener.onStatusChanged(currentStatus.copy());
    }

    public static DataModels.DeviceStatus getCurrentStatus() {
        return currentStatus.copy();
    }

    public static void sendSms(Context context, String phoneNumber, String message, int simId) {
        try {
            SmsManager smsManager;
            if (simId != -1 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                smsManager = SmsManager.getSmsManagerForSubscriptionId(simId);
            } else {
                smsManager = SmsManager.getDefault();
            }
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<SimCardInfo> getAvailableSimCards(Context context) {
        List<SimCardInfo> simCards = new ArrayList<>();
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                if (subscriptionManager != null) {
                    List<SubscriptionInfo> subscriptionInfoList = subscriptionManager.getActiveSubscriptionInfoList();
                    if (subscriptionInfoList != null && !subscriptionInfoList.isEmpty()) {
                        for (SubscriptionInfo subInfo : subscriptionInfoList) {
                            SimCardInfo simCard = new SimCardInfo();
                            simCard.id = subInfo.getSubscriptionId();
                            simCard.displayName = subInfo.getDisplayName().toString();
                            simCard.carrierName = subInfo.getCarrierName().toString();
                            simCard.number = subInfo.getNumber();
                            simCards.add(simCard);
                        }
                        return simCards;
                    }
                }
            }
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager != null) {
                SimCardInfo simCard = new SimCardInfo();
                simCard.id = 0;
                simCard.displayName = "SIM 1";
                simCard.carrierName = telephonyManager.getSimOperatorName();
                if (simCard.carrierName == null || simCard.carrierName.isEmpty()) {
                    simCard.carrierName = "SIM-карта";
                }
                simCards.add(simCard);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        if (simCards.isEmpty()) {
            SimCardInfo defaultSim = new SimCardInfo();
            defaultSim.id = -1;
            defaultSim.displayName = "SIM 1";
            defaultSim.carrierName = "По умолчанию";
            simCards.add(defaultSim);
        }
        return simCards;
    }

    public static class SimCardInfo {
        public int id;
        public String displayName;
        public String carrierName;
        public String number;
        @Override
        public String toString() {
            if (id == -1) return "SIM по умолчанию";
            if (carrierName != null && !carrierName.isEmpty()) {
                return carrierName + " (" + displayName + ")";
            }
            return "SIM " + (id + 1);
        }
    }

    private static class CollectedData {
        boolean hasVoltage = false;
        boolean hasCurrentTemp = false;
        boolean hasRequiredTemp = false;
        boolean hasBalance = false;
        boolean hasUpperThreshold = false;
        boolean hasLowerThreshold = false;
        boolean isComplete() {
            return hasVoltage && hasCurrentTemp && hasRequiredTemp && hasBalance && hasUpperThreshold && hasLowerThreshold;
        }
    }

    public static void readAllSms(Context context, Runnable onComplete) {
        if (!IS_SMS_READABLE) {
            if (onComplete != null) mainHandler.post(onComplete);
            return;
        }
        if (isReadingSms) return;
        isReadingSms = true;
        new Thread(() -> {
            Uri inboxUri = Uri.parse("content://sms/inbox");
            String[] projection = {"_id", "address", "body", "date"};
            String sortOrder = "date DESC";
            DataModels.DeviceStatus tempStatus = new DataModels.DeviceStatus();
            CollectedData collected = new CollectedData();
            String targetNumber = PreferencesManager.getPhoneNumber(context);
            if (targetNumber == null || targetNumber.isEmpty()) {
                isReadingSms = false;
                if (onComplete != null) mainHandler.post(onComplete);
                return;
            }
            Cursor cursor = context.getContentResolver().query(inboxUri, projection, null, null, sortOrder);
            if (cursor != null) {
                while (cursor.moveToNext() && !collected.isComplete()) {
                    String address = cursor.getString(cursor.getColumnIndexOrThrow("address"));
                    String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
                    long date = cursor.getLong(cursor.getColumnIndexOrThrow("date"));
                    Date msgDate = new Date(date);
                    if (address != null && address.contains(targetNumber)) {
                        DataModels.ParsedSms parsed = parseSmsMessage(body);
                        if (parsed.type != DataModels.SmsType.UNKNOWN) {
                            tempStatus = updateStatusFromParsedSms(tempStatus, parsed, msgDate, collected);
                        }
                    }
                }
                cursor.close();
            }
            currentStatus = tempStatus;
            notifyStatusUpdate();
            isReadingSms = false;
            if (onComplete != null) mainHandler.post(onComplete);
        }).start();
    }

    public static void processNewSms(Context context, String body, String sender, long date) {
        if (!IS_SMS_READABLE) return;
        String targetNumber = PreferencesManager.getPhoneNumber(context);
        if (!targetNumber.isEmpty() && sender != null && sender.contains(targetNumber)) {
            DataModels.ParsedSms parsed = parseSmsMessage(body);
            if (parsed.type != DataModels.SmsType.UNKNOWN) {
                CollectedData dummy = new CollectedData();
                currentStatus = updateStatusFromParsedSms(currentStatus, parsed, new Date(date), dummy);
                notifyStatusUpdate();
                if (StatusFragment.instance != null) StatusFragment.instance.notifyNewData();
            }
        }
    }

    private static DataModels.ParsedSms parseSmsMessage(String body) {
        Map<String, Object> data = new HashMap<>();
        if (body.contains("Propalo naprjagenie 220V") || body.contains("Naprjagenie propalo")) {
            data.put("voltage", false);
            return new DataModels.ParsedSms(DataModels.SmsType.VOLTAGE_LOST, data, body);
        }
        if (body.contains("Vosstanovleno 220V") || body.contains("Naprjagenie vosstanovleno")) {
            data.put("voltage", true);
            return new DataModels.ParsedSms(DataModels.SmsType.VOLTAGE_RESTORED, data, body);
        }
        if (body.contains("systema KSYTAL-") && body.contains("Temp.H=")) {
            Pattern pattern = Pattern.compile("Temp\\.(H|L|R)=\\+?(\\d+)");
            Matcher matcher = pattern.matcher(body);
            while (matcher.find()) {
                String type = matcher.group(1);
                int value = Integer.parseInt(matcher.group(2));
                if (type.equals("H")) data.put("upperThreshold", value);
                else if (type.equals("L")) data.put("lowerThreshold", value);
                else if (type.equals("R")) data.put("requiredTemperature", value);
            }
            return new DataModels.ParsedSms(DataModels.SmsType.TEMPERATURE_INFO, data, body);
        }
        Pattern balancePattern = Pattern.compile("^(\\d+\\.\\d+)\\s+р\\.", Pattern.MULTILINE);
        Matcher balanceMatcher = balancePattern.matcher(body);
        if (balanceMatcher.find()) {
            try {
                data.put("balance", Float.parseFloat(balanceMatcher.group(1)));
                return new DataModels.ParsedSms(DataModels.SmsType.BALANCE_INFO, data, body);
            } catch (Exception e) {}
        }
        if (body.contains("T1=") && body.contains("T2=")) {
            Pattern tempPattern = Pattern.compile("T1=\\+?(\\d+),?(\\d?)C");
            Matcher tempMatcher = tempPattern.matcher(body);
            if (tempMatcher.find()) {
                int whole = Integer.parseInt(tempMatcher.group(1));
                int fraction = tempMatcher.group(2).isEmpty() ? 0 : Integer.parseInt(tempMatcher.group(2));
                data.put("currentTemperature", whole + (fraction / 10.0f));
            }
            boolean hasVoltage = body.contains("Naprjagenie norma");
            if (body.contains("Naprjagenie propalo")) hasVoltage = false;
            data.put("voltage", hasVoltage);
            Pattern requiredPattern = Pattern.compile("Temp\\.R=\\+?(\\d+)");
            Matcher requiredMatcher = requiredPattern.matcher(body);
            if (requiredMatcher.find()) data.put("requiredTemperature", Integer.parseInt(requiredMatcher.group(1)));
            return new DataModels.ParsedSms(DataModels.SmsType.STATUS_INFO, data, body);
        }
        return new DataModels.ParsedSms(DataModels.SmsType.UNKNOWN, data, body);
    }

    private static DataModels.DeviceStatus updateStatusFromParsedSms(DataModels.DeviceStatus current, DataModels.ParsedSms parsed, Date msgDate, CollectedData collected) {
        DataModels.DeviceStatus updated = current.copy();
        switch (parsed.type) {
            case VOLTAGE_LOST: if (!collected.hasVoltage) { updated.updateVoltage(false, msgDate); collected.hasVoltage = true; } break;
            case VOLTAGE_RESTORED: if (!collected.hasVoltage) { updated.updateVoltage(true, msgDate); collected.hasVoltage = true; } break;
            case TEMPERATURE_INFO:
                if (parsed.data.containsKey("upperThreshold") && !collected.hasUpperThreshold) { updated.updateUpperThreshold((Integer) parsed.data.get("upperThreshold"), msgDate); collected.hasUpperThreshold = true; }
                if (parsed.data.containsKey("lowerThreshold") && !collected.hasLowerThreshold) { updated.updateLowerThreshold((Integer) parsed.data.get("lowerThreshold"), msgDate); collected.hasLowerThreshold = true; }
                if (parsed.data.containsKey("requiredTemperature") && !collected.hasRequiredTemp) { updated.updateRequiredTemperature((Integer) parsed.data.get("requiredTemperature"), msgDate); collected.hasRequiredTemp = true; }
                break;
            case BALANCE_INFO:
                if (!collected.hasBalance && parsed.data.containsKey("balance")) { updated.updateBalance((Float) parsed.data.get("balance"), msgDate); collected.hasBalance = true; }
                break;
            case STATUS_INFO:
                if (parsed.data.containsKey("currentTemperature") && !collected.hasCurrentTemp) { updated.updateCurrentTemperature((Float) parsed.data.get("currentTemperature"), msgDate); collected.hasCurrentTemp = true; }
                if (parsed.data.containsKey("voltage") && !collected.hasVoltage) { updated.updateVoltage((Boolean) parsed.data.get("voltage"), msgDate); collected.hasVoltage = true; }
                if (parsed.data.containsKey("requiredTemperature") && !collected.hasRequiredTemp) { updated.updateRequiredTemperature((Integer) parsed.data.get("requiredTemperature"), msgDate); collected.hasRequiredTemp = true; }
                break;
        }
        return updated;
    }

    public static void requestStatus(Context context) {
        String password = PreferencesManager.getPassword(context);
        String phoneNumber = PreferencesManager.getPhoneNumber(context);
        if (!password.isEmpty() && !phoneNumber.isEmpty()) {
            sendSms(context, phoneNumber, "Kak dela? " + password, PreferencesManager.getSimId(context));
        }
    }

    public static void requestBalance(Context context) {
        String password = PreferencesManager.getPassword(context);
        String phoneNumber = PreferencesManager.getPhoneNumber(context);
        if (!password.isEmpty() && !phoneNumber.isEmpty()) {
            sendSms(context, phoneNumber, "Balans? " + password, PreferencesManager.getSimId(context));
        }
    }

    public static void setTemperature(Context context, int temperature) {
        String password = PreferencesManager.getPassword(context);
        String phoneNumber = PreferencesManager.getPhoneNumber(context);
        if (!password.isEmpty() && !phoneNumber.isEmpty()) {
            sendSms(context, phoneNumber, String.format("Temp.R=%02d %s", temperature, password), PreferencesManager.getSimId(context));
        }
    }

    private static void notifyStatusUpdate() {
        mainHandler.post(() -> {
            DataModels.DeviceStatus copy = currentStatus.copy();
            for (OnStatusChangeListener l : listeners) l.onStatusChanged(copy);
        });
    }
}
