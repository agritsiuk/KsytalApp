package com.ksytal.app;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import java.util.List;

public class ViewPagerAdapter extends FragmentStateAdapter {
    private final List<Class<? extends Fragment>> fragmentClasses;

    public ViewPagerAdapter(@NonNull FragmentActivity activity, List<Class<? extends Fragment>> fragments) {
        super(activity);
        this.fragmentClasses = fragments;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        try {
            return fragmentClasses.get(position).newInstance();
        } catch (Exception e) {
            return new StatusFragment(); // fallback
        }
    }

    @Override
    public int getItemCount() {
        return fragmentClasses.size();
    }
}
