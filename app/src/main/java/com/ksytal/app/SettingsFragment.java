package com.ksytal.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.ksytal.app.databinding.FragmentSettingsBinding;
import java.util.ArrayList;
import java.util.List;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private boolean isPasswordVisible = false;
    private List<SmsService.SimCardInfo> simCards = new ArrayList<>();
    private ArrayAdapter<String> simAdapter;

    private final ActivityResultLauncher<Intent> selectContactLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == getActivity().RESULT_OK && result.getData() != null) {
                Uri contactUri = result.getData().getData();
                if (contactUri != null) getPhoneNumberFromContact(contactUri);
            }
        }
    );

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loadSettings();
        loadSimCards();

        binding.btnSelectContact.setOnClickListener(v -> selectContact());
        binding.btnTogglePassword.setOnClickListener(v -> togglePasswordVisibility());
        binding.btnSaveSettings.setOnClickListener(v -> saveSettings());
        binding.btnRefreshSim.setOnClickListener(v -> loadSimCards());
    }

    private void loadSettings() {
        binding.tvPhoneNumber.setText(PreferencesManager.getPhoneNumber(requireContext()).isEmpty() ? "Не выбран" : PreferencesManager.getPhoneNumber(requireContext()));
        binding.etPassword.setText(PreferencesManager.getPassword(requireContext()));
    }

    private void loadSimCards() {
        simCards = SmsService.getAvailableSimCards(requireContext());
        List<String> simNames = new ArrayList<>();
        for (SmsService.SimCardInfo sim : simCards) simNames.add(sim.toString());
        simAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, simNames);
        simAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerSim.setAdapter(simAdapter);
        int savedSimId = PreferencesManager.getSimId(requireContext());
        for (int i = 0; i < simCards.size(); i++) {
            if (simCards.get(i).id == savedSimId) { binding.spinnerSim.setSelection(i); break; }
        }
    }

    private void selectContact() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        selectContactLauncher.launch(intent);
    }

    private void getPhoneNumberFromContact(Uri contactUri) {
        String[] projection = { ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME };
        android.database.Cursor cursor = requireContext().getContentResolver().query(contactUri, projection, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            String phoneNumber = cursor.getString(0);
            if (phoneNumber != null) phoneNumber = phoneNumber.replaceAll("[^0-9+]", "");
            String name = cursor.getString(1);
            binding.tvPhoneNumber.setText(phoneNumber);
            Toast.makeText(getContext(), "Выбран контакт: " + name, Toast.LENGTH_SHORT).show();
            cursor.close();
        }
    }

    private void togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;
        if (isPasswordVisible) {
            binding.etPassword.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            binding.btnTogglePassword.setText("🙈");
        } else {
            binding.etPassword.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            binding.btnTogglePassword.setText("👁️");
        }
        binding.etPassword.setSelection(binding.etPassword.getText().length());
    }

    private void saveSettings() {
        String phoneNumber = binding.tvPhoneNumber.getText().toString();
        String password = binding.etPassword.getText().toString();

        if (phoneNumber.equals("Не выбран") || phoneNumber.isEmpty()) {
            Toast.makeText(getContext(), "Выберите номер телефона", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.isEmpty() || password.length() < 1 || password.length() > 5 || !password.matches("\\d+")) {
            Toast.makeText(getContext(), "Пароль должен содержать 1-5 цифр", Toast.LENGTH_SHORT).show();
            return;
        }

        int selectedPosition = binding.spinnerSim.getSelectedItemPosition();
        if (selectedPosition >= 0 && selectedPosition < simCards.size()) {
            PreferencesManager.saveSimId(requireContext(), simCards.get(selectedPosition).id);
        }
        PreferencesManager.savePhoneNumber(requireContext(), phoneNumber);
        PreferencesManager.savePassword(requireContext(), password);
        Toast.makeText(getContext(), "Настройки сохранены", Toast.LENGTH_SHORT).show();

        // После сохранения настроек принудительно читаем SMS и переключаем на вкладку Статус
        if (getActivity() != null) {
            SmsService.readAllSms(requireContext(), () -> {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (StatusFragment.instance != null) StatusFragment.instance.onInitialSmsComplete();
                        // Переключить ViewPager на вкладку Статус (0)
                        MainActivity activity = (MainActivity) getActivity();
                        androidx.viewpager2.widget.ViewPager2 viewPager = activity.findViewById(R.id.viewPager);
                        if (viewPager != null) viewPager.setCurrentItem(0);
                    });
                }
            });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
