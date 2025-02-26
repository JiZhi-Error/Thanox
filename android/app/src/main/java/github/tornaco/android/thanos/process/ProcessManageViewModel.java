package github.tornaco.android.thanos.process;

import android.app.Application;
import android.content.pm.PackageManager;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableArrayList;
import androidx.databinding.ObservableBoolean;
import androidx.databinding.ObservableField;
import androidx.databinding.ObservableList;
import androidx.lifecycle.AndroidViewModel;

import com.elvishew.xlog.XLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import github.tornaco.android.rhino.plugin.Verify;
import github.tornaco.android.thanos.core.app.ThanosManager;
import github.tornaco.android.thanos.core.pm.AppInfo;
import github.tornaco.android.thanos.core.process.ProcessRecord;
import github.tornaco.android.thanos.core.process.RunningState;
import github.tornaco.android.thanos.core.util.Rxs;
import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import lombok.Getter;
import rx2.android.schedulers.AndroidSchedulers;
import util.CollectionUtils;
import util.Consumer;

public class ProcessManageViewModel extends AndroidViewModel {
    @Getter
    private final ObservableBoolean isProcessNeedUpdate = new ObservableBoolean(false);
    @Getter
    private final ObservableBoolean isDataLoading = new ObservableBoolean(false);
    private final List<Disposable> disposables = new ArrayList<>();
    @Getter
    private final ObservableList<RunningState.MergedItem> mergedItems = new ObservableArrayList<>();
    @Getter
    private final ObservableField<RunningCategoryIndex> categoryIndex = new ObservableField<>(RunningCategoryIndex.Running);

    private ThanosManager thanos = ThanosManager.from(getApplication().getApplicationContext());

    public ProcessManageViewModel(@NonNull Application application) {
        super(application);
    }

    @Verify
    public void start() {
        loadProcess();
    }

    private void loadProcess() {
        if (!thanos.isServiceInstalled()) {
            return;
        }

        if (isDataLoading.get()) return;
        isDataLoading.set(true);

        RunningState runningState = RunningState.getInstance(getApplication());

        PackageManager pm = getApplication().getPackageManager();
        ThanosManager thanosManager = ThanosManager.from(getApplication());

        disposables.add(Single.create(
                (SingleOnSubscribe<ArrayList<RunningState.MergedItem>>) emitter -> {
                    try {
                        runningState.updateNow();
                        ArrayList<RunningState.MergedItem> all = new ArrayList<>();
                        ArrayList<RunningState.MergedItem> noneFiltered = new ArrayList<>();

                        if (categoryIndex.get() == RunningCategoryIndex.Running
                                || categoryIndex.get() == RunningCategoryIndex.All) {
                            noneFiltered.addAll(runningState.getCurrentMergedItems());
                        }
                        if (categoryIndex.get() == RunningCategoryIndex.Background
                                || categoryIndex.get() == RunningCategoryIndex.All) {
                            noneFiltered.addAll(runningState.getCurrentBackgroundItems());
                        }

                        CollectionUtils.consumeRemaining(noneFiltered, new Consumer<RunningState.MergedItem>() {
                            @Override
                            public void accept(RunningState.MergedItem mergedItem) {
                                if (mergedItem.mPackageInfo == null) {
                                    // Items for background processes don't normally load
                                    // their labels for performance reasons.  Do it now.
                                    if (mergedItem.mProcess != null) {
                                        mergedItem.mProcess.ensureLabel(pm);
                                        mergedItem.mPackageInfo = mergedItem.mProcess.mPackageInfo;
                                        mergedItem.mDisplayLabel = mergedItem.mProcess.mDisplayLabel;
                                    }
                                }
                                if (mergedItem.mPackageInfo == null) {
                                    XLog.e("mergedItem.mPackageInfo == null %s", mergedItem);
                                    return;
                                }
                                if (thanosManager.getPkgManager().isPkgInWhiteList(mergedItem.mPackageInfo.packageName)) {
                                    XLog.w("mergedItem skip in whitelist: %s", mergedItem.mPackageInfo);
                                    return;
                                }
                                mergedItem.appInfo = thanosManager.getPkgManager().getAppInfo(mergedItem.mPackageInfo.packageName);
                                boolean isIdle = thanosManager.getActivityManager().isPackageIdle(mergedItem.mPackageInfo.packageName);
                                mergedItem.appInfo.setIdle(isIdle);

                                XLog.v("mergedItem: mUser %s", mergedItem.mUser);
                                XLog.v("mergedItem: mUserId %s", mergedItem.mUserId);
                                all.add(mergedItem);
                            }
                        });

                        Collections.sort(all);

                        emitter.onSuccess(all);
                    } catch (Throwable e) {
                        emitter.onError(e);
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(out -> {
                    mergedItems.clear();
                    mergedItems.addAll(out);
                    isDataLoading.set(false);
                    isProcessNeedUpdate.set(false);
                }, Rxs.ON_ERROR_LOGGING));
    }

    private long getMemSize(List<ProcessRecord> processRecords)
            throws RemoteException {
        int[] pids = new int[processRecords.size()];
        for (int i = 0; i < processRecords.size(); i++) {
            pids[i] = (int) processRecords.get(i).getPid();
        }
        long[] pss = thanos.getActivityManager()
                .getProcessPss(pids);
        long size = 0L;
        for (long p : pss) {
            size += p * 1024L;
        }
        return size;
    }

    private void registerEventReceivers() {
    }

    private void unRegisterEventReceivers() {
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        CollectionUtils.consumeRemaining(disposables, Disposable::dispose);
        unRegisterEventReceivers();
    }

    @Verify
    void killApp(AppInfo appInfo) {
        ThanosManager.from(getApplication())
                .ifServiceInstalled(thanosManager -> {
                    thanosManager.getActivityManager()
                            .forceStopPackage(appInfo.getPkgName());

                    loadProcess();
                });
    }

    void setAppCategoryFilter(int index) {
        categoryIndex.set(RunningCategoryIndex.values()[index]);
        start();
    }
}
