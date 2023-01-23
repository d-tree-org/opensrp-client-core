package org.smartregister.view.activity;

import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.test.core.app.ApplicationProvider;

import com.google.gson.Gson;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowToast;
import org.robolectric.util.ReflectionHelpers;
import org.smartregister.AllConstants;
import org.smartregister.BaseRobolectricUnitTest;
import org.smartregister.Context;
import org.smartregister.CoreLibrary;
import org.smartregister.R;
import org.smartregister.TestP2pApplication;
import org.smartregister.broadcastreceivers.OpenSRPClientBroadCastReceiver;
import org.smartregister.commonregistry.CommonRepository;
import org.smartregister.commonregistry.CommonRepositoryInformationHolder;
import org.smartregister.customshadows.ShadowLocalBroadcastManager;
import org.smartregister.domain.ColumnDetails;
import org.smartregister.event.Event;
import org.smartregister.event.Listener;
import org.smartregister.service.AlertService;
import org.smartregister.util.JsonFormUtils;
import org.smartregister.util.Session;
import org.smartregister.view.customcontrols.ProcessingInProgressSnackbar;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Ephraim Kigamba - nek.eam@gmail.com on 14-07-2020.
 */

@Config(application = TestP2pApplication.class)
public class SecuredActivityTest extends BaseRobolectricUnitTest {

    private SecuredActivityImpl securedActivity;

    private ActivityController<SecuredActivityImpl> controller;

    @BeforeClass
    public static void resetCoreLibrary() {
        ReflectionHelpers.setStaticField(CoreLibrary.class, "instance", null);
    }

    @Before
    public void setUp() {
        Context.bindtypes = new ArrayList<>();
        ColumnDetails[] tableColumns = new ColumnDetails[]{
                ColumnDetails.builder().name(CommonRepository.BASE_ENTITY_ID_COLUMN).build(),
                ColumnDetails.builder().name(CommonRepository.Relational_Underscore_ID).build(),
        };
        CommonRepositoryInformationHolder bt = new CommonRepositoryInformationHolder("BINDTYPENAME", tableColumns);
        Context.bindtypes.add(bt);

        // Make sure the user is logged in
        Session session = ReflectionHelpers.getField(CoreLibrary.getInstance().context().userService(), "session");
        session.setPassword("".getBytes());
        session.start(360 * 60 * 1000);

        controller = Robolectric.buildActivity(SecuredActivityImpl.class);
        controller.create().start().resume();
        securedActivity = Mockito.spy(controller.get());
    }

    @After
    public void tearDown() throws Exception {
        // Revert to the previous state where the user is logged out
        Session session = ReflectionHelpers.getField(CoreLibrary.getInstance().context().userService(), "session");
        session.setPassword("");
        session.start(0);
        resetCoreLibrary();
    }

    @Test
    @Ignore("To Investigated: Exception loading theme")
    public void onCreateShouldCallOnCreationAndAddLogoutListener() {
        List<WeakReference<Listener<Boolean>>> listeners = ReflectionHelpers.getField(Event.ON_LOGOUT, "listeners");
        listeners.clear();

        controller = Robolectric.buildActivity(SecuredActivityImpl.class);
        SecuredActivityImpl spyActivity = Mockito.spy((SecuredActivityImpl) ReflectionHelpers.getField(controller, "component"));
        spyActivity.setTheme(R.style.Theme_AppCompat_NoActionBar);
        ReflectionHelpers.setField(controller, "component", spyActivity);

        AppCompatDelegate delegate = AppCompatDelegate.create(ApplicationProvider.getApplicationContext(), spyActivity, spyActivity);
        Mockito.doReturn(delegate).when(spyActivity).getDelegate();

        ActionBar actionBar = Mockito.mock(ActionBar.class);
        Mockito.doReturn(actionBar).when(spyActivity).getSupportActionBar();

        ReflectionHelpers.callInstanceMethod(Activity.class, spyActivity, "performCreate", from(Bundle.class, null));

        Mockito.verify(spyActivity).onCreation();
        listeners = ReflectionHelpers.getField(Event.ON_LOGOUT, "listeners");
        Assert.assertEquals(1, listeners.size());
    }

    @Test
    public void onOptionsItemSelectedShouldLogoutUserWhenLogoutIsClicked() {
        MenuItem menuItem = Mockito.mock(MenuItem.class);
        Mockito.doReturn(securedActivity.MENU_ITEM_LOGOUT).when(menuItem).getItemId();

        TestP2pApplication testP2pApplication = Mockito.spy((TestP2pApplication) securedActivity.getApplication());
        Mockito.doReturn(testP2pApplication).when(securedActivity).getApplication();

        Assert.assertFalse(securedActivity.onOptionsItemSelected(menuItem));

        Mockito.verify(testP2pApplication).logoutCurrentUser();
    }


    @Test
    public void onOptionsItemSelectedShouldSwitchLanguageWhenSwitchLangItemIsClicked() {
        MenuItem menuItem = Mockito.mock(MenuItem.class);
        Mockito.doReturn(R.id.switchLanguageMenuItem).when(menuItem).getItemId();

        Assert.assertEquals("en", CoreLibrary.getInstance().context().userService().getAllSharedPreferences().fetchLanguagePreference());

        Assert.assertFalse(securedActivity.onOptionsItemSelected(menuItem));

        Assert.assertEquals("kn", CoreLibrary.getInstance().context().userService().getAllSharedPreferences().fetchLanguagePreference());
        String toastMsg = ShadowToast.getTextOfLatestToast();
        Assert.assertEquals("Language preference set to Kannada.", toastMsg);
    }

    @Test
    public void onPauseShouldUnregisterReceivers() {
        ShadowLocalBroadcastManager shadowLocalBroadcastManager = Shadow.extract(LocalBroadcastManager.getInstance(ApplicationProvider.getApplicationContext()));
        Assert.assertEquals(1, shadowLocalBroadcastManager.getRegisteredBroadcastReceivers().size());

        securedActivity.onPause();

        Assert.assertNull(ReflectionHelpers.getField(securedActivity, "openSRPClientBroadCastReceiver"));
        Mockito.verify(securedActivity).removeProcessingInProgressSnackbar();
        Mockito.verify(securedActivity).unregisterReceiver(Mockito.any(OpenSRPClientBroadCastReceiver.class));

        Assert.assertEquals(0, shadowLocalBroadcastManager.getRegisteredBroadcastReceivers().size());
    }

    @Test
    public void onActivityResultShouldUpdateAlertStatusWhenFormHasEntityIdAndAlertStatusInMetadata() {
        Intent data = new Intent();
        String entityId = "09808234";
        String alertName = "bcg";

        HashMap<String, String> metadata = new HashMap<>();
        metadata.put("entityId", entityId);
        metadata.put("alertName", alertName);

        ReflectionHelpers.setField(securedActivity, "metaData", JsonFormUtils.gson.toJson(metadata));
        AlertService alertService = Mockito.spy(CoreLibrary.getInstance().context().alertService());
        Mockito.doNothing().when(alertService).changeAlertStatusToInProcess(entityId, alertName);
        ReflectionHelpers.setField(CoreLibrary.getInstance().context(), "alertService", alertService);

        securedActivity.onActivityResult(0, AllConstants.FORM_SUCCESSFULLY_SUBMITTED_RESULT_CODE, data);

        Mockito.verify(alertService).changeAlertStatusToInProcess(entityId, alertName);
    }

    @Test
    public void addFieldOverridesShouldPopulateIntentWithFieldOverridesWhenMetadataIsAvailable() {
        Intent intent = new Intent();
        HashMap<String, String> metadata = new HashMap<>();
        String fieldOverrideValue = "some other field override";
        metadata.put(AllConstants.FIELD_OVERRIDES_PARAM, fieldOverrideValue);

        ReflectionHelpers.setField(securedActivity, "metaData", new Gson().toJson(metadata));
        ReflectionHelpers.callInstanceMethod(securedActivity, "addFieldOverridesIfExist", from(Intent.class, intent));

        Assert.assertEquals(fieldOverrideValue, intent.getStringExtra(AllConstants.FIELD_OVERRIDES_PARAM));
    }

    @Test
    public void showProcessingInProgressSnackbarShouldCallShowSnackbarIfAlreadyCreated() {
        ProcessingInProgressSnackbar snackbar = Mockito.mock(ProcessingInProgressSnackbar.class);
        ReflectionHelpers.setField(securedActivity, "processingInProgressSnackbar", snackbar);

        securedActivity.showProcessingInProgressSnackbar(securedActivity, 0);

        Mockito.verify(snackbar).show();
    }

    /*
    public void showProcessingInProgressSnackbarWhenGivenMarginShouldCreateAndShowSnackbar() {
        securedActivity.showProcessingInProgressSnackbar(securedActivity, 0);

        ProcessingInProgressSnackbar snackbar = ReflectionHelpers.getField(securedActivity, "processingInProgressSnackbar");

        Assert.assertTrue(snackbar.isShown());
        Assert.assertEquals(BaseTransientBottomBar.LENGTH_INDEFINITE, snackbar.getDuration());
    }
     */

    @Test
    public void onStatusUpdateShouldCallShowProcessingSnackbar() {
        Mockito.doNothing().when(securedActivity).showProcessingInProgressBottomSnackbar(Mockito.any(AppCompatActivity.class));

        securedActivity.onStatusUpdate(true);

        Mockito.verify(securedActivity).showProcessingInProgressBottomSnackbar(securedActivity);
    }


    @Test
    public void onStatusUpdateShouldCallRemoveProcessingSnackbar() {
        Mockito.doNothing().when(securedActivity).removeProcessingInProgressSnackbar();

        securedActivity.onStatusUpdate(false);

        Mockito.verify(securedActivity).removeProcessingInProgressSnackbar();
    }

    @Test
    public void removeProcessingInProgressSnackbarShouldDismissSnackbarWhenSnackbarIsShowing() {
        ProcessingInProgressSnackbar snackbar = Mockito.mock(ProcessingInProgressSnackbar.class);
        ReflectionHelpers.setField(securedActivity, "processingInProgressSnackbar", snackbar);
        Mockito.doReturn(true).when(snackbar).isShown();

        securedActivity.removeProcessingInProgressSnackbar();

        Mockito.verify(snackbar).dismiss();
    }

    static class SecuredActivityImpl extends SecuredActivity {

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            setTheme(R.style.Theme_AppCompat_Light_DarkActionBar); //or just R.style.Theme_AppCompat
            super.onCreate(savedInstanceState);

        }

        @Override
        protected void onCreation() {
            // Do nothing
        }

        @Override
        protected void onResumption() {
            // Do nothing
        }
    }

}