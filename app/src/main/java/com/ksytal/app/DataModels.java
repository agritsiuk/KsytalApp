package com.ksytal.app;

import java.util.Date;

public class DataModels {
    
    public static class DeviceStatus {
        public boolean voltage = false;
        public Float currentTemperature = null;
        public Integer requiredTemperature = null;
        public Float lastBalance = null;
        public Integer upperThreshold = null;
        public Integer lowerThreshold = null;
        public Date lastUpdate = new Date();
        
        public Date voltageTime = null;
        public Date currentTemperatureTime = null;
        public Date requiredTemperatureTime = null;
        public Date lastBalanceTime = null;
        public Date upperThresholdTime = null;
        public Date lowerThresholdTime = null;
        
        public DeviceStatus copy() {
            DeviceStatus copy = new DeviceStatus();
            copy.voltage = this.voltage;
            copy.currentTemperature = this.currentTemperature;
            copy.requiredTemperature = this.requiredTemperature;
            copy.lastBalance = this.lastBalance;
            copy.upperThreshold = this.upperThreshold;
            copy.lowerThreshold = this.lowerThreshold;
            copy.lastUpdate = this.lastUpdate != null ? new Date(this.lastUpdate.getTime()) : new Date();
            
            if (this.voltageTime != null) copy.voltageTime = new Date(this.voltageTime.getTime());
            if (this.currentTemperatureTime != null) copy.currentTemperatureTime = new Date(this.currentTemperatureTime.getTime());
            if (this.requiredTemperatureTime != null) copy.requiredTemperatureTime = new Date(this.requiredTemperatureTime.getTime());
            if (this.lastBalanceTime != null) copy.lastBalanceTime = new Date(this.lastBalanceTime.getTime());
            if (this.upperThresholdTime != null) copy.upperThresholdTime = new Date(this.upperThresholdTime.getTime());
            if (this.lowerThresholdTime != null) copy.lowerThresholdTime = new Date(this.lowerThresholdTime.getTime());
            
            return copy;
        }
        
        public void updateVoltage(boolean value, Date time) {
            this.voltage = value;
            this.voltageTime = time;
        }
        
        public void updateCurrentTemperature(float value, Date time) {
            this.currentTemperature = value;
            this.currentTemperatureTime = time;
        }
        
        public void updateRequiredTemperature(int value, Date time) {
            this.requiredTemperature = value;
            this.requiredTemperatureTime = time;
        }
        
        public void updateBalance(float value, Date time) {
            this.lastBalance = value;
            this.lastBalanceTime = time;
        }
        
        public void updateUpperThreshold(int value, Date time) {
            this.upperThreshold = value;
            this.upperThresholdTime = time;
        }
        
        public void updateLowerThreshold(int value, Date time) {
            this.lowerThreshold = value;
            this.lowerThresholdTime = time;
        }
    }
    
    public enum SmsType {
        VOLTAGE_LOST, VOLTAGE_RESTORED, TEMPERATURE_INFO, BALANCE_INFO, STATUS_INFO, UNKNOWN
    }
    
    public static class ParsedSms {
        public SmsType type;
        public java.util.Map<String, Object> data;
        public String rawText;
        
        public ParsedSms(SmsType type, java.util.Map<String, Object> data, String rawText) {
            this.type = type;
            this.data = data;
            this.rawText = rawText;
        }
    }
}
