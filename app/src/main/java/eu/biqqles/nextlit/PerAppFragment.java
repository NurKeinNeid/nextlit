
/*
 * Copyright © 2018 biqqles.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package eu.biqqles.nextlit;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PerAppFragment extends Fragment {
    // A fragment which allows the user to configure notifications on a per-app basis.
    ApplicationAdapter adapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final Activity activity = getActivity();
        final Context context = getContext();
        final View view = inflater.inflate(R.layout.fragment_per_app_config, container, false);
        final SwipeRefreshLayout swipeLayout = view.findViewById(R.id.swipeRefreshLayout);
        final RecyclerView recyclerView = view.findViewById(R.id.recyclerView);

        if (activity == null || context == null) {
            return view;
        }

        activity.setTitle(R.string.title_fragment_per_app_config);

        adapter = new ApplicationAdapter(context, swipeLayout);

        recyclerView.setAdapter(adapter);

        swipeLayout.setRefreshing(true);
        swipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                adapter.refresh();
            }
        });
        return view;
    }
}


class ApplicationAdapter extends RecyclerView.Adapter<ApplicationAdapter.AppCard> {
    private PatternProvider patternProvider;
    private List<AppInfo> apps = new ArrayList<>();
    private Context context;
    private PackageManager pm;
    private SwipeRefreshLayout swipe;
    private SharedPreferences appsEnabled;  // preferences store each app's status and pattern,
    private SharedPreferences appsPatterns;  // using package name as a key

    static class ParallelPackageLoader extends AsyncTask<ApplicationAdapter, Void, Void> {
        // This AsyncTask ensures that the RecycleView is populated asynchronously. This is required
        // because PackageManager takes a while to return data.
        private ApplicationAdapter adapter;

        @Override
        protected Void doInBackground(ApplicationAdapter... params) {
            adapter = params[0];  // 3 billion devices run Java
            List<ApplicationInfo> packages = adapter.pm.getInstalledApplications(PackageManager.GET_META_DATA);

            for (ApplicationInfo p : packages) {
                AppInfo app = new AppInfo();
                app.name = (String) p.loadLabel(adapter.pm);
                app.packageName = p.packageName;
                app.icon = p.loadIcon(adapter.pm);
                adapter.apps.add(app);
            }
            Collections.sort(adapter.apps);  // sort apps by name
            return null;
        }
        @Override
        protected void onPostExecute(Void param) {
            adapter.notifyDataSetChanged();  // update views using this adapter
            // ending the refresh animation here is hacky; this shouldn't need to be done in  an
            // adapter. However I can't see a reliable alternative
            adapter.swipe.setRefreshing(false);
        }
    }

    static class AppInfo implements Comparable<AppInfo> {
        String name = "";
        String packageName = "";
        Drawable icon;

        @Override
        public int compareTo(@NonNull AppInfo comparate) {
            // Instances of this class should be sorted by their name attributes
            return name.compareToIgnoreCase(comparate.name);
        }
    }

    static class AppCard extends RecyclerView.ViewHolder {
        static final long EXPAND_ANIMATION_DURATION = 240L;
        ImageView icon;
        TextView name;
        TextView packageName;
        ToggleButton expand;
        LinearLayout configLayout;
        CheckBox enabled;
        Spinner pattern;
        View view;

        AppCard(View itemView) {
            super(itemView);
            view = itemView;
            icon = itemView.findViewById(R.id.icon);
            name = itemView.findViewById(R.id.name);
            packageName = itemView.findViewById(R.id.packageName);
            expand = itemView.findViewById(R.id.expand);
            configLayout = itemView.findViewById(R.id.configLayout);
            enabled = itemView.findViewById(R.id.checkBox);
            pattern = itemView.findViewById(R.id.spinner);
        }

        void setExpansionState(boolean expanded) {
            // animate button
            expand.animate().
                    rotation(expanded ? 180f : 0f).setDuration(EXPAND_ANIMATION_DURATION).start();

            // show configLayout
            configLayout.setVisibility(expanded ? View.VISIBLE : View.GONE);
        }
    }

    ApplicationAdapter(Context context, SwipeRefreshLayout swipe) {
        patternProvider = new PatternProvider(context);
        this.context = context;
        this.pm = context.getPackageManager();
        this.swipe = swipe;
        appsEnabled = context.getSharedPreferences("apps_enabled", Activity.MODE_PRIVATE);
        appsPatterns = context.getSharedPreferences("apps_patterns", Activity.MODE_PRIVATE);
        refresh();
    }

    public void refresh() {
        // Refresh the adapter.
        apps.clear();
        ParallelPackageLoader loader = new ParallelPackageLoader();
        loader.execute(this);
    }

    @Override
    public @NonNull AppCard onCreateViewHolder(@NonNull ViewGroup parent, int i) {
        // Create card.
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.per_app_card, parent, false);
        return new AppCard(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final AppCard card, int i) {
        // Populate card.
        final AppInfo app = apps.get(i);

        card.icon.setImageDrawable(app.icon);
        card.name.setText(app.name);
        card.packageName.setText(app.packageName);

        // "expand" button; shows configLayout
        card.expand.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                card.setExpansionState(checked);
                if (checked) {
                    // on expansion, populate fields
                    ArrayList<String> patternNames = patternProvider.getNames();
                    patternNames.set(0, context.getResources().getString(R.string.default_pattern));
                    card.pattern.setAdapter(new ArrayAdapter<>(context,
                            android.R.layout.simple_spinner_dropdown_item, patternNames));

                    final boolean appEnabled = appsEnabled.getBoolean(app.packageName, true);

                    // "enabled" checkbox
                    card.enabled.setChecked(appEnabled);
                    card.enabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        public void onCheckedChanged(CompoundButton button, boolean checked) {
                            appsEnabled.edit().putBoolean(app.packageName, checked).apply();
                            card.pattern.setEnabled(checked);
                        }
                    });

                    // pattern spinner
                    card.pattern.setEnabled(appEnabled);
                    card.pattern.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        public void onItemSelected(AdapterView adapterView, View view, int i, long l) {
                            final String patternName =
                                    i > 0 ? card.pattern.getSelectedItem().toString() : null;
                            appsPatterns.edit().putString(app.packageName, patternName).apply();
                        }

                        public void onNothingSelected(AdapterView<?> adapterView) { }
                    });
                }
            }
        });

        // make entire card clickable
        card.view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                card.expand.toggle();
            }
        });
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }
}
