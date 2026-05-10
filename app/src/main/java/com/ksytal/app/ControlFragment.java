package com.ksytal.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.ksytal.app.databinding.FragmentControlBinding;

public class ControlFragment extends Fragment {

    private static final int MIN_TEMPERATURE = 5;
    private static final int MAX_TEMPERATURE = 40;
    private static final int WARNING_THRESHOLD = 10; // предупреждение при установке <10
    private static final int PERMISSION_REQUEST_WRITE_SMS = 200;

    private FragmentControlBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentControlBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.btnRequestStatus.setOnClickListener(v -> requestStatus());
        binding.btnSetTemperature.setOnClickListener(v -> showSetTemperatureDialog());
        binding.btnRequestBalance.setOnClickListener(v -> requestBalance());
        binding.btnDeleteOldMessages.setOnClickListener(v -> checkWritePermissionAndDelete());
    }

    private void requestStatus() {
        String password = PreferencesManager.getPassword(requireContext());
        if (password.isEmpty()) {
            Toast.makeText(getContext(), "Сначала настройте пароль", Toast.LENGTH_SHORT).show();
            return;
        }
        String phoneNumber = PreferencesManager.getPhoneNumber(requireContext());
        if (phoneNumber.isEmpty()) {
            Toast.makeText(getContext(), "Сначала настройте номер телефона", Toast.LENGTH_SHORT).show();
            return;
        }
        SmsService.requestStatus(requireContext());
        Toast.makeText(getContext(), "Запрос статуса отправлен", Toast.LENGTH_SHORT).show();
    }

    private void showSetTemperatureDialog() {
        DataModels.DeviceStatus currentStatus = SmsService.getCurrentStatus();
        String currentTempText = "";
        if (currentStatus.requiredTemperature != null && currentStatus.requiredTemperatureTime != null) {
            currentTempText = "\n\nТекущее значение: " + currentStatus.requiredTemperature + " °C";
        }
        EditText input = new EditText(getContext());
        input.setHint("Введите температуру от " + MIN_TEMPERATURE + " до " + MAX_TEMPERATURE);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(requireContext())
                .setTitle("Установка требуемой температуры" + currentTempText)
                .setView(input)
                .setPositiveButton("Установить", (dialog, which) -> {
                    try {
                        int temp = Integer.parseInt(input.getText().toString());
                        if (temp >= MIN_TEMPERATURE && temp <= MAX_TEMPERATURE) {
                            if (temp < WARNING_THRESHOLD) {
                                // Предупреждение
                                new AlertDialog.Builder(requireContext())
                                        .setTitle("Подтверждение")
                                        .setMessage("Температура ниже " + WARNING_THRESHOLD + "°C может привести к нежелательным последствиям. Продолжить?")
                                        .setPositiveButton("Да", (d, w) -> sendTemperature(temp))
                                        .setNegativeButton("Нет", null)
                                        .show();
                            } else {
                                sendTemperature(temp);
                            }
                        } else {
                            Toast.makeText(getContext(),
                                    "Температура должна быть от " + MIN_TEMPERATURE + " до " + MAX_TEMPERATURE,
                                    Toast.LENGTH_SHORT).show();
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(getContext(), "Некорректная температура", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void sendTemperature(int temp) {
        SmsService.setTemperature(requireContext(), temp);
        Toast.makeText(getContext(), "Команда отправлена", Toast.LENGTH_SHORT).show();
    }

    private void requestBalance() {
        String password = PreferencesManager.getPassword(requireContext());
        String phoneNumber = PreferencesManager.getPhoneNumber(requireContext());
        if (password.isEmpty() || phoneNumber.isEmpty()) {
            Toast.makeText(getContext(), "Сначала настройте пароль и номер телефона", Toast.LENGTH_SHORT).show();
            return;
        }
        SmsService.requestBalance(requireContext());
        Toast.makeText(getContext(), "Запрос баланса отправлен", Toast.LENGTH_SHORT).show();
    }

    private void checkWritePermissionAndDelete() {
        if (ContextCompat.checkSelfPermission(requireContext(), "android.permission.WRITE_SMS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.WRITE_SMS"}, PERMISSION_REQUEST_WRITE_SMS);
        } else {
            showDeleteMessagesDialog();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_WRITE_SMS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showDeleteMessagesDialog();
            } else {
                Toast.makeText(getContext(), "Разрешение WRITE_SMS не предоставлено. Удаление невозможно.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showDeleteMessagesDialog() {
        final int[] daysOptions = {0, 90, 30, 60, 180, 365, 7, 14};
        String[] daysStrings = new String[daysOptions.length];
        for (int i = 0; i < daysOptions.length; i++) {
            daysStrings[i] = (daysOptions[i] == 0) ? "0 дней (удалить все сообщения)" : daysOptions[i] + " дней";
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Удалить сообщения старше")
                .setItems(daysStrings, (dialog, which) -> confirmDeleteMessages(daysOptions[which]))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void confirmDeleteMessages(int days) {
        String phoneNumber = PreferencesManager.getPhoneNumber(requireContext());
        if (phoneNumber.isEmpty()) {
            Toast.makeText(getContext(), "Сначала настройте номер телефона", Toast.LENGTH_SHORT).show();
            return;
        }
        String message = (days == 0) ? "Удалить ВСЕ сообщения с номером " + phoneNumber + "?" : "Удалить сообщения старше " + days + " дней с номером " + phoneNumber + "?";
        new AlertDialog.Builder(requireContext())
                .setTitle("Подтверждение")
                .setMessage(message)
                .setPositiveButton("Удалить", (dialog, which) -> {
                    SmsCleaner.cleanOldMessages(requireContext(), phoneNumber, days, new SmsCleaner.CleanupCallback() {
                        @Override
                        public void onCleanupComplete(int deletedCount) {
                            if (deletedCount == 0 && ContextCompat.checkSelfPermission(requireContext(), "android.permission.WRITE_SMS") == PackageManager.PERMISSION_GRANTED) {
                                Toast.makeText(getContext(), "Не найдено сообщений для удаления.", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(getContext(), "Удалено сообщений: " + deletedCount, Toast.LENGTH_LONG).show();
                            }
                        }
                        @Override
                        public void onCleanupError(String error) {
                            Toast.makeText(getContext(), "Ошибка: " + error, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
