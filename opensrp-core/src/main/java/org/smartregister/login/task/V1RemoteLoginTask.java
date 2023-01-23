package org.smartregister.login.task;

import static org.smartregister.domain.LoginResponse.CUSTOM_SERVER_RESPONSE;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountsException;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.AllConstants;
import org.smartregister.Context;
import org.smartregister.CoreLibrary;
import org.smartregister.R;
import org.smartregister.account.AccountAuthenticatorXml;
import org.smartregister.account.AccountConfiguration;
import org.smartregister.account.AccountError;
import org.smartregister.account.AccountHelper;
import org.smartregister.account.AccountResponse;
import org.smartregister.domain.LoginResponse;
import org.smartregister.domain.jsonmapping.User;
import org.smartregister.event.Listener;
import org.smartregister.repository.AllSharedPreferences;
import org.smartregister.sync.helper.SyncSettingsServiceHelper;
import org.smartregister.util.EasyMap;
import org.smartregister.util.Utils;
import org.smartregister.view.contract.BaseLoginContract;

import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import timber.log.Timber;

/**
 * Created by Kassim Sheghembe on 2022-12-14
 */
public class V1RemoteLoginTask {

    private final String mUsername;
    private final String mPassword;
    private final Listener<LoginResponse> afterLoginCheck;
    private boolean cancelled;
    private BaseLoginContract.View mLoginView;

    public V1RemoteLoginTask(BaseLoginContract.View loginView, String username, String password, Listener<LoginResponse> afterLoginCheck) {
        mLoginView = loginView;
        mUsername = username;
        mPassword = password;
        this.afterLoginCheck = afterLoginCheck;
    }

    public static Context getOpenSRPContext() {
        return CoreLibrary.getInstance().context();
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel(boolean cancelled) {
        this.cancelled = cancelled;
        mLoginView.showProgress(!cancelled);
    }

    public void execute() {

        mLoginView.showProgress(true);

        ExecutorService executorService = Executors.newSingleThreadExecutor();

        executorService.execute(() -> {

            LoginResponse loginResponse = doInBackGroundOldImpl();

            mLoginView.getAppCompatActivity().runOnUiThread(() -> {

                mLoginView.showProgress(false);
                afterLoginCheck.onEvent(loginResponse);

            });

        });

    }

    protected LoginResponse doInBackGroundOldImpl() {
        LoginResponse loginResponse = getOpenSRPContext().userService().v1IsValidRemoteLogin(mUsername, mPassword);

        return loginResponse;
    }

    protected JSONArray pullSetting(SyncSettingsServiceHelper syncSettingsServiceHelper, LoginResponse loginResponse, String accessToken) {
        JSONArray settings = new JSONArray();
        try {
            settings = syncSettingsServiceHelper.pullSettingsFromServer(Utils.getFilterValue(loginResponse, CoreLibrary.getInstance().getSyncConfiguration().getSyncFilterParam()), accessToken);
        } catch (JSONException e) {
            Timber.e(e);
        }

        return settings;
    }
}
