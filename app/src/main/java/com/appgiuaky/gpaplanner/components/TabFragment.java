package com.appgiuaky.gpaplanner.components;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base for the bottom-navigation tabs. Hidden tabs stay alive (MainActivity only hides them), so
 * without this every edit would rebuild all five screens; hidden tabs now render once when shown.
 */
public abstract class TabFragment extends Fragment {

    private final Map<Observer<?>, Runnable> pending = new LinkedHashMap<>();

    /** Like LiveData.observe, but while the tab is hidden only the latest value is kept until it is shown. */
    protected <T> void observeWhenVisible(LiveData<T> data, Observer<T> observer) {
        data.observe(getViewLifecycleOwner(), value -> {
            if (isHidden()) pending.put(observer, () -> observer.onChanged(value));
            else observer.onChanged(value);
        });
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden || pending.isEmpty()) return;
        List<Runnable> updates = new ArrayList<>(pending.values());
        pending.clear();
        for (Runnable update : updates) update.run();
    }

    @Override
    public void onDestroyView() {
        pending.clear();
        super.onDestroyView();
    }
}
