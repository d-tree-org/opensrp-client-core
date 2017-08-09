package org.smartregister.view.activity;

import android.view.View;

import org.smartregister.AllConstants;
import org.smartregister.R;
import org.smartregister.adapter.SmartRegisterPaginatedAdapter;
import org.smartregister.domain.form.FieldOverrides;
import org.smartregister.provider.ANCSmartRegisterClientsProvider;
import org.smartregister.provider.SmartRegisterClientsProvider;
import org.smartregister.view.contract.SmartRegisterClient;
import org.smartregister.view.controller.ANCSmartRegisterController;
import org.smartregister.view.controller.VillageController;
import org.smartregister.view.dialog.ANCOverviewServiceMode;
import org.smartregister.view.dialog.ANCVisitsServiceMode;
import org.smartregister.view.dialog.AllClientsFilter;
import org.smartregister.view.dialog.BPLSort;
import org.smartregister.view.dialog.DeliveryPlanServiceMode;
import org.smartregister.view.dialog.DialogOption;
import org.smartregister.view.dialog.DialogOptionMapper;
import org.smartregister.view.dialog.DialogOptionModel;
import org.smartregister.view.dialog.EditOption;
import org.smartregister.view.dialog.EstimatedDateOfDeliverySort;
import org.smartregister.view.dialog.FilterOption;
import org.smartregister.view.dialog.HRPSort;
import org.smartregister.view.dialog.HbIFAServiceMode;
import org.smartregister.view.dialog.NameSort;
import org.smartregister.view.dialog.OpenFormOption;
import org.smartregister.view.dialog.OutOfAreaFilter;
import org.smartregister.view.dialog.SCSort;
import org.smartregister.view.dialog.STSort;
import org.smartregister.view.dialog.ServiceModeOption;
import org.smartregister.view.dialog.SortOption;
import org.smartregister.view.dialog.TTServiceMode;

import java.util.List;

import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.toArray;
import static java.util.Arrays.asList;
import static org.smartregister.AllConstants.FormNames.ANC_CLOSE;
import static org.smartregister.AllConstants.FormNames.ANC_INVESTIGATIONS;
import static org.smartregister.AllConstants.FormNames.ANC_VISIT;
import static org.smartregister.AllConstants.FormNames.DELIVERY_OUTCOME;
import static org.smartregister.AllConstants.FormNames.DELIVERY_PLAN;
import static org.smartregister.AllConstants.FormNames.HB_TEST;
import static org.smartregister.AllConstants.FormNames.IFA;
import static org.smartregister.AllConstants.FormNames.TT;

public class NativeANCSmartRegisterActivity extends SecuredNativeSmartRegisterActivity {

    public static final List<? extends DialogOption> DEFAULT_ANC_FILTER_OPTIONS = asList(
            new OutOfAreaFilter());
    private final ClientActionHandler clientActionHandler = new ClientActionHandler();
    private SmartRegisterClientsProvider clientProvider = null;
    private ANCSmartRegisterController controller;
    private VillageController villageController;
    private DialogOptionMapper dialogOptionMapper;

    @Override
    protected SmartRegisterPaginatedAdapter adapter() {
        return new SmartRegisterPaginatedAdapter(clientsProvider());
    }

    @Override
    protected SmartRegisterClientsProvider clientsProvider() {
        if (clientProvider == null) {
            clientProvider = new ANCSmartRegisterClientsProvider(this, clientActionHandler,
                    controller);
        }
        return clientProvider;
    }

    @Override
    protected DefaultOptionsProvider getDefaultOptionsProvider() {
        return new DefaultOptionsProvider() {

            @Override
            public ServiceModeOption serviceMode() {
                return new ANCOverviewServiceMode(clientsProvider());
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
                return getResources().getString(R.string.anc_label);
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
                return toArray(concat(DEFAULT_FILTER_OPTIONS, DEFAULT_ANC_FILTER_OPTIONS,
                        villageFilterOptions), DialogOption.class);
            }

            @Override
            public DialogOption[] serviceModeOptions() {
                return new DialogOption[]{new ANCOverviewServiceMode(
                        clientsProvider()), new ANCVisitsServiceMode(
                        clientsProvider()), new HbIFAServiceMode(
                        clientsProvider()), new TTServiceMode(
                        clientsProvider()), new DeliveryPlanServiceMode(clientsProvider())};
            }

            @Override
            public DialogOption[] sortingOptions() {
                return new DialogOption[]{new NameSort(), new EstimatedDateOfDeliverySort(), new
                        HRPSort(), new BPLSort(), new SCSort(), new STSort()};
            }

            @Override
            public String searchHint() {
                return getString(R.string.str_anc_search_hint);
            }
        };
    }

    private DialogOption[] getEditOptions() {
        return new DialogOption[]{new OpenFormOption(
                getString(R.string.str_register_anc_visit_form), ANC_VISIT,
                formController), new OpenFormOption(getString(R.string.str_register_hb_test_form),
                HB_TEST, formController), new OpenFormOption(
                getString(R.string.str_register_ifa_form), IFA, formController), new OpenFormOption(
                getString(R.string.str_register_tt_form), TT, formController), new OpenFormOption(
                getString(R.string.str_register_delivery_plan_form), DELIVERY_PLAN,
                formController), new OpenFormOption(
                getString(R.string.str_register_pnc_registration_form), DELIVERY_OUTCOME,
                formController), new OpenFormOption(
                getString(R.string.str_register_anc_investigations_form), ANC_INVESTIGATIONS,
                formController), new OpenFormOption(getString(R.string.str_register_anc_close_form),
                ANC_CLOSE, formController)};
    }

    @Override
    protected void onInitialization() {
        controller = new ANCSmartRegisterController(context().serviceProvidedService(),
                context().alertService(), context().allBeneficiaries(), context().listCache(),
                context().ancClientsCache());

        villageController = new VillageController(context().allEligibleCouples(),
                context().listCache(), context().villagesCache());

        dialogOptionMapper = new DialogOptionMapper();

        clientsProvider().onServiceModeSelected(new ANCOverviewServiceMode(clientsProvider()));
    }

    @Override
    public void startRegistration() {
        FieldOverrides fieldOverrides = new FieldOverrides(
                context().anmLocationController().getLocationJSON());
        startFormActivity(AllConstants.FormNames.ANC_REGISTRATION_OA, null,
                fieldOverrides.getJSONString());
    }

    private class ClientActionHandler implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            int i = view.getId();
            if (i == R.id.profile_info_layout) {
                showProfileView((SmartRegisterClient) view.getTag());

            } else if (i == R.id.btn_edit) {
                showFragmentDialog(new EditDialogOptionModel(), view.getTag());

            }
        }

        private void showProfileView(SmartRegisterClient client) {
            navigationController.startANC(client.entityId());
        }
    }

    private class EditDialogOptionModel implements DialogOptionModel {
        @Override
        public DialogOption[] getDialogOptions() {
            return getEditOptions();
        }

        @Override
        public void onDialogOptionSelection(DialogOption option, Object tag) {
            onEditSelection((EditOption) option, (SmartRegisterClient) tag);
        }
    }
}
