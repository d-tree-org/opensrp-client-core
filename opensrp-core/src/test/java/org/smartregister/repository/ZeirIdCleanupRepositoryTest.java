package org.smartregister.repository;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import androidx.test.core.app.ApplicationProvider;

import net.sqlcipher.MatrixCursor;
import net.sqlcipher.database.SQLiteDatabase;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.reflect.Whitebox;
import org.robolectric.util.ReflectionHelpers;
import org.smartregister.BaseRobolectricUnitTest;
import org.smartregister.CoreLibrary;
import org.smartregister.repository.ZeirIdCleanupRepository;
import org.smartregister.view.activity.DrishtiApplication;

import java.util.Map;

/**
 * Created by ndegwamartin on 2019-12-02.
 */
public class ZeirIdCleanupRepositoryTest extends BaseRobolectricUnitTest {
    @Mock
    private Repository repository;

    @Mock
    private SQLiteDatabase sqLiteDatabase;

    private ZeirIdCleanupRepository zeirIdCleanupRepository;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        AllSharedPreferences allSharedPreferences = new AllSharedPreferences(getDefaultSharedPreferences(ApplicationProvider.getApplicationContext()));
        ReflectionHelpers.setField(CoreLibrary.getInstance().context(), "allSharedPreferences", allSharedPreferences);

        Whitebox.setInternalState(DrishtiApplication.getInstance(), "repository", repository);
        when(repository.getReadableDatabase()).thenReturn(sqLiteDatabase);
        when(repository.getWritableDatabase()).thenReturn(sqLiteDatabase);

        zeirIdCleanupRepository = new ZeirIdCleanupRepository();
    }

    @After
    public void tearDown() {
        Whitebox.setInternalState(DrishtiApplication.getInstance(), "repository", (Repository) null);
    }

    @Test
    public void getClientsWithDuplicateZeirIdsReturnsReturnsMap() {
        when(sqLiteDatabase.rawQuery(anyString(), any())).thenReturn(getZuplicateZeirIdsCursor());
        Map<String, String > duplicates = zeirIdCleanupRepository.getClientsWithDuplicateZeirIds();
        Assert.assertEquals(2, duplicates.size());
    }

    public MatrixCursor getZuplicateZeirIdsCursor() {
        MatrixCursor cursor = new MatrixCursor(new String[]{"baseEntityId", "zeir_id", "prev_zeir_id"});
        cursor.addRow(new Object[]{"1b6fca83-26d0-46d2-bfba-254de5c4424a", "11320561", null});
        cursor.addRow(new Object[]{"951f9ecc-50cf-4af5-ba8f-f2ce18a108b2", "11320561", "11320561"});
        return cursor;
    }

    @Test
    public void getClientsWithDuplicateOpenSrpIdsReturnsMap() {
        when(sqLiteDatabase.rawQuery(anyString(), any())).thenReturn(getDuplicateOpensrpIdCursor());
        Map<String, String> duplicateOpenSRPId = zeirIdCleanupRepository.getClientsWithDuplicateZeirIds();
        Assert.assertEquals(2, duplicateOpenSRPId.size());
    }

    public MatrixCursor getDuplicateOpensrpIdCursor() {
        MatrixCursor cursor = new MatrixCursor(new String[]{"baseEntityId", "opensrp_id", "prev_opensrp_id"});
        cursor.addRow(new Object[]{"bbae7f5c-8ba9-4e6d-86a7-9f6ee911c040", "00003jlj6", null});
        cursor.addRow(new Object[]{"19f79a8b-55d7-4042-981c-fb9fa470802a", "00003jlj5", "00003jlj5"});
        return cursor;
    }

}
