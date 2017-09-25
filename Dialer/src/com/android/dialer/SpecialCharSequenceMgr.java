/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.dialer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.KeyguardManager;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Looper;
import android.provider.Settings;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import com.android.common.io.MoreCloseables;
import com.android.contacts.common.compat.CompatUtils;
import com.android.contacts.common.compat.TelephonyManagerCompat;
import com.android.contacts.common.database.NoNullCursorAsyncQueryHandler;
import com.android.contacts.common.util.ContactDisplayUtils;
import com.android.contacts.common.widget.SelectPhoneAccountDialogFragment;
import com.android.contacts.common.widget.SelectPhoneAccountDialogFragment.SelectPhoneAccountListener;
import com.android.dialer.calllog.PhoneAccountUtils;
import com.android.dialer.util.TelecomUtil;

import java.util.ArrayList;
import java.util.List;

import android.content.ComponentName;
import android.content.ActivityNotFoundException;
import android.os.Build;
import android.os.SystemProperties;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Helper class to listen for some magic character sequences
 * that are handled specially by the dialer.
 * <p>
 * Note the Phone app also handles these sequences too (in a couple of
 * relatively obscure places in the UI), so there's a separate version of
 * this class under apps/Phone.
 * <p>
 * TODO: there's lots of duplicated code between this class and the
 * corresponding class under apps/Phone.  Let's figure out a way to
 * unify these two classes (in the framework? in a common shared library?)
 */
public class SpecialCharSequenceMgr {
    private static final String TAG = "SpecialCharSequenceMgr";

    private static final String TAG_SELECT_ACCT_FRAGMENT = "tag_select_acct_fragment";

    private static final String SECRET_CODE_ACTION = "android.provider.Telephony.SECRET_CODE";
    private static final String MMI_IMEI_DISPLAY = "*#06#";
    private static final String MMI_REGULATORY_INFO_DISPLAY = "*#07#";

    private static final String PRL_VERSION_DISPLAY = "*#0000#";

    /* Hidden menu */
    private static final String HIDDENMENU_BY_APK = "android.intent.action.factorytest";
    private static final String EN_HIDDENMENU_BY_APK = "*#476368#*22746#";

    /* add FACTORY_TEST code by huangzhigang start*/
    private static final String EN_FACTORY_TEST = "*937*0#";
    private static final String FACTORY_MMI2_TEST = "*#*#1111#*#*";
    private static final String EN_VERSION_INFO_DISPLAY = "*#8375#";
    /* add FACTORY_TEST code by huangzhigang end*/
    /* add REBOOT code by renhongwei start*/
    //private static final String EN_REBOOT = "*#2846#";
    /* add REBOOT code by renhongwei end*/

    /*add hardinfo code by shenlong start*/
    private static final String HARDINFO = "*#42734636#";
    private static final String MMI_HARD_INFO = "android.com.huaqin.action.HARDWARE";
    /*add hardinfo code by shenlong end*/

    // sunzhi5, BugID:ICE2-114, [HQ_VIBRATOR_MODE] modify start
    private static final String EN_VIBRATOR_INFO = "*#00*#925#";
    private static final String MMI_VIBRATOR_INFO = "android.com.hqvibrator.improve";
    // sunzhi5, BugID:ICE2-114, [HQ_VIBRATOR_MODE] modify end

    /*add start by huqichang runtimetest*/
    private static final String EN_RUNTIME_TEST= "*#6688#";
    /*add end by huqichang runtimetest*/
    private static final int IMEI_14_DIGIT = 14;
    /*add diag port code by yuhaixiang start*/
    private static final String DIAGPORT = "*#*#717717#*#*";
    private static final String MMI_DIAG_PORT = "android.com.huaqin.action.diagport";
    private static final String EN_FLAG_INFO_DISPLAY = "*937*6#";
    /*add diag port code by yuhaixiang end*/

    /*add remove serial no code by yuhaixiang start*/
    private static final String RemoveSerialNo = "*#*#73742566#*#*";
    private static final String MMI_REMOVE_SERIALNO = "android.com.huaqin.action.remove.serialno";
    /*add remove serial no code by yuhaixiang end*/

    // Alex.Xie 20170525 add for Logkit 3.0 start @{
    private static final String LOGKIT_TOOL = "*564548*3#";
    // Alex.Xie 20170525 add for Logkit 3.0 end @}

    // Alex.Xie 20170601 add for Disable GMS start @{
    //private static final String DISABLE_GMS = "*937*467#";
    // Alex.Xie 20170601 add for Disable GMS end @}

    /**
     * Remembers the previous {@link QueryHandler} and cancel the operation when needed, to
     * prevent possible crash.
     * <p>
     * QueryHandler may call {@link ProgressDialog#dismiss()} when the screen is already gone,
     * which will cause the app crash. This variable enables the class to prevent the crash
     * on {@link #cleanup()}.
     * <p>
     * TODO: Remove this and replace it (and {@link #cleanup()}) with better implementation.
     * One complication is that we have SpecialCharSequenceMgr in Phone package too, which has
     * *slightly* different implementation. Note that Phone package doesn't have this problem,
     * so the class on Phone side doesn't have this functionality.
     * Fundamental fix would be to have one shared implementation and resolve this corner case more
     * gracefully.
     */
    private static QueryHandler sPreviousAdnQueryHandler;

    public static class HandleAdnEntryAccountSelectedCallback extends SelectPhoneAccountListener {
        final private Context mContext;
        final private QueryHandler mQueryHandler;
        final private SimContactQueryCookie mCookie;

        public HandleAdnEntryAccountSelectedCallback(Context context,
                                                     QueryHandler queryHandler, SimContactQueryCookie cookie) {
            mContext = context;
            mQueryHandler = queryHandler;
            mCookie = cookie;
        }

        @Override
        public void onPhoneAccountSelected(PhoneAccountHandle selectedAccountHandle,
                                           boolean setDefault) {
            Uri uri = TelecomUtil.getAdnUriForPhoneAccount(mContext, selectedAccountHandle);
            handleAdnQuery(mQueryHandler, mCookie, uri);
            // TODO: Show error dialog if result isn't valid.
        }

    }

    public static class HandleMmiAccountSelectedCallback extends SelectPhoneAccountListener {
        final private Context mContext;
        final private String mInput;

        public HandleMmiAccountSelectedCallback(Context context, String input) {
            mContext = context.getApplicationContext();
            mInput = input;
        }

        @Override
        public void onPhoneAccountSelected(PhoneAccountHandle selectedAccountHandle,
                                           boolean setDefault) {
            TelecomUtil.handleMmi(mContext, mInput, selectedAccountHandle);
        }
    }

    /**
     * This class is never instantiated.
     */
    private SpecialCharSequenceMgr() {
    }

    public static boolean handleChars(Context context, String input, EditText textField) {
        //get rid of the separators so that the string gets parsed correctly
        String dialString = PhoneNumberUtils.stripSeparators(input);
        ///ICE15-1945, context is null,Add by fanta @{
        if(context == null){
            return false;
        }
        ///@}
        if (handleDeviceIdDisplay(context, dialString)
                || handlePRLVersion(context, dialString)
                || handleRegulatoryInfoDisplay(context, dialString)
                || handlePinEntry(context, dialString)
                || handleVibratorImprove(context, dialString) //HQ_VIBRATOR_MODE sunzhi5, HQ vibrator
                /*add for RuntimeTest by huqichang begin */
                || handleRuntimeTest(context, dialString)
                /*add for RuntimeTest by huqichang end */
                /* add FACTORY_TEST code by huangzhigang start*/
                || handleFactoryTestCode(context, dialString)
                || handleHuaqinVersionInfo(context, dialString)
                /* add FACTORY_TEST code by huangzhigang end*/
                //|| handleReboot(context, dialString) //add Reboot by renhongwei
                || handleHardInfo(context,dialString)//add hardinfo by shenlong
                || handleDiagport(context,dialString)//add diag port by yuhaixiang
                || handleRemoveSerialNo(context,dialString) //add remove serial no by yuhaixiang
                || handleFactoryFLAGInfo(context,dialString)//add flag display by yuhaixiang
                || handleLogkitTool(context, dialString) // Alex.Xie 20170525 add for Logkit 3.0
                //|| handleDisableGms(context, dialString) //Alex.Xie 20170601 add for Disable GMS
                || handleAdnEntry(context, dialString, textField)
                || handleSecretCode(context, dialString)
                || handleHiddenMenu(context,dialString)) {
            return true;
        }

        return false;
    }

    // sunzhi5, BugID:ICE2-114, [HQ_VIBRATOR_MODE] modify start
    public static boolean handleVibratorImprove(Context context, String input) {
               if (input.equals(EN_VIBRATOR_INFO)) {
                  Intent intent = new Intent();
                  intent.setAction(MMI_VIBRATOR_INFO);
                  context.sendBroadcast(intent);
                  Log.d(TAG,"handleVibratorImprove=true");
                  return true;
          }
          return false;
   }
   // sunzhi5, BugID:ICE2-114, [HQ_VIBRATOR_MODE] modify end

    /*add hardinfo code by shenlong start*/
    public static boolean handleHardInfo(Context context, String input) {
        if (input.equals(HARDINFO)) {
            Intent intent = new Intent();
            intent.setAction(MMI_HARD_INFO);
            context.sendBroadcast(intent);
            return true;
        }
        return false;
    }
    /*add hardinfo code by shenlong end*/

  /*add diag port code by yuhaixiang start*/
    public static boolean handleDiagport(Context context, String input) {
        if (input.equals(DIAGPORT)) {
            Intent intent = new Intent();
            intent.setAction(MMI_DIAG_PORT);
            context.sendBroadcast(intent);
            return true;
        }
        return false;
    }
    /*add diag port code by yuhaixiang end*/

    /*add remove serial no code by yuhaixiang start*/
    public static boolean handleRemoveSerialNo(Context context, String input) {
        if (input.equals(RemoveSerialNo)) {
            Intent intent = new Intent();
            intent.setAction(MMI_REMOVE_SERIALNO);
            context.sendBroadcast(intent);
            return true;
        }
        return false;
    }
    /*add remove serial no code by yuhaixiang end*/

    /*[HQ_Reboot_APK] modify by renhongwei start*/
    /*
    public static boolean handleReboot(Context context, String input) {
        if (input.equals(EN_REBOOT)) {
            Intent mIntent = new Intent();
            mIntent.setClassName("com.zh.reboot", "com.zh.reboot.mainActivity");
            mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(mIntent);
            return true;
        }
        return false;
    }
    */
    /*[HQ_Reboot_APK] modify by renhongwei end*/

    /* add FACTORY_TEST_CODE by huangzhigang start*/
    public static boolean handleFactoryTestCode(Context context, String input) {
        if (input.equals(EN_FACTORY_TEST) || input.equals(FACTORY_MMI2_TEST)) {
            try{
                Intent intent = new Intent(Intent.ACTION_MAIN);
                ComponentName componentName = new ComponentName("com.android.huaqin.factory", "com.android.huaqin.factory.ControlCenterActivity");
                intent.setComponent(componentName);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return true;
            } catch (ActivityNotFoundException e) {
                Log.d(TAG, "no activity to handle showing factory mode");
            }
        }
        return false;
    }
    /* add FACTORY_TEST_CODE by huangzhigang end*/

    public static boolean handleHiddenMenu(Context context, String input) {
        if (input.equals(EN_HIDDENMENU_BY_APK)) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            ComponentName componentName = new ComponentName("com.huaqin.hiddenmenu", "com.huaqin.hiddenmenu.HideMenu");
            intent.setComponent(componentName);
            context.startActivity(intent);
            return true;
        }
        return false;
    }

    /*[HQ_RuntimeTest_APK] modify huqichang start*/
    public static boolean handleRuntimeTest(Context context, String input) {
        if (input.equals(EN_RUNTIME_TEST)) {
           try{
               Intent mIntent = new Intent();
               mIntent.setClassName("com.huaqin.runtime", "com.huaqin.runtime.RuntimeTestMain");
               mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
               context.startActivity(mIntent);
               return true;
           } catch (ActivityNotFoundException e) {
                Log.d(TAG, "no activity to handle showing running");
            }
        }
        return false;
    }
    /*[HQ_RuntimeTest_APK] modify huqichang end*/

    /* add HUAQIN_VERSION_INFO by huangzhigang start*/
    public static boolean handleHuaqinVersionInfo(Context context, String input) {
        if (input.equals(EN_VERSION_INFO_DISPLAY)) {
            String strInfo = "";
            String buildNumber = Build.DISPLAY;
            buildNumber = SystemProperties.get("ro.huaqin.version.release");
            String baseBand = SystemProperties
                    .get("gsm.version.baseband", "Unknown");
            strInfo = "SW version:\n" + Build.VERSION.HQ_SW_REL + "\n" + "Android version:\n"
                    + Build.VERSION.RELEASE + "\n" + "Baseband version:\n" + baseBand + "\n"
                    + "HW version:\n" + SystemProperties.get("ro.product.hw.version") + "\n"
                    + "Model number:\n" + Build.MODEL + "\n"
                    + "Kernel version:\n" + getFormattedKernelVersion() + "\n"
                    + "Build Type:\n" + Build.TYPE + "\n"
                    + "Build Time :\n" + SystemProperties.get("ro.build.date") + "\n";

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Version Info")
                    .setIcon(android.R.drawable.ic_dialog_info).setMessage(strInfo)
                    .setNegativeButton("cancel", null);
            builder.show();
            return true;
        }
        return false;
    }

    //add by yuhaixiang for flag begin
    public static boolean handleFactoryFLAGInfo(Context context, String input) {
       if (input.equals(EN_FLAG_INFO_DISPLAY)) {
           Intent mIntent = new Intent();
           mIntent.setClassName("com.android.settings", "com.android.settings.ViewFactoryFlagsActivity");
           mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
           context.startActivity(mIntent);
           return true;
        }
        return false;
    }
    //add by yuhaixiang for flag end

    // Alex.Xie 20170525 add for Logkit 3.0 start @{
    public static boolean handleLogkitTool(Context context, String input) {
        if (input.equals(LOGKIT_TOOL)) {
            Intent mIntent = new Intent();
            mIntent.setClassName("com.qualcomm.qti.logkit", "com.qualcomm.qti.logkit.cActivity");
            mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(mIntent);
            return true;
        }
        return false;
    }
    // Alex.Xie 20170525 add for Logkit 3.0 end @}

    // Alex.Xie 20170601 add for DISABLE GMS start @{
    /*
    public static boolean handleDisableGms(Context context, String input) {
        if (input.equals(DISABLE_GMS)) {
            Intent mIntent = new Intent();
            mIntent.setClassName("com.huaqin.hiddenmenu", "com.huaqin.hiddenmenu.disablegms.DisableGmsActivity");
            mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(mIntent);
            return true;
        }
        return false;
    }
    */
    // Alex.Xie 20170601 add for DISABLE GMS end @}

    private static String getFormattedKernelVersion() {
        String procVersionStr;

        try {
            BufferedReader reader = new BufferedReader(new FileReader(
                    "/proc/version"), 256);
            try {
                procVersionStr = reader.readLine();
            } finally {
                reader.close();
            }

            final String PROC_VERSION_REGEX = "\\w+\\s+" + /* ignore: Linux */
                    "\\w+\\s+" + /* ignore: version */
                    "([^\\s]+)\\s+" + /* group 1: 2.6.22-omap1 */
                    "\\(([^\\s@]+(?:@[^\\s.]+)?)[^)]*\\)\\s+" + /*
                                                             * group 2:
                                                             * (xxxxxx@xxxxx
                                                             * .constant)
                                                             */
                    "\\((?:[^(]*\\([^)]*\\))?[^)]*\\)\\s+" + /* ignore: (gcc ..) */
                    "([^\\s]+)\\s+" + /* group 3: #26 */
                    "(?:PREEMPT\\s+)?" + /* ignore: PREEMPT (optional) */
                    "(.+)"; /* group 4: date */
            Pattern p = Pattern.compile(PROC_VERSION_REGEX);
            Matcher m = p.matcher(procVersionStr);
            if (!m.matches()) {
                return "Unavailable";
            } else if (m.groupCount() < 4) {
                return "Unavailable";
            } else {
                return (new StringBuilder(m.group(1))).toString();
            }
        } catch (IOException e) {
            return "Unavailable";
        }
    }
    /* add HUAQIN_VERSION_INFO by huangzhigang end*/

    static private boolean handlePRLVersion(Context context, String input) {
        if (input.equals(PRL_VERSION_DISPLAY)) {
            try {
                Intent intent = new Intent("android.intent.action.ENGINEER_MODE_DEVICEINFO");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return true;
            } catch (ActivityNotFoundException e) {
                Log.d(TAG, "no activity to handle showing device info");
            }
        }
        return false;
    }

    /**
     * Cleanup everything around this class. Must be run inside the main thread.
     * <p>
     * This should be called when the screen becomes background.
     */
    public static void cleanup() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.wtf(TAG, "cleanup() is called outside the main thread");
            return;
        }

        if (sPreviousAdnQueryHandler != null) {
            sPreviousAdnQueryHandler.cancel();
            sPreviousAdnQueryHandler = null;
        }
    }

    /**
     * Handles secret codes to launch arbitrary activities in the form of *#*#<code>#*#*.
     * If a secret code is encountered an Intent is started with the android_secret_code://<code>
     * URI.
     *
     * @param context the context to use
     * @param input   the text to check for a secret code in
     * @return true if a secret code was encountered
     */
    static boolean handleSecretCode(Context context, String input) {
        // Secret codes are in the form *#*#<code>#*#*
        int len = input.length();
        if (len > 8 && input.startsWith("*#*#") && input.endsWith("#*#*")) {
            final Intent intent = new Intent(SECRET_CODE_ACTION,
                    Uri.parse("android_secret_code://" + input.substring(4, len - 4)));
            context.sendBroadcast(intent);
            return true;
        }
        if (!TextUtils.isEmpty(context.getString(R.string.oem_key_code_action))) {
            if (len > 10 && !input.startsWith("*#*#")
                    && input.startsWith("*#") && input.endsWith("#")) {
                Intent intent = new Intent(context.getString(R.string.oem_key_code_action));
                intent.putExtra(context.getString(R.string.oem_code), input);
                context.sendBroadcast(intent);
                return true;
            }
        }
        return false;
    }

    /**
     * Handle ADN requests by filling in the SIM contact number into the requested
     * EditText.
     * <p>
     * This code works alongside the Asynchronous query handler {@link QueryHandler}
     * and query cancel handler implemented in {@link SimContactQueryCookie}.
     */
    static boolean handleAdnEntry(Context context, String input, EditText textField) {
        /* ADN entries are of the form "N(N)(N)#" */
        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager == null
                || telephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_GSM) {
            return false;
        }

        // if the phone is keyguard-restricted, then just ignore this
        // input.  We want to make sure that sim card contacts are NOT
        // exposed unless the phone is unlocked, and this code can be
        // accessed from the emergency dialer.
        KeyguardManager keyguardManager =
                (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguardManager.inKeyguardRestrictedInputMode()) {
            return false;
        }

        int len = input.length();
        if ((len > 1) && (len < 5) && (input.endsWith("#"))) {
            try {
                // get the ordinal number of the sim contact
                final int index = Integer.parseInt(input.substring(0, len - 1));

                // The original code that navigated to a SIM Contacts list view did not
                // highlight the requested contact correctly, a requirement for PTCRB
                // certification.  This behaviour is consistent with the UI paradigm
                // for touch-enabled lists, so it does not make sense to try to work
                // around it.  Instead we fill in the the requested phone number into
                // the dialer text field.

                // create the async query handler
                final QueryHandler handler = new QueryHandler(context.getContentResolver());

                // create the cookie object
                final SimContactQueryCookie sc = new SimContactQueryCookie(index - 1, handler,
                        ADN_QUERY_TOKEN);

                // setup the cookie fields
                sc.contactNum = index - 1;
                sc.setTextField(textField);

                // create the progress dialog
                sc.progressDialog = new ProgressDialog(context);
                sc.progressDialog.setTitle(R.string.simContacts_title);
                sc.progressDialog.setMessage(context.getText(R.string.simContacts_emptyLoading));
                sc.progressDialog.setIndeterminate(true);
                sc.progressDialog.setCancelable(true);
                sc.progressDialog.setOnCancelListener(sc);
                sc.progressDialog.getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_BLUR_BEHIND);

                List<PhoneAccountHandle> subscriptionAccountHandles =
                        PhoneAccountUtils.getSubscriptionPhoneAccounts(context);
                Context applicationContext = context.getApplicationContext();
                boolean hasUserSelectedDefault = subscriptionAccountHandles.contains(
                        TelecomUtil.getDefaultOutgoingPhoneAccount(applicationContext,
                                PhoneAccount.SCHEME_TEL));

                if (subscriptionAccountHandles.size() <= 1 || hasUserSelectedDefault) {
                    Uri uri = TelecomUtil.getAdnUriForPhoneAccount(applicationContext, null);
                    handleAdnQuery(handler, sc, uri);
                } else {
                    SelectPhoneAccountListener callback = new HandleAdnEntryAccountSelectedCallback(
                            applicationContext, handler, sc);

                    DialogFragment dialogFragment = SelectPhoneAccountDialogFragment.newInstance(
                            subscriptionAccountHandles, callback);
                    dialogFragment.show(((Activity) context).getFragmentManager(),
                            TAG_SELECT_ACCT_FRAGMENT);
                }

                return true;
            } catch (NumberFormatException ex) {
                // Ignore
            }
        }
        return false;
    }

    private static void handleAdnQuery(QueryHandler handler, SimContactQueryCookie cookie,
                                       Uri uri) {
        if (handler == null || cookie == null || uri == null) {
            Log.w(TAG, "queryAdn parameters incorrect");
            return;
        }

        // display the progress dialog
        cookie.progressDialog.show();

        // run the query.
        handler.startQuery(ADN_QUERY_TOKEN, cookie, uri, new String[]{ADN_PHONE_NUMBER_COLUMN_NAME},
                null, null, null);

        if (sPreviousAdnQueryHandler != null) {
            // It is harmless to call cancel() even after the handler's gone.
            sPreviousAdnQueryHandler.cancel();
        }
        sPreviousAdnQueryHandler = handler;
    }

    static boolean handlePinEntry(final Context context, final String input) {
        if ((input.startsWith("**04") || input.startsWith("**05")) && input.endsWith("#")) {
            List<PhoneAccountHandle> subscriptionAccountHandles =
                    PhoneAccountUtils.getSubscriptionPhoneAccounts(context);
            boolean hasUserSelectedDefault = subscriptionAccountHandles.contains(
                    TelecomUtil.getDefaultOutgoingPhoneAccount(context, PhoneAccount.SCHEME_TEL));

            if (subscriptionAccountHandles.size() <= 1 || hasUserSelectedDefault) {
                // Don't bring up the dialog for single-SIM or if the default outgoing account is
                // a subscription account.
                return TelecomUtil.handleMmi(context, input, null);
            } else {
                SelectPhoneAccountListener listener =
                        new HandleMmiAccountSelectedCallback(context, input);

                DialogFragment dialogFragment = SelectPhoneAccountDialogFragment.newInstance(
                        subscriptionAccountHandles, listener);
                dialogFragment.show(((Activity) context).getFragmentManager(),
                        TAG_SELECT_ACCT_FRAGMENT);
            }
            return true;
        }
        return false;
    }

    // TODO: Use TelephonyCapabilities.getDeviceIdLabel() to get the device id label instead of a
    // hard-coded string.
    static boolean handleDeviceIdDisplay(Context context, String input) {

        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        if (telephonyManager != null && input.equals(MMI_IMEI_DISPLAY)) {
            int labelResId = (telephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) ?
                    R.string.imei : R.string.meid;

            List<String> deviceIds = new ArrayList<String>();
            if (TelephonyManagerCompat.getPhoneCount(telephonyManager) > 1 &&
                    CompatUtils.isMethodAvailable(TelephonyManagerCompat.TELEPHONY_MANAGER_CLASS,
                            "getDeviceId", Integer.TYPE)) {
                for (int slot = 0; slot < telephonyManager.getPhoneCount(); slot++) {
                    String deviceId = telephonyManager.getDeviceId(slot);
                    boolean enable14DigitImei = false;
                    try {
                        CarrierConfigManager configManager =
                                (CarrierConfigManager) context.getSystemService(
                                        Context.CARRIER_CONFIG_SERVICE);
                        int[] subIds = SubscriptionManager.getSubId(slot);
                        if (configManager != null &&
                                configManager.getConfigForSubId(subIds[0]) != null) {
                            enable14DigitImei =
                                    configManager.getConfigForSubId(subIds[0]).getBoolean(
                                            "config_enable_display_14digit_imei");
                        }
                    } catch (RuntimeException ex) {
                        //do Nothing
                        Log.e(TAG, "Config for 14 digit IMEI not found: " + ex);
                    }
                    if (enable14DigitImei && !TextUtils.isEmpty(deviceId)
                            && deviceId.length() > IMEI_14_DIGIT) {
                        deviceId = deviceId.substring(0, IMEI_14_DIGIT);
                    }
                    if (!TextUtils.isEmpty(deviceId)) {
                        deviceIds.add(deviceId);
                    }
                }
            } else {
                deviceIds.add(telephonyManager.getDeviceId());
            }

            AlertDialog alert = new AlertDialog.Builder(context)
                    .setTitle(labelResId)
                    .setItems(deviceIds.toArray(new String[deviceIds.size()]), null)
                    .setPositiveButton(android.R.string.ok, null)
                    .setCancelable(false)
                    .show();
            return true;
        }
        return false;
    }

    //modify by zouqin begin bug:ICE2-1907
    private static boolean handleRegulatoryInfoDisplay(Context context, String input) {
        if (input.equals(MMI_REGULATORY_INFO_DISPLAY)) {
            Log.d(TAG, "handleRegulatoryInfoDisplay() sending intent to settings app");
            /*Intent showRegInfoIntent = new Intent(Settings.ACTION_SHOW_REGULATORY_INFO);
            try {
                context.startActivity(showRegInfoIntent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "startActivity() failed: " + e);
            }*/
            String strInfo = context.getResources().getString(R.string.sar_context);
            String strTitle = context.getResources().getString(R.string.sar_title);
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(strTitle)
                   .setIcon(android.R.drawable.ic_dialog_info)
                   .setMessage(strInfo)
                   .setNegativeButton("OK", null);
            builder.show();
            return true;
        }
        return false;
    }
    //modify by zouqin end bug:ICE2-1907

    /*******
     * This code is used to handle SIM Contact queries
     *******/
    private static final String ADN_PHONE_NUMBER_COLUMN_NAME = "number";
    private static final String ADN_NAME_COLUMN_NAME = "name";
    private static final int ADN_QUERY_TOKEN = -1;

    /**
     * Cookie object that contains everything we need to communicate to the
     * handler's onQuery Complete, as well as what we need in order to cancel
     * the query (if requested).
     * <p>
     * Note, access to the textField field is going to be synchronized, because
     * the user can request a cancel at any time through the UI.
     */
    private static class SimContactQueryCookie implements DialogInterface.OnCancelListener {
        public ProgressDialog progressDialog;
        public int contactNum;

        // Used to identify the query request.
        private int mToken;
        private QueryHandler mHandler;

        // The text field we're going to update
        private EditText textField;

        public SimContactQueryCookie(int number, QueryHandler handler, int token) {
            contactNum = number;
            mHandler = handler;
            mToken = token;
        }

        /**
         * Synchronized getter for the EditText.
         */
        public synchronized EditText getTextField() {
            return textField;
        }

        /**
         * Synchronized setter for the EditText.
         */
        public synchronized void setTextField(EditText text) {
            textField = text;
        }

        /**
         * Cancel the ADN query by stopping the operation and signaling
         * the cookie that a cancel request is made.
         */
        public synchronized void onCancel(DialogInterface dialog) {
            // close the progress dialog
            if (progressDialog != null) {
                progressDialog.dismiss();
            }

            // setting the textfield to null ensures that the UI does NOT get
            // updated.
            textField = null;

            // Cancel the operation if possible.
            mHandler.cancelOperation(mToken);
        }
    }

    /**
     * Asynchronous query handler that services requests to look up ADNs
     * <p>
     * Queries originate from {@link #handleAdnEntry}.
     */
    private static class QueryHandler extends NoNullCursorAsyncQueryHandler {

        private boolean mCanceled;

        public QueryHandler(ContentResolver cr) {
            super(cr);
        }

        /**
         * Override basic onQueryComplete to fill in the textfield when
         * we're handed the ADN cursor.
         */
        @Override
        protected void onNotNullableQueryComplete(int token, Object cookie, Cursor c) {
            try {
                sPreviousAdnQueryHandler = null;
                if (mCanceled) {
                    return;
                }

                SimContactQueryCookie sc = (SimContactQueryCookie) cookie;

                // close the progress dialog.
                sc.progressDialog.dismiss();

                // get the EditText to update or see if the request was cancelled.
                EditText text = sc.getTextField();

                // if the TextView is valid, and the cursor is valid and positionable on the
                // Nth number, then we update the text field and display a toast indicating the
                // caller name.
                if ((c != null) && (text != null) && (c.moveToPosition(sc.contactNum))) {
                    String name = c.getString(c.getColumnIndexOrThrow(ADN_NAME_COLUMN_NAME));
                    String number =
                            c.getString(c.getColumnIndexOrThrow(ADN_PHONE_NUMBER_COLUMN_NAME));

                    // fill the text in.
                    text.getText().replace(0, 0, number);

                    // display the name as a toast
                    Context context = sc.progressDialog.getContext();
                    CharSequence msg = ContactDisplayUtils.getTtsSpannedPhoneNumber(
                            context.getResources(), R.string.menu_callNumber, name);
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
                }
            } finally {
                MoreCloseables.closeQuietly(c);
            }
        }

        public void cancel() {
            mCanceled = true;
            // Ask AsyncQueryHandler to cancel the whole request. This will fail when the query is
            // already started.
            cancelOperation(ADN_QUERY_TOKEN);
        }
    }
}
