/*
 * Copyright (C) 2012 Dominik Schürmann <dominik@dominikschuermann.de>
 * Copyright (C) 2010 Thialfihar <thi@thialfihar.org>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sufficientlysecure.keychain.ui;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.regex.Matcher;

import org.openintents.openpgp.OpenPgpSignatureResult;
import org.spongycastle.openpgp.PGPPublicKeyRing;
import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.Id;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.helper.ActionBarHelper;
import org.sufficientlysecure.keychain.helper.FileHelper;
import org.sufficientlysecure.keychain.pgp.PgpDecryptVerifyResult;
import org.sufficientlysecure.keychain.pgp.PgpHelper;
import org.sufficientlysecure.keychain.pgp.PgpKeyHelper;
import org.sufficientlysecure.keychain.pgp.PgpDecryptVerify;
import org.sufficientlysecure.keychain.pgp.exception.NoAsymmetricEncryptionException;
import org.sufficientlysecure.keychain.pgp.exception.PgpGeneralException;
import org.sufficientlysecure.keychain.provider.ProviderHelper;
import org.sufficientlysecure.keychain.service.KeychainIntentService;
import org.sufficientlysecure.keychain.service.KeychainIntentServiceHandler;
import org.sufficientlysecure.keychain.service.PassphraseCacheService;
import org.sufficientlysecure.keychain.ui.adapter.DecryptActivityPagerAdapter;
import org.sufficientlysecure.keychain.ui.dialog.DeleteFileDialogFragment;
import org.sufficientlysecure.keychain.ui.dialog.FileDialogFragment;
import org.sufficientlysecure.keychain.ui.dialog.PassphraseDialogFragment;
import org.sufficientlysecure.keychain.util.Log;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.AnimationUtils;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.beardedhen.androidbootstrap.BootstrapButton;
import com.devspark.appmsg.AppMsg;

@SuppressLint("NewApi")
public class DecryptActivity extends DrawerActivity implements  DecryptFileFragment.DecryptionFunctions{

    /* Intents */
    // without permissiong
    public static final String ACTION_DECRYPT = Constants.INTENT_PREFIX + "DECRYPT";

    /* EXTRA keys for input */
    public static final String EXTRA_TEXT = "text";
    public static final String FRAGMENT_MESSAGE = "message";
    public static final String FRAGMENT_FILE = "file";
    public static final String FRAGMENT_BUNDLE_ACTION = "ACTION";
    public static final String FRAGMENT_BUNDLE_TYPE = "TYPE";
    public static final String FRAGMENT_BUNDLE_EXTRATEXT = "EXTRATEXT";
    public static final String FRAGMENT_BUNDLE_URI = "URI";
    public static final int FRAGMENT_FILE_POSITION = 1;
    public static final int FRAGMENT_MESSAGE_POSITION = 0;
    private static final int RESULT_CODE_LOOKUP_KEY = 0x00007006;
    private static final int RESULT_CODE_FILE = 0x00007003;

    private long mSignatureKeyId = 0;

    private boolean mReturnResult = false;

    // TODO: replace signed only checks with something more intelligent
    // PgpDecryptVerify should handle all automatically!!!
    private boolean mSignedOnly = false;
    private boolean mAssumeSymmetricEncryption = false;




    private int mDecryptTarget;

    private String mInputFilename = null;
    private String mOutputFilename = null;

    private Uri mContentUri = null;
    private boolean mReturnBinary = false;

    private long mSecretKeyId = Id.key.none;

    private FileDialogFragment mFileDialog;

    private boolean mDecryptImmediately = false;

    private BootstrapButton mDecryptButton;

    private DecryptActivityPagerAdapter pager_adapter;
    private ActionBar mActionBar;
    private ViewPager decrypt_pager;


    private void initView() {
        decrypt_pager = (ViewPager)findViewById(R.id.decrypt_pager);
        pager_adapter = new DecryptActivityPagerAdapter(this, decrypt_pager);
        mActionBar = getSupportActionBar();
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        mActionBar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
        decrypt_pager.setAdapter(pager_adapter);
        //Dont Change the order. Pager Adapter settings are linked to it.
        pager_adapter.addTab(mActionBar.newTab().setText("Decrypt Message"), DecryptMessageFragment.
                class, null, FRAGMENT_MESSAGE, FRAGMENT_MESSAGE_POSITION);
        pager_adapter.addTab(mActionBar.newTab().setText("Decrypt File"),
                DecryptFileFragment.class, null, FRAGMENT_FILE, FRAGMENT_FILE_POSITION);


    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.decrypt_activity);

        // set actionbar without home button if called from another app
        ActionBarHelper.setBackButton(this);

        initView();

        setupDrawerNavigation(savedInstanceState);
        // Handle intent actions
        Intent intent = getIntent();
        if(intent != null) {
            handleActions(getIntent());
        }

    }

    public void mSignatureLayout_OnClick() {

        PGPPublicKeyRing key = ProviderHelper.getPGPPublicKeyRingByKeyId(
                DecryptActivity.this, mSignatureKeyId);
        if (key != null) {
            Intent intent = new Intent(DecryptActivity.this, ImportKeysActivity.class);
            intent.setAction(ImportKeysActivity.ACTION_IMPORT_KEY_FROM_KEYSERVER);
            intent.putExtra(ImportKeysActivity.EXTRA_KEY_ID, mSignatureKeyId);
            startActivity(intent);

        }
    }

    /**
     * Handles all actions with this intent
     *
     * @param intent
     */
    private void handleActions(Intent intent) {
        String action = intent.getAction();
        Bundle extras = intent.getExtras();
        String type = intent.getType();
        String extra_text = intent.getStringExtra(Intent.EXTRA_TEXT);
        Uri uri = intent.getData();
        if(extras == null){
            extras = new Bundle();
        }
        extras.putString(FRAGMENT_BUNDLE_TYPE, type);
        extras.putString(FRAGMENT_BUNDLE_ACTION, action);
        extras.putParcelable(FRAGMENT_BUNDLE_URI, uri);
        extras.putString(FRAGMENT_BUNDLE_EXTRATEXT, extra_text);
        //If type == "text/plain" pack the intents and send it to message fragment.
        try {
            if (type.equals("text/plain")) {
                pager_adapter.getIntentFromActivity(extras, FRAGMENT_MESSAGE);
            } else {
                pager_adapter.getIntentFromActivity(extras, FRAGMENT_FILE);
            }
        }
        catch(Exception e){

        }













        /*
         * Android's Action

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            // When sending to Keychain Decrypt via share menu
            if ("text/plain".equals(type)) {
                // Plain text
                String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (sharedText != null) {
                    // handle like normal text decryption, override action and extras to later
                    // executeServiceMethod ACTION_DECRYPT in main actions
                    extras.putString(EXTRA_TEXT, sharedText);
                    action = ACTION_DECRYPT;
                }
            } else {
                // Binary via content provider (could also be files)
                // override uri to get stream from send
                uri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
                action = ACTION_DECRYPT;
            }
        } else if (Intent.ACTION_VIEW.equals(action)) {
            // Android's Action when opening file associated to Keychain (see AndroidManifest.xml)

            // override action
            action = ACTION_DECRYPT;
        }

        String textData = extras.getString(EXTRA_TEXT);

        /**
         * Main Actions

        if (ACTION_DECRYPT.equals(action) && textData != null) {
            Log.d(Constants.TAG, "textData null, matching text ...");
            Matcher matcher = PgpHelper.PGP_MESSAGE.matcher(textData);
            if (matcher.matches()) {
                Log.d(Constants.TAG, "PGP_MESSAGE matched");
                textData = matcher.group(1);
                // replace non breakable spaces
                textData = textData.replaceAll("\\xa0", " ");
                mMessage.setText(textData);
            } else {
                matcher = PgpHelper.PGP_SIGNED_MESSAGE.matcher(textData);
                if (matcher.matches()) {
                    Log.d(Constants.TAG, "PGP_SIGNED_MESSAGE matched");
                    textData = matcher.group(1);
                    // replace non breakable spaces
                    textData = textData.replaceAll("\\xa0", " ");
                    mMessage.setText(textData);
                } else {
                    Log.d(Constants.TAG, "Nothing matched!");
                }
            }
        } else if (ACTION_DECRYPT.equals(action) && uri != null) {
            // get file path from uri
            String path = FileHelper.getPath(this, uri);

            if (path != null) {
                mInputFilename = path;
                mFilename.setText(mInputFilename);
                guessOutputFilename();
                mSource.setInAnimation(null);
                mSource.setOutAnimation(null);
                while (mSource.getCurrentView().getId() != R.id.sourceFile) {
                    mSource.showNext();
                }
            } else {
                Log.e(Constants.TAG,
                        "Direct binary data without actual file in filesystem is not supported. Please use the Remote Service API!");
                Toast.makeText(this, R.string.error_only_files_are_supported, Toast.LENGTH_LONG)
                        .show();
                // end activity
                finish();
            }
        } else {
            Log.e(Constants.TAG,
                    "Include the extra 'text' or an Uri with setData() in your Intent!");
        }
        */
    }

    public void guessOutputFilename(EditText mFilename) {
        mInputFilename = mFilename.getText().toString();
        File file = new File(mInputFilename);
        String filename = file.getName();
        if (filename.endsWith(".asc") || filename.endsWith(".gpg") || filename.endsWith(".pgp")) {
            filename = filename.substring(0, filename.length() - 4);
        }
        mOutputFilename = Constants.path.APP_DIR + "/" + filename;
    }



    public void decryptClicked(EditText data, int code) {
        initiateDecryption(data, code);
    }

    private void initiateDecryption(View Fragment_View, int code) {

        mDecryptTarget = code;
        EditText mFilename = (EditText)Fragment_View.findViewById(R.id.filename);
        if (mDecryptTarget == Id.target.file) {
            String currentFilename = mFilename.getText().toString();
            if (mInputFilename == null || !mInputFilename.equals(currentFilename)) {
                guessOutputFilename(mFilename);
            }

            if (mInputFilename.equals("")) {
                AppMsg.makeText(this, R.string.no_file_selected, AppMsg.STYLE_ALERT).show();
                return;
            }

            if (mInputFilename.startsWith("file")) {
                File file = new File(mInputFilename);
                if (!file.exists() || !file.isFile()) {
                    AppMsg.makeText(
                            this,
                            getString(R.string.error_message,
                                    getString(R.string.error_file_not_found)), AppMsg.STYLE_ALERT)
                            .show();
                    return;
                }
            }
        }

        if (mDecryptTarget == Id.target.message) {
            String messageData = mFilename.getText().toString();
            Matcher matcher = PgpHelper.PGP_SIGNED_MESSAGE.matcher(messageData);
            if (matcher.matches()) {
                mSignedOnly = true;
                decryptStart(Fragment_View, code);
                return;
            }
        }

        // else treat it as an decrypted message/file
        mSignedOnly = false;
        EditText mMessage = (EditText)Fragment_View.findViewById(R.id.message);
        getDecryptionKeyFromInputStream(mMessage);

        // if we need a symmetric passphrase or a passphrase to use a secret key ask for it
        if (mSecretKeyId == Id.key.symmetric
                || PassphraseCacheService.getCachedPassphrase(this, mSecretKeyId) == null) {
            showPassphraseDialog(Fragment_View);
        } else {
            if (mDecryptTarget == Id.target.file) {
                askForOutputFilename(Fragment_View, code);
            } else { // mDecryptTarget == Id.target.message
                decryptStart(Fragment_View, code);
            }
        }
    }

    /**
     * Shows passphrase dialog to cache a new passphrase the user enters for using it later for
     * encryption. Based on mSecretKeyId it asks for a passphrase to open a private key or it asks
     * for a symmetric passphrase
     */
    public void showPassphraseDialog(final View fragmentView) {
        // Message is received after passphrase is cached
        Handler returnHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                if (message.what == PassphraseDialogFragment.MESSAGE_OKAY) {
                    if (mDecryptTarget == Id.target.file) {
                        askForOutputFilename(fragmentView, Id.target.file);
                    } else {
                        decryptStart(fragmentView, Id.target.message);
                    }
                }
            }
        };

        // Create a new Messenger for the communication back
        Messenger messenger = new Messenger(returnHandler);

        try {
            PassphraseDialogFragment passphraseDialog = PassphraseDialogFragment.newInstance(this,
                    messenger, mSecretKeyId);

            passphraseDialog.show(getSupportFragmentManager(), "passphraseDialog");
        } catch (PgpGeneralException e) {
            Log.d(Constants.TAG, "No passphrase for this secret key, encrypt directly!");
            // send message to handler to start encryption directly
            returnHandler.sendEmptyMessage(PassphraseDialogFragment.MESSAGE_OKAY);
        }
    }

    /**
     * TODO: Rework function, remove global variables
     */
    private void getDecryptionKeyFromInputStream(EditText mMessage) {
        InputStream inStream = null;
        if (mContentUri != null) {
            try {
                inStream = getContentResolver().openInputStream(mContentUri);
            } catch (FileNotFoundException e) {
                Log.e(Constants.TAG, "File not found!", e);
                AppMsg.makeText(this, getString(R.string.error_file_not_found, e.getMessage()),
                        AppMsg.STYLE_ALERT).show();
            }
        } else if (mDecryptTarget == Id.target.file) {
            // check if storage is ready
            if (!FileHelper.isStorageMounted(mInputFilename)) {
                AppMsg.makeText(this, getString(R.string.error_external_storage_not_ready),
                        AppMsg.STYLE_ALERT).show();
                return;
            }

            try {
                inStream = new BufferedInputStream(new FileInputStream(mInputFilename));
            } catch (FileNotFoundException e) {
                Log.e(Constants.TAG, "File not found!", e);
                AppMsg.makeText(this, getString(R.string.error_file_not_found, e.getMessage()),
                        AppMsg.STYLE_ALERT).show();
            }
        } else {
            inStream = new ByteArrayInputStream(mMessage.getText().toString().getBytes());
        }

        // get decryption key for this inStream
        try {
            try {
                if (inStream.markSupported()) {
                    inStream.mark(200); // should probably set this to the max size of two pgpF
                    // objects, if it even needs to be anything other than 0.
                }
                mSecretKeyId = PgpHelper.getDecryptionKeyId(this, inStream);
                if (mSecretKeyId == Id.key.none) {
                    throw new PgpGeneralException(getString(R.string.error_no_secret_key_found));
                }
                mAssumeSymmetricEncryption = false;
            } catch (NoAsymmetricEncryptionException e) {
                if (inStream.markSupported()) {
                    inStream.reset();
                }
                mSecretKeyId = Id.key.symmetric;
                if (!PgpDecryptVerify.hasSymmetricEncryption(this, inStream)) {
                    throw new PgpGeneralException(
                            getString(R.string.error_no_known_encryption_found));
                }
                mAssumeSymmetricEncryption = true;
            }
        } catch (Exception e) {
            AppMsg.makeText(this, getString(R.string.error_message, e.getMessage()),
                    AppMsg.STYLE_ALERT).show();
        }
    }

    private void replyClicked(EditText mMessage) {
        Intent intent = new Intent(this, EncryptActivity.class);
        intent.setAction(EncryptActivity.ACTION_ENCRYPT);
        String data = mMessage.getText().toString();
        data = data.replaceAll("(?m)^", "> ");
        data = "\n\n" + data;
        intent.putExtra(EncryptActivity.EXTRA_TEXT, data);
        intent.putExtra(EncryptActivity.EXTRA_SIGNATURE_KEY_ID, mSecretKeyId);
        intent.putExtra(EncryptActivity.EXTRA_ENCRYPTION_KEY_IDS, new long[]{mSignatureKeyId});
        startActivity(intent);
    }

    public void askForOutputFilename(final View fragment_view, final int code) {
        // Message is received after passphrase is cached
        Handler returnHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                if (message.what == FileDialogFragment.MESSAGE_OKAY) {
                    Bundle data = message.getData();
                    mOutputFilename = data.getString(FileDialogFragment.MESSAGE_DATA_FILENAME);
                    decryptStart(fragment_view, code);
                }
            }
        };

        // Create a new Messenger for the communication back
        Messenger messenger = new Messenger(returnHandler);

        mFileDialog = FileDialogFragment.newInstance(messenger,
                getString(R.string.title_decrypt_to_file),
                getString(R.string.specify_file_to_decrypt_to), mOutputFilename, null);

        mFileDialog.show(getSupportFragmentManager(), "fileDialog");
    }

    private void lookupUnknownKey(long unknownKeyId) {
        Intent intent = new Intent(this, ImportKeysActivity.class);
        intent.setAction(ImportKeysActivity.ACTION_IMPORT_KEY_FROM_KEYSERVER);
        intent.putExtra(ImportKeysActivity.EXTRA_KEY_ID, unknownKeyId);
        startActivityForResult(intent, RESULT_CODE_LOOKUP_KEY);
    }

    public void decryptStart(final View fragment_view, int code) {
        Log.d(Constants.TAG, "decryptStart");
        final EditText mFilename;
        final EditText mMessage;
        final LinearLayout mSignatureLayout = (LinearLayout)fragment_view.findViewById(R.id.signature);
        final CheckBox mDeleteAfter;
        final BootstrapButton mLookupKey = (BootstrapButton) findViewById(R.id.lookup_key);
        final TextView mUserId = (TextView) fragment_view.findViewById(R.id.mainUserId);
        final TextView mUserIdRest = (TextView) fragment_view.findViewById(R.id.mainUserIdRest);
        final ImageView mSignatureStatusImage = (ImageView) fragment_view.findViewById(R.id.ic_signature_status);
        if (code == Id.target.file) {
            try {
                mFilename = (EditText) fragment_view.findViewById(R.id.filename);
                mDeleteAfter = (CheckBox) findViewById(R.id.deleteAfterDecryption);
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
        else if(code == Id.target.message){
                mMessage = (EditText) fragment_view.findViewById(R.id.message);
        }
        // Send all information needed to service to decrypt in other thread
        Intent intent = new Intent(this, KeychainIntentService.class);

        // fill values for this action
        Bundle data = new Bundle();

        intent.setAction(KeychainIntentService.ACTION_DECRYPT_VERIFY);

        // choose action based on input: decrypt stream, file or bytes
        if (mContentUri != null) {
            data.putInt(KeychainIntentService.TARGET, KeychainIntentService.TARGET_STREAM);

            data.putParcelable(KeychainIntentService.ENCRYPT_PROVIDER_URI, mContentUri);
        } else if (mDecryptTarget == Id.target.file) {
            data.putInt(KeychainIntentService.TARGET, KeychainIntentService.TARGET_URI);

            Log.d(Constants.TAG, "mInputFilename=" + mInputFilename + ", mOutputFilename="
                    + mOutputFilename);

            data.putString(KeychainIntentService.ENCRYPT_INPUT_FILE, mInputFilename);
            data.putString(KeychainIntentService.ENCRYPT_OUTPUT_FILE, mOutputFilename);
        } else {
            data.putInt(KeychainIntentService.TARGET, KeychainIntentService.TARGET_BYTES);
            EditText mMessage1 = (EditText)fragment_view.findViewById(R.id.message);
            String message = mMessage1.getText().toString();
            data.putByteArray(KeychainIntentService.DECRYPT_CIPHERTEXT_BYTES, message.getBytes());
        }

        data.putLong(KeychainIntentService.ENCRYPT_SECRET_KEY_ID, mSecretKeyId);

        data.putBoolean(KeychainIntentService.DECRYPT_RETURN_BYTES, mReturnBinary);
        data.putBoolean(KeychainIntentService.DECRYPT_ASSUME_SYMMETRIC, mAssumeSymmetricEncryption);

        intent.putExtra(KeychainIntentService.EXTRA_DATA, data);

        // Message is received after encrypting is done in ApgService
        KeychainIntentServiceHandler saveHandler = new KeychainIntentServiceHandler(this,
                getString(R.string.progress_decrypting), ProgressDialog.STYLE_HORIZONTAL) {
            public void handleMessage(Message message) {
                // handle messages by standard ApgHandler first
                super.handleMessage(message);

                if (message.arg1 == KeychainIntentServiceHandler.MESSAGE_OKAY) {
                    // get returned data bundle
                    Bundle returnData = message.getData();

                    mSignatureKeyId = 0;
                    mSignatureLayout.setVisibility(View.GONE);

                    AppMsg.makeText(DecryptActivity.this, R.string.decryption_successful,
                            AppMsg.STYLE_INFO).show();
                    if (mReturnResult) {
                        Intent intent = new Intent();
                        intent.putExtras(returnData);
                        setResult(RESULT_OK, intent);
                        finish();
                        return;
                    }

                    switch (mDecryptTarget) {
                        case Id.target.message:
                            String decryptedMessage = returnData
                                    .getString(KeychainIntentService.RESULT_DECRYPTED_STRING);
                            EditText mMessage1 = (EditText) fragment_view.findViewById(R.id.message);
                            mMessage1.setText(decryptedMessage);
                            mMessage1.setHorizontallyScrolling(false);

                            break;

                        case Id.target.file:
                            CheckBox mDeleteAfter1 = (CheckBox) findViewById(R.id.deleteAfterDecryption);
                            if (mDeleteAfter1.isChecked()) {
                                // Create and show dialog to delete original file
                                DeleteFileDialogFragment deleteFileDialog = DeleteFileDialogFragment
                                        .newInstance(mInputFilename);
                                deleteFileDialog.show(getSupportFragmentManager(), "deleteDialog");
                            }
                            break;

                        default:
                            // shouldn't happen
                            break;

                    }

                    PgpDecryptVerifyResult decryptVerifyResult =
                            returnData.getParcelable(KeychainIntentService.RESULT_DECRYPT_VERIFY_RESULT);

                    OpenPgpSignatureResult signatureResult = decryptVerifyResult.getSignatureResult();

                    if (signatureResult != null) {

                        String userId = signatureResult.getUserId();
                        mSignatureKeyId = signatureResult.getKeyId();
                        mUserIdRest.setText("id: "
                                + PgpKeyHelper.convertKeyIdToHex(mSignatureKeyId));
                        if (userId == null) {
                            userId = getResources().getString(R.string.user_id_no_name);
                        }
                        String chunks[] = userId.split(" <", 2);
                        userId = chunks[0];
                        if (chunks.length > 1) {
                            mUserIdRest.setText("<" + chunks[1]);
                        }
                        mUserId.setText(userId);

                        switch (signatureResult.getStatus()) {
                            case OpenPgpSignatureResult.SIGNATURE_SUCCESS_UNCERTIFIED: {
                                mSignatureStatusImage.setImageResource(R.drawable.overlay_ok);
                                mLookupKey.setVisibility(View.GONE);
                                break;
                            }

                            // TODO!
//                            case OpenPgpSignatureResult.SIGNATURE_SUCCESS_CERTIFIED: {
//                                break;
//                            }

                            case OpenPgpSignatureResult.SIGNATURE_UNKNOWN_PUB_KEY: {
                                mSignatureStatusImage.setImageResource(R.drawable.overlay_error);
                                mLookupKey.setVisibility(View.VISIBLE);
                                AppMsg.makeText(DecryptActivity.this,
                                        R.string.unknown_signature,
                                        AppMsg.STYLE_ALERT).show();
                                break;
                            }

                            default: {
                                mSignatureStatusImage.setImageResource(R.drawable.overlay_error);
                                mLookupKey.setVisibility(View.GONE);
                                break;
                            }
                        }
                        mSignatureLayout.setVisibility(View.VISIBLE);
                    }
                }
            }
        };

        // Create a new Messenger for the communication back
        Messenger messenger = new Messenger(saveHandler);
        intent.putExtra(KeychainIntentService.EXTRA_MESSENGER, messenger);

        // show progress dialog
        saveHandler.showProgressDialog(this);

        // start service with intent
        startService(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case RESULT_CODE_FILE: {
                if (resultCode == RESULT_OK && data != null) {
                    try {
                        String path = FileHelper.getPath(this, data.getData());
                        Log.d(Constants.TAG, "path=" + path);
                        decrypt_pager.setCurrentItem(FRAGMENT_FILE_POSITION);// i.e 1
                        Fragment fileFragment = (DecryptFileFragment)getSupportFragmentManager().findFragmentByTag
                                ("android:switcher:" + R.id.decrypt_pager + ":" +
                                        decrypt_pager.getCurrentItem());
                        EditText mFilename= (EditText)fileFragment.getView().findViewById(R.id.filename);
                        mFilename.setText(path);
                    } catch (NullPointerException e) {
                        Log.e(Constants.TAG, "Nullpointer while retrieving path!");
                    }
                }
                return;
            }

            // this request is returned after LookupUnknownKeyDialogFragment started
            // ImportKeysActivity and user looked uo key
            case RESULT_CODE_LOOKUP_KEY: {
                Log.d(Constants.TAG, "Returning from Lookup Key...");
                if (resultCode == RESULT_OK) {
                    // decrypt again
                    Fragment fragment = getSupportFragmentManager().findFragmentByTag
                            ("android:switcher:" + R.id.decrypt_pager + ":" +
                                    decrypt_pager.getCurrentItem());
                    EditText mMessage1 = null;
                    try {

                        mMessage1 = (EditText) fragment.getView().findViewById(R.id.message);
                    }
                    catch(Exception e){
                        e.printStackTrace();
                    }
                    if(mMessage1 == null) {
                        decryptStart(fragment.getView(), Id.target.file);
                    }
                    else{
                        decryptStart(fragment.getView(), Id.target.message);
                    }
                }
                return;
            }

            default: {
                super.onActivityResult(requestCode, resultCode, data);

                break;
            }
        }
    }

}
