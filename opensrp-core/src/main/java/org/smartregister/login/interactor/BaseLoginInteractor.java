package org.smartregister.login.interactor;

import static org.smartregister.domain.LoginResponse.INVALID_GRANT;
import static org.smartregister.domain.LoginResponse.NO_INTERNET_CONNECTIVITY;
import static org.smartregister.domain.LoginResponse.UNAUTHORIZED;
import static org.smartregister.domain.LoginResponse.UNKNOWN_RESPONSE;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import androidx.annotation.VisibleForTesting;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.AllConstants;
import org.smartregister.CoreLibrary;
import org.smartregister.P2POptions;
import org.smartregister.R;
import org.smartregister.account.AccountAuthenticatorXml;
import org.smartregister.account.AccountHelper;
import org.smartregister.domain.LoginResponse;
import org.smartregister.domain.TimeStatus;
import org.smartregister.domain.jsonmapping.util.TeamLocation;
import org.smartregister.event.Listener;
import org.smartregister.job.P2pServiceJob;
import org.smartregister.job.PullUniqueIdsServiceJob;
import org.smartregister.job.SyncSettingsServiceJob;
import org.smartregister.login.task.LocalLoginTask;
import org.smartregister.login.task.RemoteLoginTask;
import org.smartregister.login.task.V1RemoteLoginTask;
import org.smartregister.multitenant.ResetAppHelper;
import org.smartregister.repository.AllSharedPreferences;
import org.smartregister.service.UserService;
import org.smartregister.sync.helper.ServerSettingsHelper;
import org.smartregister.util.NetworkUtils;
import org.smartregister.view.activity.ChangePasswordActivity;
import org.smartregister.view.activity.DrishtiApplication;
import org.smartregister.view.contract.BaseLoginContract;

import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import timber.log.Timber;

/**
 * Created by ndegwamartin on 26/06/2018.
 */
public abstract class BaseLoginInteractor implements BaseLoginContract.Interactor {

    private static final int MINIMUM_JOB_FLEX_VALUE = 5;
    private BaseLoginContract.Presenter mLoginPresenter;
    private RemoteLoginTask remoteLoginTask;
    private V1RemoteLoginTask v1RemoteLogin;
    private boolean isLocalLogin;

    private ResetAppHelper resetAppHelper;

    public BaseLoginInteractor(BaseLoginContract.Presenter loginPresenter) {
        this.mLoginPresenter = loginPresenter;
        resetAppHelper = new ResetAppHelper(DrishtiApplication.getInstance());
    }

    @Override
    public void onDestroy(boolean isChangingConfiguration) {
        if (!isChangingConfiguration) {
            mLoginPresenter = null;
        }
    }

    @Override
    public void login(WeakReference<BaseLoginContract.View> view, String userName, char[] password) {
        getLoginView().showProgress(true);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {

            isLocalLogin = !getSharedPreferences().fetchForceRemoteLogin(userName);
            org.smartregister.Context opensrpContext = CoreLibrary.getInstance().context();
            if (NetworkUtils.isNetworkAvailable() && (isRefreshTokenExpired(userName) || (opensrpContext.getAppProperties().getPropertyBoolean(AllConstants.PROPERTY.ALLOW_OFFLINE_LOGIN_WITH_INVALID_TOKEN)
                    && isLocalLogin
                    && HttpURLConnection.HTTP_UNAUTHORIZED == getSharedPreferences().getLastAuthenticationHttpStatus()))) {
                isLocalLogin = false;
            }

            getLoginView().getAppCompatActivity().runOnUiThread(() -> loginWithLocalFlag(view, isLocalLogin && getSharedPreferences().isRegisteredANM(userName), userName, password));

        });
    }

    @Override
    public void v1Login(WeakReference<BaseLoginContract.View> view, String userName, String password) {
        v1LoginWithLocalFlag(view, !getSharedPreferences().v1FetchForceRemoteLogin()
                && userName.equalsIgnoreCase(getSharedPreferences().fetchRegisteredANM()), userName, password);
    }

    public void v1LoginWithLocalFlag(WeakReference<BaseLoginContract.View> view, boolean localLogin, String userName, String password) {

        getLoginView().hideKeyboard();
        getLoginView().enableLoginButton(false);
        if (localLogin) {
            v1localLogin(view, userName, password);
        } else {
            v1RemoteLogin(userName, password);
        }

        Timber.i("Login result finished " + DateTime.now().toString());
    }

    @VisibleForTesting
    protected boolean isRefreshTokenExpired(String userName) {
        return !AccountHelper.isRefreshTokenValid(userName, CoreLibrary.getInstance().getAccountAuthenticatorXml().getAccountType());
    }

    public void loginWithLocalFlag(WeakReference<BaseLoginContract.View> view, boolean localLogin, String userName, char[] password) {

        getLoginView().hideKeyboard();
        getLoginView().enableLoginButton(false);
        if (localLogin) {
            localLogin(view, userName, password);
        } else {
            remoteLogin(userName, password, CoreLibrary.getInstance().getAccountAuthenticatorXml());
        }

        Timber.i("Login result finished " + DateTime.now());
    }

    private void v1localLogin(WeakReference<BaseLoginContract.View> view, String userName, String password) {
        getLoginView().enableLoginButton(true);
        boolean isAuthenticated = getUserService().v1isUserInValidGroup(userName, password);
        if (!isAuthenticated) {

            getLoginView().showErrorDialog(getApplicationContext().getResources().getString(R.string.unauthorized));

        } else if (isAuthenticated && (!AllConstants.TIME_CHECK || TimeStatus.OK.equals(getUserService().validateStoredServerTimeZone()))) {

            v1NavigateToHomePage(userName, password);

        } else {
            v1LoginWithLocalFlag(view, false, userName, password);
        }
    }

    private void localLogin(WeakReference<BaseLoginContract.View> view, String userName, char[] password) {
        getLoginView().enableLoginButton(true);

        new LocalLoginTask(view.get(), userName, password, isAuthenticated -> {

            if (!isAuthenticated) {

                getLoginView().showErrorDialog(getApplicationContext().getResources().getString(R.string.unauthorized));

            } else if (isAuthenticated && isValidTimecheck(getUserService().validateStoredServerTimeZone())) {

                navigateToHomePage(userName);

            } else {
                loginWithLocalFlag(view, false, userName, password);
            }


        }).execute();

    }

    private void v1NavigateToHomePage(String userName, String password) {

        getUserService().v1localLogin(userName, password);
        getLoginView().goToHome(false);

        CoreLibrary.getInstance().initP2pLibrary(userName);

        new Thread(new Runnable() {
            @Override
            public void run() {
                Timber.i("Starting DrishtiSyncScheduler " + DateTime.now().toString());

                scheduleJobsImmediately();

                Timber.i("Started DrishtiSyncScheduler " + DateTime.now().toString());

                CoreLibrary.getInstance().context().getUniqueIdRepository().releaseReservedIds();
            }
        }).start();
    }

    private void navigateToHomePage(String userName) {

        getUserService().localLoginWith(userName);

        if (mLoginPresenter != null) {
            getLoginView().goToHome(false);
        }

        CoreLibrary.getInstance().initP2pLibrary(userName);

        new Thread(() -> {
            Timber.i("Starting DrishtiSyncScheduler " + DateTime.now().toString());

            scheduleJobsImmediately();

            Timber.i("Started DrishtiSyncScheduler " + DateTime.now().toString());

            CoreLibrary.getInstance().context().getUniqueIdRepository().releaseReservedIds();
        }).start();
    }

    private void remoteLogin(final String userName, final char[] password, final AccountAuthenticatorXml accountAuthenticatorXml) {

        try {
            if (getSharedPreferences().fetchBaseURL("").isEmpty() && StringUtils.isNotBlank(this.getApplicationContext().getString(R.string.opensrp_url))) {
                getSharedPreferences().savePreference("DRISHTI_BASE_URL", getApplicationContext().getString(R.string.opensrp_url));
            }
            if (!getSharedPreferences().fetchBaseURL("").isEmpty()) {

                tryRemoteLogin(userName, password, accountAuthenticatorXml, loginResponse -> {

                    getLoginView().enableLoginButton(true);

                    if (loginResponse == LoginResponse.SUCCESS) {

                        String username = getUsername(userName, loginResponse);

                        //Capture the location of the user and store it in shared
                        Set<TeamLocation> userLocaltion;
                        TeamLocation currentUserLocation;
                        if (loginResponse.payload() != null){
                            userLocaltion = loginResponse.payload().team.locations;

                            TeamLocation[] tlocationArray = userLocaltion.toArray(new TeamLocation[1]);
                            currentUserLocation = tlocationArray[0];
                            CoreLibrary.getInstance().context().allSharedPreferences().saveUserLocalityName(username, currentUserLocation.name);
                        }

                        if (getUserService().isUserInPioneerGroup(username)) {

                            TimeStatus timeStatus = getUserService().validateDeviceTime(loginResponse.payload(), AllConstants.MAX_SERVER_TIME_DIFFERENCE);

                            if (isValidTimecheck(timeStatus)) {

                                postProcessRemoteLoginSuccess(username, loginResponse);

                            } else {

                                postProcessRemoteLoginServerTimeMismatch(timeStatus, loginResponse);
                            }
                        } else {

                            if (CoreLibrary.getInstance().getSyncConfiguration().clearDataOnNewTeamLogin()) {
                                getLoginView().showClearDataDialog((dialog, which) -> {

                                    dialog.dismiss();

                                    if (which == DialogInterface.BUTTON_POSITIVE) {
                                        resetAppHelper.startResetProcess(getLoginView().getAppCompatActivity(), () -> login(new WeakReference<>(getLoginView()), userName, mLoginPresenter.getPassword()));
                                    }
                                });
                            } else {
                                // Valid user from wrong group trying to log in
                                getLoginView().showErrorDialog(getApplicationContext().getString(R.string.unauthorized_group));
                            }

                        }
                    } else {
                        if (loginResponse == null) {
                            getLoginView().showErrorDialog(getApplicationContext().getString(R.string.remote_login_generic_error));
                        } else {
                            if (loginResponse == NO_INTERNET_CONNECTIVITY) {
                                getLoginView().showErrorDialog(getApplicationContext().getResources().getString(R.string.no_internet_connectivity));
                            } else if (loginResponse == UNKNOWN_RESPONSE) {
                                getLoginView().showErrorDialog(getApplicationContext().getResources().getString(R.string.unknown_response));
                            } else if (loginResponse == UNAUTHORIZED) {
                                getLoginView().showErrorDialog(getApplicationContext().getResources().getString(R.string.unauthorized));
                            } else if (loginResponse == INVALID_GRANT) {
                                String pwdResetEndpoint = loginResponse.getRawData() != null ? loginResponse.getRawData().optString(AccountHelper.CONFIGURATION_CONSTANTS.ISSUER_ENDPOINT_URL) : "";
                                showPasswordResetView(pwdResetEndpoint);
                            } else {
                                getLoginView().showErrorDialog(loginResponse.message());
                            }
                        }
                    }
                });
            } else {
                getLoginView().enableLoginButton(true);
                getLoginView().showErrorDialog(getApplicationContext().getString(R.string.remote_login_base_url_missing_error));
            }
        } catch (Exception e) {
            Timber.e(e);
            getLoginView().showErrorDialog(getApplicationContext().getString(R.string.remote_login_generic_error));
        }
    }


    private void remoteLoginWith(String userName, String password, LoginResponse loginResponse) {
        getUserService().v1RemoteLogin(userName, password, loginResponse.payload());
        processServerSettings(loginResponse);

        scheduleJobsPeriodically();
        scheduleJobsImmediately();

        CoreLibrary.getInstance().initP2pLibrary(userName);

        getLoginView().goToHome(true);
    }

    private void v1RemoteLogin(final String userName, final String password) {

        try {
            if (getSharedPreferences().fetchBaseURL("").isEmpty() && StringUtils.isNotBlank(this.getApplicationContext().getString(R.string.opensrp_url))) {
                getSharedPreferences().savePreference("DRISHTI_BASE_URL", getApplicationContext().getString(R.string.opensrp_url));
            }
            if (!getSharedPreferences().fetchBaseURL("").isEmpty()) {
                v1TryRemoteLogin(userName, password, new Listener<LoginResponse>() {

                    public void onEvent(LoginResponse loginResponse) {
                        getLoginView().enableLoginButton(true);
                        if (loginResponse == LoginResponse.SUCCESS) {
                            String username=loginResponse.payload()!=null && loginResponse.payload().user != null && StringUtils.isNotBlank(loginResponse.payload().user.getUsername())
                                    ? loginResponse.payload().user.getUsername() : userName;

                            //Capture the location of the user and store it in shared
                            Set<TeamLocation> userLocaltion;
                            TeamLocation currentUserLocation;
                            if (loginResponse.payload() != null){
                                userLocaltion = loginResponse.payload().team.locations;

                                TeamLocation[] tlocationArray = userLocaltion.toArray(new TeamLocation[1]);
                                currentUserLocation = tlocationArray[0];
                                CoreLibrary.getInstance().context().allSharedPreferences().saveUserLocalityName(username, currentUserLocation.name);
                            }

                            if (getUserService().isUserInPioneerGroup(username)) {
                                TimeStatus timeStatus = getUserService().validateDeviceTime(
                                        loginResponse.payload(), AllConstants.MAX_SERVER_TIME_DIFFERENCE
                                );
                                if (!AllConstants.TIME_CHECK || timeStatus.equals(TimeStatus.OK)) {

                                    remoteLoginWith(username, password, loginResponse);

                                } else {
                                    if (timeStatus.equals(TimeStatus.TIMEZONE_MISMATCH)) {
                                        TimeZone serverTimeZone = UserService.getServerTimeZone(loginResponse.payload());

                                        getLoginView().showErrorDialog(getApplicationContext().getString(timeStatus.getMessage(),
                                                serverTimeZone.getDisplayName()));
                                    } else {
                                        getLoginView().showErrorDialog(getApplicationContext().getString(timeStatus.getMessage()));
                                    }
                                }
                            } else {
                                // Valid user from wrong group trying to log in
                                getLoginView().showErrorDialog(getApplicationContext().getString(R.string.unauthorized_group));
                            }
                        } else {
                            if (loginResponse == null) {
                                getLoginView().showErrorDialog("Sorry, your loginWithLocalFlag failed. Please try again");
                            } else {
                                if (loginResponse == NO_INTERNET_CONNECTIVITY) {
                                    getLoginView().showErrorDialog(getApplicationContext().getResources().getString(R.string.no_internet_connectivity));
                                } else if (loginResponse == UNKNOWN_RESPONSE) {
                                    getLoginView().showErrorDialog(getApplicationContext().getResources().getString(R.string.unknown_response));
                                } else if (loginResponse == UNAUTHORIZED) {
                                    getLoginView().showErrorDialog(getApplicationContext().getResources().getString(R.string.unauthorized));
                                } else {
                                    getLoginView().showErrorDialog(loginResponse.message());
                                }
                            }
                        }
                    }
                });
            } else {
                getLoginView().enableLoginButton(true);
                getLoginView().showErrorDialog("OpenSRP Base URL is missing. Please add it in Setting and try again");
            }
        } catch (Exception e) {
            Timber.e(e);
            getLoginView().showErrorDialog("Error occurred trying to loginWithLocalFlag in. Please try again...");
        }
    }

    private void v1TryRemoteLogin(final String userName, final String password, final Listener<LoginResponse> afterLogincheck) {
        if (v1RemoteLogin != null && !v1RemoteLogin.isCancelled()) {
            v1RemoteLogin.cancel(true);
        }
        v1RemoteLogin = new V1RemoteLoginTask(getLoginView(), userName, password, afterLogincheck);
        v1RemoteLogin.execute();
    }

    private boolean isValidTimecheck(TimeStatus timeStatus) {
        return !CoreLibrary.getInstance().isTimecheckDisabled() || TimeStatus.OK.equals(timeStatus);
    }

    private void postProcessRemoteLoginServerTimeMismatch(TimeStatus timeStatus, LoginResponse loginResponse) {
        if (timeStatus.equals(TimeStatus.TIMEZONE_MISMATCH)) {

            TimeZone serverTimeZone = UserService.getServerTimeZone(loginResponse.payload());
            getLoginView().showErrorDialog(getApplicationContext().getString(timeStatus.getMessage(), serverTimeZone.getDisplayName()));

        } else {
            getLoginView().showErrorDialog(getApplicationContext().getString(timeStatus.getMessage()));
        }
    }

    private String getUsername(String userName, LoginResponse loginResponse) {
        return loginResponse.payload() != null && loginResponse.payload().user != null && StringUtils.isNotBlank(loginResponse.payload().user.getUsername())
                ? loginResponse.payload().user.getUsername() : userName;
    }

    private void tryRemoteLogin(final String userName, final char[] password, final AccountAuthenticatorXml accountAuthenticatorXml, final Listener<LoginResponse> afterLogincheck) {
        if (remoteLoginTask != null && !remoteLoginTask.isCancelled()) {
            remoteLoginTask.cancel(true);
        }
        remoteLoginTask = new RemoteLoginTask(getLoginView(), userName, password, accountAuthenticatorXml, afterLogincheck);
        remoteLoginTask.execute();
    }

    private void postProcessRemoteLoginSuccess(String userName, LoginResponse loginResponse) {
        getUserService().processLoginResponseDataForUser(userName, loginResponse.payload());
        processServerSettings(loginResponse);

        scheduleJobsPeriodically();
        scheduleJobsImmediately();

        CoreLibrary.getInstance().initP2pLibrary(userName);

        getLoginView().goToHome(true);
    }

    public Context getApplicationContext() {
        return getLoginView().getActivityContext();
    }

    public AllSharedPreferences getSharedPreferences() {
        return mLoginPresenter != null && mLoginPresenter.getOpenSRPContext() != null ? mLoginPresenter.getOpenSRPContext().allSharedPreferences() : null;
    }

    public BaseLoginContract.View getLoginView() {
        return mLoginPresenter != null ? mLoginPresenter.getLoginView() : null;
    }

    public UserService getUserService() {
        return CoreLibrary.getInstance().context().userService();
    }

    /**
     * Add all the metnods that should be scheduled remotely
     */
    protected abstract void scheduleJobsPeriodically();

    /**
     * Sync and pull unique ids are scheduleds by default.
     * Call super if you override this method.
     */
    protected void scheduleJobsImmediately() {
        P2POptions p2POptions = CoreLibrary.getInstance().getP2POptions();
        if (p2POptions != null && p2POptions.isEnableP2PLibrary()) {
            // Finish processing any unprocessed sync records here
            P2pServiceJob.scheduleJobImmediately(P2pServiceJob.TAG);
        }

        if (NetworkUtils.isNetworkAvailable()) {
            PullUniqueIdsServiceJob.scheduleJobImmediately(PullUniqueIdsServiceJob.TAG);
            SyncSettingsServiceJob.scheduleJobImmediately(SyncSettingsServiceJob.TAG);
        }
    }

    protected long getFlexValue(int value) {
        int minutes = MINIMUM_JOB_FLEX_VALUE;

        if (value > MINIMUM_JOB_FLEX_VALUE) {

            minutes = (int) Math.ceil(value / 3);
        }

        return minutes < MINIMUM_JOB_FLEX_VALUE ? MINIMUM_JOB_FLEX_VALUE : minutes;
    }

    //Always call super.processServerSettings( ) if you ever Override this
    protected void processServerSettings(LoginResponse loginResponse) {
        JSONObject data = loginResponse.getRawData();

        if (data != null) {
            try {

                JSONArray settings = data.has(AllConstants.PREF_KEY.SETTINGS) ? data.getJSONArray(AllConstants.PREF_KEY.SETTINGS) : null;

                if (settings != null && settings.length() > 0) {
                    ServerSettingsHelper.saveSetting(settings);
                }

            } catch (JSONException e) {
                Timber.e(e);

            }
        }
    }

    @Override
    public void showPasswordResetView(String passwordResetEndpoint) {
        Intent intent = new Intent(getLoginView().getActivityContext(), ChangePasswordActivity.class);
        intent.putExtra(AccountHelper.CONFIGURATION_CONSTANTS.ISSUER_ENDPOINT_URL, passwordResetEndpoint);
        getLoginView().getActivityContext().startActivity(intent);
    }
}
