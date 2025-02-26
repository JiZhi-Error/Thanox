package github.tornaco.android.thanos.settings;

import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;

import com.elvishew.xlog.XLog;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import github.tornaco.android.thanos.BaseWithFabPreferenceFragmentCompat;
import github.tornaco.android.thanos.BuildConfig;
import github.tornaco.android.thanos.BuildProp;
import github.tornaco.android.thanos.R;
import github.tornaco.android.thanos.ThanosApp;
import github.tornaco.android.thanos.app.donate.DonateActivity;
import github.tornaco.android.thanos.app.donate.DonateSettings;
import github.tornaco.android.thanos.core.app.ThanosManager;
import github.tornaco.android.thanos.module.easteregg.paint.PlatLogoActivity;
import util.CollectionUtils;
import util.Consumer;

public class AboutSettingsFragment extends BaseWithFabPreferenceFragmentCompat {
    private int buildInfoClickTimes = 0;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.about_settings_pref, rootKey);
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    protected void onBindPreferences() {
        super.onBindPreferences();
        ThanosManager thanos = ThanosManager.from(getContext());

        // Build.
        findPreference(getString(R.string.key_build_info_app))
                .setSummary(
                        BuildConfig.VERSION_NAME
                                + "\n"
                                + BuildProp.THANOS_BUILD_FINGERPRINT
                                + "\n"
                                + BuildProp.THANOS_BUILD_DATE
                                + "\n"
                                + BuildProp.THANOS_BUILD_HOST);

        findPreference(getString(R.string.key_build_info_app))
                .setOnPreferenceClickListener(
                        preference -> {
                            PlatLogoActivity.start(getActivity());
                            Toast.makeText(
                                    getActivity(),
                                    "Thanox is build against Android 11, patching against "
                                            + thanos.getPatchingSource(),
                                    Toast.LENGTH_LONG)
                                    .show();
                            return true;
                        });
        if (thanos.isServiceInstalled()) {
            findPreference(getString(R.string.key_build_info_server))
                    .setSummary(thanos.getVersionName() + "\n" + thanos.fingerPrint());
            findPreference(getString(R.string.key_patch_info)).setSummary(thanos.getPatchingSource());
        } else {
            findPreference(getString(R.string.key_build_info_server))
                    .setSummary(R.string.status_not_active);
            findPreference(getString(R.string.key_patch_info)).setSummary("N/A");
        }

        findPreference(getString(R.string.key_build_info_server))
                .setOnPreferenceClickListener(preference -> {
                    buildInfoClickTimes += 1;
                    if (buildInfoClickTimes >= 10) {
                        buildInfoClickTimes = 0;
                        showBuildProp();
                    }
                    return false;
                });

        // About.
        Preference donatePref = findPreference(getString(R.string.key_donate));
        donatePref.setOnPreferenceClickListener(
                preference -> {
                    DonateActivity.start(getActivity());
                    return true;
                });

        if (DonateSettings.isActivated(getContext())) {
            donatePref.setSummary(R.string.module_donate_donated);
        }

        Preference licensePref = findPreference(getString(R.string.key_open_source_license));
        licensePref.setOnPreferenceClickListener(
                preference12 -> {
                    LicenseHelper.showLicenseDialog(getActivity());
                    return true;
                });

        findPreference(getString(R.string.key_contributors)).setSummary(BuildProp.THANOX_CONTRIBUTORS);
        donatePref.setVisible(ThanosApp.isPrc());

        findPreference(getString(R.string.key_email)).setSummary(BuildProp.THANOX_CONTACT_EMAIL);
    }

    private void showBuildProp() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            XLog.w("BuildProp" + System.lineSeparator() + String.join(System.lineSeparator(), getBuildProp()));
        }
    }

    private List<String> getBuildProp() {
        List<String> res = new ArrayList<>();
        CollectionUtils.consumeRemaining(BuildProp.class.getDeclaredFields()
                , new Consumer<Field>() {
                    @Override
                    public void accept(Field field) {
                        try {
                            res.add(field.getName() + "=" + field.get(null));
                        } catch (Throwable e) {
                            XLog.e("Reflect getBuildProp error", e);
                        }
                    }
                });
        return res;
    }
}
