
/*
 * Copyright © 2018 biqqles.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package eu.biqqles.nextlit;

import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity
                          implements NavigationView.OnNavigationItemSelectedListener,
                                     SharedPreferences.OnSharedPreferenceChangeListener {
    // A navigation drawer that allows the service to be enabled and disabled, patterns to be
    // previewed and a default pattern to be set.
    private LedControl ledcontrol;
    private SharedPreferences prefs;
    private Resources resources;

    private DrawerLayout navigationDrawer;
    private NavigationView navigationView;
    private Spinner patternSpinner;
    private ToggleButton previewButton;
    private Switch serviceSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        resources = getResources();

        final PatternProvider patternProvider = new PatternProvider(getApplicationContext());

        try {
            ledcontrol = new LedControl(patternProvider.patterns);
        } catch (IOException e) {
            // root access denied/unavailable
            rootDenied();
            return;
        }

        // ensure that the service is aware of all changes to settings
        // (see onSharedPreferenceChanged)
        prefs.registerOnSharedPreferenceChangeListener(this);
        getSharedPreferences("apps_enabled", MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(this);
        getSharedPreferences("apps_patterns", MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(this);

        // set up navigation menu...
        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        navigationDrawer = findViewById(R.id.drawer_layout);

        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        final ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, navigationDrawer, toolbar, R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        navigationDrawer.addDrawerListener(toggle);
        toggle.syncState();

        // ...and header
        final View header = navigationView.getHeaderView(0);
        final TextView subtitle = header.findViewById(R.id.service_state);
        serviceSwitch = header.findViewById(R.id.serviceSwitch);
        patternSpinner = header.findViewById(R.id.patternSpinner);
        previewButton = header.findViewById(R.id.previewButton);

        // this switch enables and disables the notification service
        serviceSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                int statusText;

                if (checked) {
                    // take user to Notification access if they haven't enabled the service already
                    if (!serviceBound()) {
                        showNotificationListenerSettings();
                    }
                    statusText = R.string.service_enabled;
                } else {
                    statusText = R.string.service_disabled;
                }

                subtitle.setText(statusText);

                /* Normal procedure here would be to call start/stopService, but for whatever reason
                Android ignores these calls for NotificationListenerServices, and so effectively the
                only thing that governs whether a service is enabled or not is if it's enabled in
                Notification access. Instead, we accept that the service will always run and just
                modify a static flag that determines if the lights should be enabled or not. */
                prefs.edit().putBoolean("service_enabled", checked).apply();
            }
        });

        serviceSwitch.setChecked(prefs.getBoolean("service_enabled", false));

        // this spinner allows the default pattern to be selected
        ArrayList<String> patternNames = patternProvider.getNames();
        patternNames.set(0, resources.getString(R.string.choose_pattern));
        patternSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, patternNames));

        patternSpinner.setSelection(patternProvider.indexOf(prefs.getString("pattern_name", null)));

        patternSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView adapterView, View view, int i, long l) {
                // halt preview and update preferences
                stopPreview(previewButton);
                // no pattern selected is represented by null
                String patternName = i > 0 ? patternSpinner.getSelectedItem().toString() : null;
                prefs.edit().putString("pattern_name", patternName).apply();
            }

            public void onNothingSelected(AdapterView<?> adapterView) { }
        });

        // this toggle allows the pattern currently selected in the spinner to be previewed
        previewButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                if (button.isPressed()) {  // ignore calls from setChecked and the like
                    if (checked) {
                        startPreview(button, patternSpinner.getSelectedItem().toString());
                    } else {
                        stopPreview(button);
                    }
                }
            }
        });

        // display default fragment
        onNavigationItemSelected(navigationView.getMenu().findItem(R.id.nav_per_app));
        // open navigation drawer
        navigationDrawer.openDrawer(GravityCompat.START);
    }

    @Override
    public void onBackPressed() {
        // Closes navigation drawer on back button press.
        if (navigationDrawer.isDrawerOpen(GravityCompat.START)) {
            navigationDrawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handles navigation menu item clicks.
        final int id = item.getItemId();

        if (id == R.id.nav_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        } else {
            Fragment fragment = new Fragment();

            switch (id) {
                case R.id.nav_per_app:
                    fragment = new PerAppFragment();
                    break;
            }

            // select fragment in menu
            navigationView.setCheckedItem(id);
            // display fragment
            getSupportFragmentManager()
                    .beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .replace(R.id.content_frame, fragment)
                    .commitNow();
        }

        navigationDrawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // This ensures that the lights' state always reflects the current settings. It applies
        // throughout the application.
        restoreLightsState();
    }

    @Override
    protected void onPause() {
        // Stops preview when activity loses focus.
        stopPreview(previewButton);
        super.onPause();
    }

    @Override
    protected void onResume() {
        // Ensures that the service switch reflects whether the service is bound. If the user
        // disables the service in system settings the switch within the app should reflect that.
        if (serviceSwitch.isChecked()) {
            serviceSwitch.setChecked(serviceBound());
        }
        super.onResume();
    }

    public void restoreLightsState() {
        // Restores the "proper" state of the leds. Should be called after any setting which might
        // require a change in their current visibility has been modified.
        if (serviceBound()) {
            final Intent serviceIntent = new Intent(this, NotificationLightsService.class);
            serviceIntent.addCategory("restore_state");
            startService(serviceIntent);
            stopService(serviceIntent);
        } else {
            ledcontrol.clearAll();
        }
    }

    public void startPreview(CompoundButton button, String patternName) {
        // Begins previewing the given pattern.
        button.setChecked(true);

        ledcontrol.clearAll();
        ledcontrol.setPattern(patternName);

        final String message = MessageFormat.format(resources.getString(R.string.preview_active),
                patternName);
        Snackbar.make(navigationView, message, Snackbar.LENGTH_LONG).show();
    }

    public void stopPreview(CompoundButton button) {
        // Halts any ongoing preview.
        button.setChecked(false);
        restoreLightsState();
    }

    private boolean serviceBound() {
        // Reports whether the notification service has been bound (i.e. activated).
        final String enabledNotificationListeners = Settings.Secure.getString(
                getContentResolver(),"enabled_notification_listeners");

        return !(enabledNotificationListeners == null ||
                !enabledNotificationListeners.contains(getPackageName()));
    }

    private void rootDenied() {
        // Handles root access unavailable or denied.
        Toast.makeText(this, resources.getString(R.string.root_denied), Toast.LENGTH_LONG).show();
        // prevent the service from constantly trying to restart itself if already enabled
        prefs.edit().putBoolean("service_enabled", false).apply();
        finish();
    }

    private void showNotificationListenerSettings() {
        // Presents the user with the "Notification access" settings screen and asks them to give
        // the service access.
        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        Toast.makeText(MainActivity.this,
                resources.getString(R.string.enable_service), Toast.LENGTH_SHORT).show();
    }
}
