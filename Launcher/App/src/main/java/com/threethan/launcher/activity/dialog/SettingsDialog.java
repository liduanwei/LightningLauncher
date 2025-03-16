package com.threethan.launcher.activity.dialog;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.threethan.launcher.BuildConfig;
import com.threethan.launcher.R;
import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.activity.support.SettingsManager;
import com.threethan.launcher.data.Settings;
import com.threethan.launcher.helper.AppExt;
import com.threethan.launcher.helper.Compat;
import com.threethan.launcher.helper.PlatformExt;
import com.threethan.launcher.helper.PlaytimeHelper;
import com.threethan.launcher.helper.SettingsSaver;
import com.threethan.launcher.updater.LauncherUpdater;
import com.threethan.launchercore.util.App;
import com.threethan.launchercore.util.CustomDialog;
import com.threethan.launchercore.util.Platform;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/*
    SettingsDialog

    This class handles the main settings page, including setting/getting preferences, button states,
    and making sure settings are correctly applied to launcher activities
 */
@SuppressLint("UseSwitchCompatOrMaterialCode")

public class SettingsDialog extends BasicDialog<LauncherActivity> {
    private static boolean clearedLabel;
    private static boolean clearedIconCache;
    private static boolean clearedIconCustom;
    private static boolean clearedSort;
    private static boolean clearedGroups;
    private static AlertDialog instance;
    private static AlertDialog advancedInstance;
    private WeakReference<Switch> darkSwitchRef;

    /**
     * Creates a settings dialog. Make sure to call show()!
     *
     * @param launcherActivity Parent LauncherActivity instance
     */
    public SettingsDialog(LauncherActivity launcherActivity) {
        super(launcherActivity, R.layout.dialog_settings);
    }

    @SuppressLint("SetTextI18n")
    public AlertDialog show() {
        if (instance != null) try {
            instance.dismiss();
        } catch (Exception ignored) {
        }
        AlertDialog dialog;
        try {
            dialog = super.show();
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
        if (dialog == null) return null;
        instance = dialog;

        a.settingsVisible = true;

        dialog.setOnDismissListener(dialogInterface -> a.settingsVisible = false);
        View dismiss = dialog.findViewById(R.id.dismissButton);
        dismiss.setOnClickListener(view -> dialog.dismiss());
        dismiss.post(dismiss::requestFocus);

        ((TextView) dialog.findViewById(R.id.versionLabel)).setText('v' + BuildConfig.VERSION_NAME);

        // Addons
        View addonsButton = dialog.findViewById(R.id.addonsButton);
        addonsButton.setOnClickListener(view -> new AddonDialog(a).show());
        if (!a.dataStoreEditor.getBoolean(Settings.KEY_SEEN_ADDONS, false)) {
            View addonsButtonAttract = dialog.findViewById(R.id.addonsButtonAttract);
            addonsButtonAttract.setVisibility(View.VISIBLE);
            addonsButton.setVisibility(View.GONE);
            addonsButtonAttract.setOnClickListener(view -> {
                a.dataStoreEditor.putBoolean(Settings.KEY_SEEN_ADDONS, true);
                new AddonDialog(a).show();
            });
        }

        // Edit
        Switch editSwitch = dialog.findViewById(R.id.editModeSwitch);
        editSwitch.setOnClickListener(view1 -> {
            a.setEditMode(!a.isEditing());
            ArrayList<String> selectedGroups = a.settingsManager.getAppGroupsSorted(true);
            if (a.isEditing() && (selectedGroups.size() > 1)) {
                Set<String> selectFirst = new HashSet<>();
                selectFirst.add(selectedGroups.get(0));
                a.settingsManager.setSelectedGroups(selectFirst);
            }
        });

        View addWebsite = dialog.findViewById(R.id.addWebsiteButton);
        addWebsite.setOnClickListener(v -> a.addWebsite(a));

        // Group enabled state
        if (a.canEdit()) {
            // Can edit, show switch
            editSwitch.setChecked(a.isEditing());
            editSwitch.setVisibility(View.VISIBLE);
            addWebsite.setVisibility(View.GONE);
        } else {
            editSwitch.setVisibility(View.GONE);
            addWebsite.setVisibility(View.VISIBLE);
        }

        // Auto-Multitasking
        Switch multitaskSwitch = dialog.findViewById(R.id.multitaskSwitch);
        if (Platform.supportsNewVrOsMultiWindow()) {
            multitaskSwitch.setVisibility(View.VISIBLE);
            attachSwitchToSetting(multitaskSwitch, Settings.KEY_NEW_MULTITASK,
                    Settings.DEFAULT_NEW_MULTITASK, null, true);
        } else
            multitaskSwitch.setVisibility(View.GONE);

        // Update button
        if (LauncherUpdater.isAppUpdateAvailable()) {
            View skippedUpdateButton = dialog.findViewById(R.id.updateButton);
            skippedUpdateButton.setVisibility(View.VISIBLE);
            skippedUpdateButton.setOnClickListener((view) -> new LauncherUpdater(a).checkAppUpdateAndInstall());
        }

        // Wallpaper and style
        Switch darkSwitch = dialog.findViewById(R.id.darkModeSwitch);
        darkSwitchRef = new WeakReference<>(darkSwitch);
        attachSwitchToSetting(darkSwitch,
                Settings.KEY_DARK_MODE, Settings.DEFAULT_DARK_MODE,
                value -> {
                    LauncherActivity.darkMode = value;
                    a.launcherService.forEachActivity(LauncherActivity::resetAdapters);
                }, false);
        ImageView[] views = {
                dialog.findViewById(R.id.background0),
                dialog.findViewById(R.id.background1),
                dialog.findViewById(R.id.background2),
                dialog.findViewById(R.id.background3),
                dialog.findViewById(R.id.background4),
                dialog.findViewById(R.id.background5),
                dialog.findViewById(R.id.background6),
                dialog.findViewById(R.id.background7),
                dialog.findViewById(R.id.background8),
                dialog.findViewById(R.id.background9),
                dialog.findViewById(R.id.background_custom)
        };
        int background = a.dataStoreEditor.getInt(Settings.KEY_BACKGROUND,
                Platform.isTv()
                        ? Settings.DEFAULT_BACKGROUND_TV
                        : Settings.DEFAULT_BACKGROUND_VR);
        if (background < 0) background = views.length - 1;

        for (ImageView image : views) {
            image.setClipToOutline(true);
        }
        final int wallpaperWidth = 32;
        final int selectedWallpaperWidthPx = a.dp(455 + 20 - (wallpaperWidth + 4) * (views.length - 1) - wallpaperWidth);
        views[background].getLayoutParams().width = selectedWallpaperWidthPx;
        views[background].requestLayout();
        for (int i = 0; i < views.length; i++) {
            int index = i;
            views[i].setOnClickListener(view -> {
                int lastIndex = a.dataStoreEditor.getInt(Settings.KEY_BACKGROUND,
                        Platform.isTv()
                                ? Settings.DEFAULT_BACKGROUND_TV
                                : Settings.DEFAULT_BACKGROUND_VR);
                if (lastIndex >= SettingsManager.BACKGROUND_DRAWABLES.length || lastIndex < 0)
                    lastIndex = SettingsManager.BACKGROUND_DRAWABLES.length;
                ImageView last = views[lastIndex];

                if (index == views.length - 1) a.showFilePicker(LauncherActivity.FilePickerTarget.WALLPAPER);
                if (last == view) return;

                ValueAnimator viewAnimator = ValueAnimator.ofInt(view.getWidth(), selectedWallpaperWidthPx);
                viewAnimator.setDuration(250);
                viewAnimator.setInterpolator(new DecelerateInterpolator());
                viewAnimator.addUpdateListener(animation -> {
                    view.getLayoutParams().width = (int) animation.getAnimatedValue();
                    view.requestLayout();
                });
                viewAnimator.start();

                ValueAnimator lastAnimator = ValueAnimator.ofInt(last.getWidth(), a.dp(wallpaperWidth));
                lastAnimator.setDuration(250);
                lastAnimator.setInterpolator(new DecelerateInterpolator());
                lastAnimator.addUpdateListener(animation -> {
                    last.getLayoutParams().width = (int) animation.getAnimatedValue();
                    last.requestLayout();
                });
                lastAnimator.start();

                // Set the background
                a.setBackground(index);

                // Set if darkSwitch mode is switch was automatically en/disabled
                if (index != views.length - 1) darkSwitch.setChecked(LauncherActivity.darkMode);
            });
        }

        // Icons & Layout
        SeekBar scale = dialog.findViewById(R.id.scaleSeekBar);
        scale.setProgress(a.dataStoreEditor.getInt(Settings.KEY_SCALE, Settings.DEFAULT_SCALE));

        scale.post(() -> scale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int value, boolean b) {
                a.dataStoreEditor.putInt(Settings.KEY_SCALE, value);
                LauncherActivity.iconScale = value;
                a.refreshInterface();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                a.launcherService.forEachActivity(LauncherActivity::refreshInterface);
            }
        }));
        scale.setMax(Settings.MAX_SCALE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) scale.setMin(Settings.MIN_SCALE);

        SeekBar margin = dialog.findViewById(R.id.marginSeekBar);
        margin.setProgress(a.dataStoreEditor.getInt(Settings.KEY_MARGIN, Settings.DEFAULT_MARGIN));
        margin.post(() -> margin.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int value, boolean b) {
                a.dataStoreEditor.putInt(Settings.KEY_MARGIN, value);
                LauncherActivity.iconMargin = value;
                a.refreshAdapters();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                a.launcherService.forEachActivity(LauncherActivity::refreshInterface);
            }
        }));
        margin.setMax(40);

        // Advanced button
        dialog.findViewById(R.id.advancedSettingsButton).setOnClickListener(view -> showAdvancedSettings());
        return dialog;
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    public void showAdvancedSettings() {
        if (advancedInstance != null) try {
            advancedInstance.dismiss();
        } catch (Exception ignored) {
        }

        AlertDialog dialog = new BasicDialog<>(a, R.layout.dialog_settings_advanced).show();
        if (dialog == null) return;
        advancedInstance = dialog;

        dialog.findViewById(R.id.dismissButton).setOnClickListener(view -> dialog.dismiss());

        dialog.findViewById(R.id.questWindowSettings).setVisibility(Platform.isQuest() ? View.VISIBLE : View.GONE);
        if (Platform.isQuest()) {
            SeekBar alpha = dialog.findViewById(R.id.alphaSeekBar);
            alpha.setProgress(255 - a.dataStoreEditor.getInt(Settings.KEY_BACKGROUND_ALPHA, Settings.DEFAULT_ALPHA));
            alpha.post(() -> alpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int value, boolean b) {
                    a.dataStoreEditor.putInt(Settings.KEY_BACKGROUND_ALPHA, 255 - value);
                    // Automatically turn on dark mode if we're settings transparency above 50%
                    // (the user can turn it back on themselves after, if they want)
                    if (value > 128) {
                        try {
                            darkSwitchRef.get().setChecked(true);
                        } catch (Exception ignored) {
                        }
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    a.refreshBackground();
                }
            }));
        }

        // Advanced
        attachSwitchToSetting(dialog.findViewById(R.id.longPressEditSwitch),
                Settings.KEY_DETAILS_LONG_PRESS, Settings.DEFAULT_DETAILS_LONG_PRESS, null, true);

        if (Platform.isVr() && Platform.getVrOsVersion() < 69) {
            View defaultSettingsButton = dialog.findViewById(R.id.defaultLauncherSettingsButton);
            defaultSettingsButton.setOnClickListener((view) -> new CustomDialog.Builder(a)
                    .setTitle(R.string.warning)
                    .setMessage(R.string.set_default_launcher_message)
                    .setPositiveButton(R.string.understood, (d, w) -> {
                        final Intent intent = new Intent(android.provider.Settings.ACTION_HOME_SETTINGS);
                        intent.setPackage("com.android.permissioncontroller");
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        d.dismiss();
                        a.startActivity(intent);
                    })
                    .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                    .show());
        } else {
            dialog.findViewById(R.id.extraFeaturesTitle).setVisibility(View.GONE);
            dialog.findViewById(R.id.defaultLauncherSettingsButton).setVisibility(View.GONE);
        }
        attachSwitchToSetting(dialog.findViewById(R.id.showPlaytimesSwitch),
                Settings.KEY_SHOW_TIMES_BANNER, Settings.DEFAULT_SHOW_TIMES_BANNER,
                b -> a.launcherService.forEachActivity(LauncherActivity::refreshInterface), false);
        dialog.findViewById(R.id.openUsageSettings).setOnClickListener(v -> PlaytimeHelper.requestPermission());

        // Launch Section
        final Spinner defaultBrowserSpinner = dialog.findViewById(R.id.launchBrowserSpinner);

        final int defaultBrowserSelection = a.dataStoreEditor.getInt(
                Settings.KEY_DEFAULT_BROWSER,
                SettingsManager.getDefaultBrowser());
        initSpinner(defaultBrowserSpinner,
                Platform.isQuest()
                        ? R.array.advanced_launch_browsers_quest
                        : R.array.advanced_launch_browsers,
                p -> a.dataStoreEditor.putInt(Settings.KEY_DEFAULT_BROWSER, p),
                defaultBrowserSelection);

        Switch chainLaunchSwitch = dialog.findViewById(R.id.allowChainLaunchSwitch);
        attachSwitchToSetting(chainLaunchSwitch,
                Settings.KEY_ALLOW_CHAIN_LAUNCH, Settings.DEFAULT_ALLOW_CHAIN_LAUNCH);
        chainLaunchSwitch.setVisibility(Platform.supportsVrOsChainLaunch()
                ? View.VISIBLE : View.GONE);

        dialog.findViewById(R.id.fullyCloseButton).setOnClickListener(v -> Compat.restartFully());

        // Search settings
        attachSwitchToSetting(dialog.findViewById(R.id.searchWebSwitch),
                Settings.KEY_SEARCH_WEB, Settings.DEFAULT_SEARCH_WEB);
        attachSwitchToSetting(dialog.findViewById(R.id.searchHiddenSwitch),
                Settings.KEY_SEARCH_HIDDEN, Settings.DEFAULT_SEARCH_HIDDEN);

        // Save/load settings
        View loadSettings = dialog.findViewById(R.id.loadSettingsButton);
        loadSettings.setAlpha(SettingsSaver.canLoad(a) ? 1F : 0.5F);
        loadSettings.setOnClickListener((view) -> {
            if (SettingsSaver.canLoad(a)) {
                dialog.dismiss();
                SettingsSaver.load(a);
            } else {
                BasicDialog.toast("Failed to find file!");
            }
        });

        View saveSettings = dialog.findViewById(R.id.saveSettingsButton);
        saveSettings.setOnClickListener((view) -> {
            SettingsSaver.save(a);
            loadSettings.setAlpha(1F);
        });

        // Save/load settings
        View loadGroupings = dialog.findViewById(R.id.loadGroupingsButton);
        loadGroupings.setAlpha(SettingsSaver.canLoadSort(a) ? 1F : 0.5F);
        loadGroupings.setOnClickListener((view) -> {
            if (SettingsSaver.canLoadSort(a)) {
                dialog.dismiss();
                SettingsSaver.loadSort(a);
            } else {
                BasicDialog.toast("Failed to find file!");
            }
        });

        View saveGroupings = dialog.findViewById(R.id.saveGroupingsButton);
        saveGroupings.setOnClickListener((view) -> {
            SettingsSaver.saveSort(a);
            loadGroupings.setAlpha(1F);
        });


        // Banner mode
        final Map<App.Type, Switch> switchByType = new HashMap<>();
        switchByType.put(App.Type.PHONE, dialog.findViewById(R.id.bannerPhoneSwitch));
        switchByType.put(App.Type.VR, dialog.findViewById(R.id.bannerVrSwitch));
        switchByType.put(App.Type.TV, dialog.findViewById(R.id.bannerTvSwitch));
        switchByType.put(App.Type.PANEL, dialog.findViewById(R.id.bannerPanelSwitch));
        switchByType.put(App.Type.WEB, dialog.findViewById(R.id.bannerWebsiteSwitch));

        for (App.Type type : switchByType.keySet()) {
            if (PlatformExt.getSupportedAppTypes().contains(type)) {
                Objects.requireNonNull(switchByType.get(type)).setVisibility(View.VISIBLE);

                final Switch bSwitch = switchByType.get(type);
                if (bSwitch == null) continue;
                bSwitch.setChecked(AppExt.typeIsBanner(type));
                bSwitch.setOnCheckedChangeListener((switchView, value) -> {
                    SettingsManager.setTypeBanner(type, value);
                    a.launcherService.forEachActivity(LauncherActivity::resetAdapters);
                });
            } else {
                Objects.requireNonNull(switchByType.get(type)).setVisibility(View.GONE);
            }
        }

        // Names
        attachSwitchToSetting(dialog.findViewById(R.id.namesSquareSwitch),
                Settings.KEY_SHOW_NAMES_SQUARE, Settings.DEFAULT_SHOW_NAMES_SQUARE,
                v -> a.launcherService.forEachActivity(LauncherActivity::forceRefreshPackages),
                false);

        attachSwitchToSetting(dialog.findViewById(R.id.namesBannerSwitch),
                Settings.KEY_SHOW_NAMES_BANNER, Settings.DEFAULT_SHOW_NAMES_BANNER,
                v -> a.launcherService.forEachActivity(LauncherActivity::forceRefreshPackages),
                false);


        attachSwitchToSetting(dialog.findViewById(R.id.groupSwitch),
                Settings.KEY_GROUPS_ENABLED, Settings.DEFAULT_GROUPS_ENABLED,
                value -> {
                    new CustomDialog.Builder(a)
                            .setTitle(R.string.warning)
                            .setMessage(R.string.hidden_groups_message)
                            .show();
                    a.setEditMode(true);
                    a.setEditMode(false);
                }, false
        );

        // Clear buttons (limited to one use to prevent bugs due to spamming)
        clearedLabel = false;

        View clearLabel = dialog.findViewById(R.id.clearLabelButton);
        clearLabel.setOnClickListener(view -> {
            if (!clearedLabel) {
                Compat.clearLabels(a);
                clearLabel.setAlpha(0.5f);
                clearedLabel = true;
            }
        });
        View iconSettings = dialog.findViewById(R.id.iconSettingsButton);
        iconSettings.setOnClickListener(view -> showIconSettings(a));

        View groupSettings = dialog.findViewById(R.id.groupDefaultsInfoButton);
        groupSettings.setOnClickListener(view -> showGroupSettings());
    }


    /**
     * Attaches a toggle switch to a specific setting
     *
     * @param toggle  Switch ui element
     * @param setting Setting string key
     * @param def     Default setting value
     */
    private void attachSwitchToSetting(Switch toggle, String setting,
                                       boolean def) {
        attachSwitchToSetting(toggle, setting, def, null, false);
    }

    /**
     * Attaches a toggle switch to a specific setting
     *
     * @param toggle   Switch ui element
     * @param setting  Setting string key
     * @param def      Default setting value
     * @param onSwitch Consumes the new value when it is changed, after writing to the datastore
     * @param inverted If true, inverts the setting
     */
    private void attachSwitchToSetting(Switch toggle, String setting,
                                       boolean def, Consumer<Boolean> onSwitch, boolean inverted) {
        toggle.setChecked(inverted != a.dataStoreEditor.getBoolean(setting, def));
        toggle.setOnCheckedChangeListener((compoundButton, value) -> {
            a.dataStoreEditor.putBoolean(setting, inverted != value);
            if (onSwitch != null) onSwitch.accept(inverted != value);
            a.launcherService.forEachActivity(LauncherActivity::refreshInterface);
        });
    }

    private void showGroupSettings() {
        clearedSort = false;
        clearedGroups = false;

        AlertDialog dialog = new BasicDialog<>(a, R.layout.dialog_setting_reset_groups).show();
        if (dialog == null) return;

        View clearSort = dialog.findViewById(R.id.resortOnly);
        clearSort.setOnClickListener(view -> {
            if (!clearedSort) {
                Compat.clearSort(a);
                clearSort.setAlpha(0.5f);
                clearedSort = true;

                BasicDialog.toast(a.getText(R.string.default_groups_resort_only_toast_main),
                        a.getString(R.string.default_groups_resort_only_toast_bold),
                        false);
            }
        });

        StringBuilder builder = new StringBuilder();
        for (App.Type type : PlatformExt.getSupportedAppTypes()) {
            builder.append(AppExt.getTypeString(a, type))
                    .append(" : ")
                    .append(AppExt.getDefaultGroupFor(type))
                    .append("\n");
        }

        TextView info = dialog.findViewById(R.id.infoText);
        info.setText(a.getString(R.string.default_groups_info, builder.toString()));


        View clearDefaults = dialog.findViewById(R.id.resetGroups);
        clearDefaults.setOnClickListener(view -> {
            if (!clearedGroups) {
                Compat.resetDefaultGroups(a);
                clearDefaults.setAlpha(0.5f);
                clearedGroups = true;
                clearSort.setAlpha(0.5f);
                clearedSort = true;

                BasicDialog.toast(a.getString(R.string.default_groups_reset_groups_toast_main),
                        a.getString(R.string.default_groups_reset_groups_toast_bold),
                        false);

                StringBuilder builder2 = new StringBuilder();
                for (App.Type type : PlatformExt.getSupportedAppTypes()) {
                    builder2.append(AppExt.getTypeString(a, type))
                            .append(" : ")
                            .append(AppExt.getDefaultGroupFor(type))
                            .append("\n");
                }

                info.setText(a.getString(R.string.default_groups_info, builder2.toString()));
            }
        });

        dialog.findViewById(R.id.cancel).setOnClickListener(v -> dialog.dismiss());
    }

    public void showIconSettings(LauncherActivity a) {
        clearedIconCache = false;
        clearedIconCustom = false;

        AlertDialog dialog = new BasicDialog<>(a, R.layout.dialog_setting_reset_icons).show();
        if (dialog == null) return;

        View clearCache = dialog.findViewById(R.id.clearCache);
        clearCache.setOnClickListener(view -> {
            if (!clearedIconCache) {
                Compat.clearIconCache(a);
                clearCache.setAlpha(0.5f);
                clearedSort = true;

                BasicDialog.toast(a.getString(R.string.toast_cleared_icon_cache));
            }
        });

        View clearAll = dialog.findViewById(R.id.clearAll);
        clearAll.setOnClickListener(view -> {
            if (!clearedIconCustom) {
                Compat.clearIcons(a);
                clearAll.setAlpha(0.5f);
                clearedIconCustom = true;
                clearCache.setAlpha(0.5f);
                clearedIconCache = true;

                BasicDialog.toast(a.getString(R.string.toast_cleared_icon_all));
            }
        });

        dialog.findViewById(R.id.cancel).setOnClickListener(v -> dialog.dismiss());
    }


}
