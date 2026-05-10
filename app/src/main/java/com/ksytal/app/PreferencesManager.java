package com.ksytal.app;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferencesManager {
    private static final String PREF_NAME = "ksytal_prefs";
    private static final String KEY_PHONE_NUMBER = "phone_number";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_SIM_ID = "sim_id";
    
    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    public static void savePhoneNumber(Context context, String phoneNumber) {
        getPrefs(context).edit().putString(KEY_PHONE_NUMBER, phoneNumber).apply();
    }
    
    public static String getPhoneNumber(Context context) {
        return getPrefs(context).getString(KEY_PHONE_NUMBER, "");
    }
    
    public static void savePassword(Context context, String password) {
        getPrefs(context).edit().putString(KEY_PASSWORD, password).apply();
    }
    
    public static String getPassword(Context context) {
        return getPrefs(context).getString(KEY_PASSWORD, "");
    }
    
    public static void saveSimId(Context context, int simId) {
        getPrefs(context).edit().putInt(KEY_SIM_ID, simId).apply();
    }
    
    public static int getSimId(Context context) {
        return getPrefs(context).getInt(KEY_SIM_ID, -1);
    }
}
