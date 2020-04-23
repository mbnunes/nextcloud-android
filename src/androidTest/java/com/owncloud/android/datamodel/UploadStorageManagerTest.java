package com.owncloud.android.datamodel;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;

import com.nextcloud.client.account.CurrentAccountProvider;
import com.owncloud.android.db.OCUpload;
import com.owncloud.android.db.UploadResult;
import com.owncloud.android.files.services.FileUploader;
import com.owncloud.android.operations.UploadFileOperation;

import net.bytebuddy.utility.RandomString;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Random;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import static org.junit.Assert.assertEquals;

/**
 * Created by JARP on 6/7/17.
 */

@RunWith(AndroidJUnit4.class)
@SmallTest
public class UploadStorageManagerTest {

    private Account[] Accounts;
    private UploadsStorageManager uploadsStorageManager;
    private CurrentAccountProvider currentAccountProvider = () -> null;

    @Before
    public void setUp() {
        Context instrumentationCtx = InstrumentationRegistry.getTargetContext();
        ContentResolver contentResolver = instrumentationCtx.getContentResolver();
        uploadsStorageManager = new UploadsStorageManager(currentAccountProvider, contentResolver);
        Accounts = new Account[]{new Account("A", "A"), new Account("B", "B")};
    }

    @Test
    public void testDeleteAllUploads() {
        // Clean
        for (Account account : Accounts) {
            uploadsStorageManager.removeAccountUploads(account);
        }
        int accountRowsA = 3;
        int accountRowsB = 4;
        insertUploads(Accounts[0], accountRowsA);
        insertUploads(Accounts[1], accountRowsB);

        assertEquals("Expected 4 removed uploads files",
                     4,
                     uploadsStorageManager.removeAccountUploads(Accounts[1]));
    }

    @Test
    public void largeTest() {
        int size = 1000;

        insertUploads(Accounts[0], size);

        assertEquals(size + 1, uploadsStorageManager.getAllStoredUploads().length);
    }

    @Test(expected = IllegalArgumentException.class)
    public void corruptedUpload() {
        OCUpload corruptUpload = new OCUpload(File.separator + "LocalPath",
                                              OCFile.PATH_SEPARATOR + "RemotePath",
                                              Accounts[0].name);

        corruptUpload.setLocalPath(null);

        uploadsStorageManager.storeUpload(corruptUpload);

        uploadsStorageManager.getAllStoredUploads();
    }

    private void insertUploads(Account account, int rowsToInsert) {
        for (int i = 0; i < rowsToInsert; i++) {
            uploadsStorageManager.storeUpload(createUpload(account));
        }
    }

    private OCUpload createUpload(Account account) {
        OCUpload upload = new OCUpload(File.separator + "LocalPath",
                                       OCFile.PATH_SEPARATOR + "RemotePath",
                                       account.name);

        upload.setFileSize(new Random().nextInt(20000));
        upload.setUploadStatus(UploadsStorageManager.UploadStatus.UPLOAD_IN_PROGRESS);
        upload.setLocalAction(2);
        upload.setNameCollisionPolicy(FileUploader.NameCollisionPolicy.ASK_USER);
        upload.setCreateRemoteFolder(false);
        upload.setUploadEndTimestamp(System.currentTimeMillis());
        upload.setLastResult(UploadResult.DELAYED_FOR_WIFI);
        upload.setCreatedBy(UploadFileOperation.CREATED_BY_USER);
        upload.setUseWifiOnly(true);
        upload.setWhileChargingOnly(false);
        upload.setFolderUnlockToken(RandomString.make(10));

        return upload;
    }

    @After
    public void tearDown() {
        for (Account account : Accounts) {
            uploadsStorageManager.removeAccountUploads(account);
        }
    }
}
