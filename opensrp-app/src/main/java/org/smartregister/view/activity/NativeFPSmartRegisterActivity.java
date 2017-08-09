package org.smartregister.view.activity;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.view.View;

import org.smartregister.AllConstants;
import org.smartregister.R;
import org.smartregister.adapter.SmartRegisterPaginatedAdapter;
import org.smartregister.domain.form.FieldOverrides;
import org.smartregister.provider.FPSmartRegisterClientsProvider;
import org.smartregister.provider.SmartRegisterClientsProvider;
import org.smartregister.view.contract.FPClient;
import org.smartregister.view.contract.SmartRegisterClient;
import org.smartregister.view.controller.FPSmartRegisterController;
import org.smartregister.view.controller.VillageController;
import org.smartregister.view.dialog.AllClientsFilter;
import org.smartregister.view.dialog.BPLSort;
import org.smartregister.view.dialog.DialogOption;
import org.smartregister.view.dialog.DialogOptionMapper;
import org.smartregister.view.dialog.DialogOptionModel;
import org.smartregister.view.dialog.ECNumberSort;
import org.smartregister.view.dialog.EditOption;
import org.smartregister.view.dialog.FPAllMethodsServiceMode;
import org.smartregister.view.dialog.FPCondomServiceMode;
import org.smartregister.view.dialog.FPDMPAServiceMode;
import org.smartregister.view.dialog.FPDialogOptionModel;
import org.smartregister.view.dialog.FPFemaleSterilizationServiceMode;
import org.smartregister.view.dialog.FPIUCDServiceMode;
import org.smartregister.view.dialog.FPMaleSterilizationServiceMode;
import org.smartregister.view.dialog.FPOCPServiceMode;
import org.smartregister.view.dialog.FPOthersServiceMode;
import org.smartregister.view.dialog.FPPrioritizationAllECServiceMode;
import org.smartregister.view.dialog.FPPrioritizationHighPriorityServiceMode;
import org.smartregister.view.dialog.FPPrioritizationOneChildrenServiceMode;
import org.smartregister.view.dialog.FPPrioritizationTwoPlusChildrenServiceMode;
import org.smartregister.view.dialog.FPSmartRegisterDialogFragment;
import org.smartregister.view.dialog.FilterOption;
import org.smartregister.view.dialog.HighPrioritySort;
import org.smartregister.view.dialog.NameSort;
import org.smartregister.view.dialog.OpenFormOption;
import org.smartregister.view.dialog.SCSort;
import org.smartregister.view.dialog.STSort;
import org.smartregister.view.dialog.ServiceModeOption;
import org.smartregister.view.dialog.SortOption;

import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.toArray;
import static org.smartregister.AllConstants.FormNames.EC_REGISTRATION;
import static org.smartregister.AllConstants.FormNames.FP_CHANGE;
import static org.smartregister.AllConstants.FormNames.FP_COMPLICATIONS;
import static org.smartregister.AllConstants.FormNames.RECORD_ECPS;

public class NativeFPSmartRegisterActivity extends SecuredNativeSmartRegisterActivity {

    private static final String DIALOG_TAG = "dialog";
    private static int CHECKED_TAB_ID = R.id.rb_fp_method;
    private final ClientActionHandler clientActionHandler = new ClientActionHandler();
    FPSmartRegisterDialogFragment.onSelectedListener listener = new FPSmartRegisterDialogFragment
            .onSelectedListener() {
        @Override
        public void onSelected(int id) {
            CHECKED_TAB_ID = id;
        }
    };
    private SmartRegisterClientsProvider clientProvider = null;
    private FPSmartRegisterController controller;
    private VillageController villageController;
    private DialogOptionMapper dialogOptionMapper;

    @Override
    protected SmartRegisterPaginatedAdapter adapter() {
        return new SmartRegisterPaginatedAdapter(clientsProvider());
    }

    @Override
    protected DefaultOptionsProvider getDefaultOptionsProvider() {
        return new DefaultOptionsProvider() {

            @Override
            public ServiceModeOption serviceMode() {
                return new FPAllMethodsServiceMode(clientsProvider());
            }

            @Override
            public FilterOption villageFilter() {
                return new AllClientsFilter();
            }

            @Override
            public SortOption sortOption() {
                return new NameSort();
            }

            @Override
            public String nameInShortFormForTitle() {
                return getResources().getString(R.string.fp_register_title_in_short);
            }
        };
    }

    @Override
    protected NavBarOptionsProvider getNavBarOptionsProvider() {
        return new NavBarOptionsProvider() {

            @Override
            public DialogOption[] filterOptions() {
                Iterable<? extends DialogOption> villageFilterOptions = dialogOptionMapper
                        .mapToVillageFilterOptions(villageController.getVillages());
                return toArray(concat(DEFAULT_FILTER_OPTIONS, villageFilterOptions),
                        DialogOption.class);
            }

            @Override
            public DialogOption[] serviceModeOptions() {

                return new DialogOption[]{new FPAllMethodsServiceMode(
                        clientsProvider()), new FPCondomServiceMode(
                        clientsProvider()), new FPDMPAServiceMode(
                        clientsProvider()), new FPIUCDServiceMode(
                        clientsProvider()), new FPOCPServiceMode(
                        clientsProvider()), new FPFemaleSterilizationServiceMode(
                        clientsProvider()), new FPMaleSterilizationServiceMode(
                        clientsProvider()), new FPOthersServiceMode(clientsProvider())};
            }

            @Override
            public DialogOption[] sortingOptions() {
                return new DialogOption[]{new NameSort(), new ECNumberSort(), new
                        HighPrioritySort(), new BPLSort(), new SCSort(), new STSort()};
            }

            @Override
            public String searchHint() {
                return getString(R.string.str_fp_search_hint);
            }
        };
    }

    @Override
    protected SmartRegisterClientsProvider clientsProvider() {
        if (clientProvider == null) {
            clientProvider = new FPSmartRegisterClientsProvider(this, clientActionHandler,
                    controller);
        }
        return clientProvider;
    }

    private DialogOption[] getUpdateOptions() {
        return new DialogOption[]{new OpenFormOption(getString(R.string.str_fp_change_form),
                FP_CHANGE, formController), new OpenFormOption(
                getString(R.string.str_record_ecp_form), RECORD_ECPS, formController),};
    }

    @Override
    protected void onInitialization() {
        controller = new FPSmartRegisterController(context().allEligibleCouples(),
                context().allBeneficiaries(), context().alertService(), context().listCache(),
                context().fpClientsCache());
        villageController = new VillageController(context().allEligibleCouples(),
                context().listCache(), context().villagesCache());
        dialogOptionMapper = new DialogOptionMapper();
        clientsProvider().onServiceModeSelected(new FPAllMethodsServiceMode(clientsProvider()));
    }

    @Override
    public void setupViews() {
        super.setupViews();

    }

    @Override
    public void startRegistration() {
        FieldOverrides fieldOverrides = new FieldOverrides(
                context().anmLocationController().getLocationJSON());
        startFormActivity(EC_REGISTRATION, null, fieldOverrides.getJSONString());
    }

    @Override
    public void showFragmentDialog(DialogOptionModel dialogOptionModel, Object tag) {
        if (dialogOptionModel.getDialogOptions().length <= 0) {
            return;
        }

        if (!(dialogOptionModel instanceof ServiceModeDialogOptionModel)) {
            NativeFPSmartRegisterActivity.super.showFragmentDialog(dialogOptionModel, tag);
            return;
        }

        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment prev = getFragmentManager().findFragmentByTag(DIALOG_TAG);
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);

        dialogOptionModel = new FPServiceModeDialogOptionModel()
                .cloneDialogOptionsWith(dialogOptionModel);
        FPSmartRegisterDialogFragment fpSmartRegisterDialogFragment = FPSmartRegisterDialogFragment
                .newInstance(this, dialogOptionModel, tag);
        fpSmartRegisterDialogFragment.setSelectedListener(listener);
        fpSmartRegisterDialogFragment.setArguments(setAndReturnTabSelectionBundle());
        fpSmartRegisterDialogFragment.show(ft, DIALOG_TAG);

    }

    private Bundle setAndReturnTabSelectionBundle() {
        Bundle bundle = new Bundle();
        bundle.putInt(AllConstants.FP_DIALOG_TAB_SELECTION, CHECKED_TAB_ID);
        return bundle;
    }

    private class ClientActionHandler implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            int i = view.getId();
            if (i == R.id.profile_info_layout) {
                showProfileView((FPClient) view.getTag());

            } else if (i == R.id.btn_fp_method_update) {
                NativeFPSmartRegisterActivity.super
                        .showFragmentDialog(new UpdateDialogOptionModel(), view.getTag());

            } else if (i == R.id.btn_side_effects) {
                SmartRegisterClient fpClient = (SmartRegisterClient) view.getTag();
                startFormActivity(FP_COMPLICATIONS, fpClient.entityId(), null);

            } else if (i == R.id.lyt_fp_add) {
                NativeFPSmartRegisterActivity.super
                        .showFragmentDialog(new UpdateDialogOptionModel(), view.getTag());

            } else if (i == R.id.lyt_fp_videos) {
                navigationController.startVideos();

            }
        }

        private void showProfileView(FPClient client) {
            navigationController.startEC(client.entityId());
        }
    }

    private class UpdateDialogOptionModel implements DialogOptionModel {
        @Override
        public DialogOption[] getDialogOptions() {
            return getUpdateOptions();
        }

        @Override
        public void onDialogOptionSelection(DialogOption option, Object tag) {
            onEditSelection((EditOption) option, (SmartRegisterClient) tag);
        }
    }

    private class FPServiceModeDialogOptionModel implements FPDialogOptionModel {
        private DialogOption[] parentDialogOption;

        @Override
        public DialogOption[] getPrioritizationDialogOptions() {
            return new DialogOption[]{new FPPrioritizationAllECServiceMode(
                    clientsProvider()), new FPPrioritizationHighPriorityServiceMode(
                    clientsProvider()), new FPPrioritizationTwoPlusChildrenServiceMode(
                    clientsProvider()), new FPPrioritizationOneChildrenServiceMode(
                    clientsProvider()),};
        }

        @Override
        public DialogOption[] getDialogOptions() {
            return parentDialogOption;
        }

        @Override
        public void onDialogOptionSelection(DialogOption option, Object tag) {
            NativeFPSmartRegisterActivity.super.onServiceModeSelection((ServiceModeOption) option);
        }

        public FPDialogOptionModel cloneDialogOptionsWith(DialogOptionModel dialogOptionModel) {
            this.parentDialogOption = dialogOptionModel.getDialogOptions();
            return this;
        }
    }

}
