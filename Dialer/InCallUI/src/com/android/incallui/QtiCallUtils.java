/**
 * Copyright (c) 2015, 2016 The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.incallui;

import android.os.IBinder;
import android.os.RemoteException;
import android.telecom.VideoProfile;
import android.telecom.Connection.VideoProvider;
import android.widget.Toast;
import android.content.Context;
import android.content.res.Resources;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.IBinder;
// baoyilin modified for ICE2-1653 2017-07-06
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.content.pm.ActivityInfo;
import android.telecom.InCallService.VideoCall;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import android.content.pm.PackageManager;
import android.Manifest;
import android.support.v4.app.ActivityCompat;

import java.lang.reflect.*;
import java.util.ArrayList;

import org.codeaurora.internal.IExtTelephony;
import org.codeaurora.ims.QtiCallConstants;
// baoyilin modified for ICE2-1653 2017-07-06
import org.codeaurora.ims.QtiCarrierConfigs;
import org.codeaurora.ims.utils.QtiImsExtUtils;

/**
 * This class contains Qti specific utiltity functions.
 */
public class QtiCallUtils {

    private static String LOG_TAG = "QtiCallUtils";

    // baoyilin modified for ICE2-1653 2017-07-06 start
    private static IExtTelephony mIExtTelephony = null;
    private static Method sGetPhoneId = null;
    private static Method sGetSubId = null;
    private static final CarrierConfigCache sCarrierConfigCache = new CarrierConfigCache();

    private static final class CarrierConfigCache {
        private  PersistableBundle mBackup = null;

        private PersistableBundle loadConfig(Context context) {
            start(context);
            return mBackup;
        }

        public boolean getValue(Context context, String key) {
            PersistableBundle values = loadConfig(context);
            if (values != null && key != null) {
                return values.getBoolean(key, false);
            }
            return false;
        }

        public void start(Context context) {
            if (mBackup == null) {
                mBackup = QtiImsExtUtils.getConfigForDefaultImsPhoneId(context);
            }
        }

        public void clear() {
            if (mBackup != null) {
                mBackup = null;
            }
        }
    }
    // baoyilin modified for ICE2-1653 2017-07-06 end

    /**
     * Private constructor for QtiCallUtils as we don't want to instantiate this class
     */
    private QtiCallUtils() {
    }

    // baoyilin modified for ICE2-1653 2017-07-06 start
    public static void startCarrierConfigCache(Context context) {
        sCarrierConfigCache.start(context);
    }

    public static void clearCarrierConfigCache() {
        sCarrierConfigCache.clear();
    }
    // baoyilin modified for ICE2-1653 2017-07-06 end

    /**
     * This utility method checks to see if bits in the mask are enabled in the value
     * Returns true if all bits in {@code mask} are set in {@code value}
     */
    public static boolean isEnabled(final int mask, final int value) {
        return (mask & value) == mask;
    }

    /**
     * This utility method checks to see if none of the bits in the mask are enabled in the value
     * Returns true if none of the bits in {@code mask} are set in {@code value}
     */
    public static boolean isNotEnabled(final int mask, final int value) {
        return (mask & value) == 0;
    }

    /**
     * Method to get the video quality display string resource id given the video quality
     */
    public static int getVideoQualityResourceId(int videoQuality) {
        switch (videoQuality) {
            case VideoProfile.QUALITY_HIGH:
                return R.string.video_quality_high;
            case VideoProfile.QUALITY_MEDIUM:
                return R.string.video_quality_medium;
            case VideoProfile.QUALITY_LOW:
                return R.string.video_quality_low;
            default:
                return R.string.video_quality_unknown;
        }
    }

    /**
     * Returns the call session resource id given the call session event
     */
    public static int getCallSessionResourceId(int event) {
        switch (event) {
            case VideoProvider.SESSION_EVENT_RX_PAUSE:
                return R.string.player_stopped;
            case VideoProvider.SESSION_EVENT_RX_RESUME:
                return R.string.player_started;
            case VideoProvider.SESSION_EVENT_CAMERA_FAILURE:
                return R.string.camera_not_ready;
            case VideoProvider.SESSION_EVENT_CAMERA_READY:
                return R.string.camera_ready;
            default:
                return R.string.unknown_call_session_event;
        }
    }

    /**
     * Displays the message as a Toast on the UI
     */
    public static void displayToast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    /**
     * Displays the string corresponding to the resourceId as a Toast on the UI
     */
    public static void displayToast(Context context, int resourceId) {
        displayToast(context, context.getResources().getString(resourceId));
    }

    /**
     * Displays the message as a long Toast on the UI
     */
    public static void displayLongToast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }

    /**
     * Displays the string corresponding to the resourceId as a long Toast on the UI
     */
    public static void displayLongToast(Context context, int resourceId) {
        displayLongToast(context, context.getResources().getString(resourceId));
    }

    /**
     * The function is called when Modify Call button gets pressed. The function creates and
     * displays modify call options.
     */
    public static void displayModifyCallOptions(final Call call, final Context context) {
        if (call == null) {
            Log.d(LOG_TAG, "Can't display modify call options. Call is null");
            return;
        }

        if (isTtyEnabled(context)) {
            Log.w(LOG_TAG, "Call session modification is allowed only when TTY is off.");
            displayToast(context, R.string.video_call_not_allowed_if_tty_enabled);
            return;
        }

        final ArrayList<CharSequence> items = new ArrayList<CharSequence>();
        final ArrayList<Integer> itemToCallType = new ArrayList<Integer>();
        final Resources res = context.getResources();

        // Prepare the string array and mapping.
        if (hasVoiceCapabilities(call)) {
            items.add(res.getText(R.string.modify_call_option_voice));
            itemToCallType.add(VideoProfile.STATE_AUDIO_ONLY);
        }

        if (hasReceiveVideoCapabilities(call)) {
            items.add(res.getText(R.string.modify_call_option_vt_rx));
            itemToCallType.add(VideoProfile.STATE_RX_ENABLED);
        }

        if (hasTransmitVideoCapabilities(call)) {
            items.add(res.getText(R.string.modify_call_option_vt_tx));
            itemToCallType.add(VideoProfile.STATE_TX_ENABLED);
        }

        if (hasReceiveVideoCapabilities(call) && hasTransmitVideoCapabilities(call)) {
            items.add(res.getText(R.string.modify_call_option_vt));
            itemToCallType.add(VideoProfile.STATE_BIDIRECTIONAL);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.modify_call_option_title);
        final AlertDialog alert;

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                Toast.makeText(context, items.get(item), Toast.LENGTH_SHORT).show();
                final int selCallType = itemToCallType.get(item);
                Log.i(LOG_TAG, "Videocall: ModifyCall: upgrade/downgrade to "
                        + callTypeToString(selCallType));
                VideoProfile videoProfile = new VideoProfile(selCallType);
                changeToVideoClicked(call, videoProfile, context);
                dialog.dismiss();
            }
        };
        final int currUnpausedVideoState = VideoUtils.getUnPausedVideoState(call.getVideoState());
        final int index = itemToCallType.indexOf(currUnpausedVideoState);
        builder.setSingleChoiceItems(items.toArray(new CharSequence[0]), index, listener);
        alert = builder.create();
        alert.show();
    }

    public static void changeToVideoCall(Call call, VideoProfile videoProfile, Context context) {
            changeToVideoClicked(call, videoProfile, context);
    }
    /**
     * Converts the call type to string
     */
    public static String callTypeToString(int callType) {
        switch (callType) {
            case VideoProfile.STATE_BIDIRECTIONAL:
                return "VT";
            case VideoProfile.STATE_TX_ENABLED:
                return "VT_TX";
            case VideoProfile.STATE_RX_ENABLED:
                return "VT_RX";
        }
        return "";
    }

    /**
     * Sends a session modify request to the telephony framework
     */
    private static void changeToVideoClicked(Call call, VideoProfile videoProfile) {
        changeToVideoClicked(call, videoProfile, null);
    }

    private static void changeToVideoClicked(Call call, VideoProfile videoProfile, Context ctx) {
        VideoCall videoCall = call.getVideoCall();
        if (videoCall == null) {
            return;
        }
        videoCall.sendSessionModifyRequest(videoProfile);
        if (shallShowPreviewWhileWaiting(ctx)) {
            call.setRequestedVideoState(videoProfile.getVideoState());
        }
        call.setSessionModificationState(Call.SessionModificationState.WAITING_FOR_RESPONSE);
        InCallAudioManager.getInstance().onModifyCallClicked(call, videoProfile.getVideoState());
        /*add by yaoyajun for issue ICE2-1755 start*/
        if (videoProfile.getVideoState() == VideoProfile.STATE_AUDIO_ONLY && InCallPresenter.getInstance().getProximitySensor() != null) {
            InCallPresenter.getInstance().getProximitySensor().onInCallShowing(true);
        }
        /*add by yaoyajun for issue ICE2-1755 end*/
    }

    /* [ICE15-1073] add by Sven start, add phone permission check */
    private static boolean hasPhonePermission(Context context) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return true;
    }
    /* [ICE15-1073] add by Sven end */

    /**
     * Checks the boolean flag in config file to figure out if we are going to use Qti extension or
     * not
     */
    public static boolean useExt(Context context) {
        if (context == null) {
            Log.w(context, "Context is null...");
        }
        /* [ICE15-1073] change by Sven start, add phone permission check */
        // baoyilin modified for ICE2-1653 2017-07-06
        return context != null && hasPhonePermission(context) &&
                sCarrierConfigCache.getValue(context, QtiCarrierConfigs.USE_VIDEO_UI_EXTENSIONS);
        /* [ICE15-1073] change by Sven end */
    }

    /**
     * Checks the boolean flag in config file to figure out if custom video ui is required or
     * not
     */
    public static boolean useCustomVideoUi(Context context) {
        if (context == null) {
            Log.w(context, "Context is null...");
        }
        /* [ICE15-1073] change by Sven start, add phone permission check */
        // baoyilin modified for ICE2-1653 2017-07-06
        return context != null && hasPhonePermission(context) &&
                sCarrierConfigCache.getValue(context, QtiCarrierConfigs.USE_CUSTOM_VIDEO_UI);
        /* [ICE15-1073] change by Sven end */
    }

    /**
     * Checks the boolean flag in config file to figure out if transmitting static image
     * in a video call is enabled or not
     */
    public static boolean shallTransmitStaticImage(Context context) {
        if (context == null) {
            Log.w(context, "Context is null...");
        }
        /* [ICE15-1073] change by Sven start, add phone permission check */
        // baoyilin modified for ICE2-1653 2017-07-06
        return context != null && hasPhonePermission(context) &&
                sCarrierConfigCache.getValue(context, QtiCarrierConfigs.TRANSMIT_STATIC_IMAGE);
        /* [ICE15-1073] change by Sven end */
    }

    // baoyilin modified for ICE2-1653 2017-07-06 start
    public static boolean shallShowDataUsageToast(Context context) {
        if (context == null) {
            Log.w(context, "Context is null...");
        }
        return context != null &&
                sCarrierConfigCache.getValue(context, QtiCarrierConfigs.SHOW_DATA_USAGE_TOAST);
    }

    public static boolean shallHidePreviewInVtConference(Context context) {
        if (context == null) {
            Log.w(context, "Context is null...");
        }
        return context != null && sCarrierConfigCache.getValue(context,
                QtiCarrierConfigs.HIDE_PREVIEW_IN_VT_CONFERENCE);
     }

    public static boolean shallShowVideoQualityToast(Context context) {
        if (context == null) {
            Log.w(context, "Context is null...");
        }
        return context != null && sCarrierConfigCache.getValue(context,
                QtiCarrierConfigs.SHOW_VIDEO_QUALITY_TOAST);
    }

    public static boolean shallShowCallSessionEventToast(Context context) {
        if (context == null) {
            Log.w(context, "Context is null...");
        }
        return context != null && sCarrierConfigCache.getValue(context,
                QtiCarrierConfigs.SHOW_CALL_SESSION_EVENT_TOAST);
    }
    // baoyilin modified for ICE2-1653 2017-07-06 end

    /**
     * Checks the boolean flag in config file to figure out if it support preview before the accept
     * video call or not
     */
    public static boolean shallShowPreviewWhileWaiting(Context context) {
        if (context == null) {
            Log.w(context, "Context is null...");
            return false;
        }
        return context.getResources().getBoolean(
                R.bool.config_enable_modify_call_preview);
    }

    /**
     * Returns user options for accepting an incoming video call based on Qti extension flag
     */
    public static int getIncomingCallAnswerOptions(Context context, int videoState,
            boolean withSms) {
        if (!useExt(context)) {
            return withSms ? AnswerFragment.TARGET_SET_FOR_VIDEO_WITH_SMS :
                    AnswerFragment.TARGET_SET_FOR_VIDEO_WITHOUT_SMS;
        } else if (VideoProfile.isBidirectional(videoState)) {
            return withSms ? AnswerFragment.TARGET_SET_FOR_QTI_VIDEO_WITH_SMS :
                    AnswerFragment.TARGET_SET_FOR_QTI_VIDEO_WITHOUT_SMS;
        } else if (VideoProfile.isTransmissionEnabled(videoState)) {
            return withSms ?
                    AnswerFragment.TARGET_SET_FOR_QTI_VIDEO_TRANSMIT_ACCEPT_REJECT_WITH_SMS :
                    AnswerFragment.TARGET_SET_FOR_QTI_VIDEO_TRANSMIT_ACCEPT_REJECT_WITHOUT_SMS;
        } else {
            return withSms ?
                    AnswerFragment.TARGET_SET_FOR_QTI_VIDEO_RECEIVE_ACCEPT_REJECT_WITH_SMS :
                    AnswerFragment.TARGET_SET_FOR_QTI_VIDEO_RECEIVE_ACCEPT_REJECT_WITHOUT_SMS;
        }
    }

    /**
     * Returns the session modification user options based on session modify request video states
     * (current video state and modify request video state)
     */
    public static int getSessionModificationOptions(Context context, int currentVideoState,
            int modifyToVideoState) {
        if (!useExt(context)) {
            return AnswerFragment.TARGET_SET_FOR_VIDEO_ACCEPT_REJECT_REQUEST;
        }

        if (showVideoUpgradeOptions(currentVideoState, modifyToVideoState)) {
            return AnswerFragment.TARGET_SET_FOR_QTI_VIDEO_ACCEPT_REJECT_REQUEST;
        } else if (isEnabled(VideoProfile.STATE_BIDIRECTIONAL, modifyToVideoState)) {
            return AnswerFragment.TARGET_SET_FOR_QTI_BIDIRECTIONAL_VIDEO_ACCEPT_REJECT_REQUEST;
        } else if (isEnabled(VideoProfile.STATE_TX_ENABLED, modifyToVideoState)) {
            return AnswerFragment.TARGET_SET_FOR_QTI_VIDEO_TRANSMIT_ACCEPT_REJECT_REQUEST;
        } else if (isEnabled(VideoProfile.STATE_RX_ENABLED, modifyToVideoState)) {
            return AnswerFragment.TARGET_SET_FOR_QTI_VIDEO_RECEIVE_ACCEPT_REJECT_REQUEST;
        }
        return AnswerFragment.TARGET_SET_FOR_QTI_VIDEO_ACCEPT_REJECT_REQUEST;
    }

    /**
     * Returns true if we are upgrading from Voice to Bidirectional video, false otherwise
     */
    private static boolean showVideoUpgradeOptions(int currentVideoState, int modifyToVideoState) {
        return currentVideoState == VideoProfile.STATE_AUDIO_ONLY &&
                isEnabled(VideoProfile.STATE_BIDIRECTIONAL, modifyToVideoState);
    }

    /**
     * Returns IExtTelephony handle
     */
    public static IExtTelephony getIExtTelephony() {
        // baoyilin modified for ICE2-1653 2017-07-06
        if (mIExtTelephony != null) return mIExtTelephony;

        try {
            Class c = Class.forName("android.os.ServiceManager");
            Method m = c.getMethod("getService",new Class[]{String.class});
            // baoyilin modified for ICE2-1653 2017-07-06
            mIExtTelephony =
                IExtTelephony.Stub.asInterface((IBinder)m.invoke(null, "extphone"));
        } catch (ClassNotFoundException e) {
            Log.e(LOG_TAG, " ex: " + e);
        } catch (IllegalArgumentException e) {
            Log.e(LOG_TAG, " ex: " + e);
        } catch (IllegalAccessException e) {
            Log.e(LOG_TAG, " ex: " + e);
        } catch (InvocationTargetException e) {
            Log.e(LOG_TAG, " ex: " + e);
        } catch (SecurityException e) {
            Log.e(LOG_TAG, " ex: " + e);
        } catch (NoSuchMethodException e) {
            Log.e(LOG_TAG, " ex: " + e);
        }
        // baoyilin modified for ICE2-1653 2017-07-06
        return mIExtTelephony;
    }

    /**
     * returns true if it is emrgency number else false
     */
    public static boolean isEmergencyNumber(String number) {
        boolean isEmergencyNumber = false;

        try {
            isEmergencyNumber = getIExtTelephony().isEmergencyNumber(number);
        } catch (RemoteException ex) {
            Log.e(LOG_TAG, "Exception : " + ex);
        } catch (NullPointerException ex) {
            Log.e(LOG_TAG, "Exception : " + ex);
        }
        return isEmergencyNumber;
    }

    /**
     * returns true if it is local emrgency number else false
     */
    public static boolean isLocalEmergencyNumber(String number) {
        boolean isEmergencyNumber = false;

        try {
            isEmergencyNumber = getIExtTelephony().isLocalEmergencyNumber(number);
        } catch (RemoteException ex) {
            Log.e(LOG_TAG, "Exception : " + ex);
        } catch (NullPointerException ex) {
            Log.e(LOG_TAG, "Exception : " + ex);
        }
        return isEmergencyNumber;
    }

    /**
     * Returns true if TTY mode is enabled, false otherwise
     */
    private static boolean isTtyEnabled(final Context context) {
        if (context == null) {
            Log.w(context, "Context is null...");
            return false;
        }

        final int TTY_MODE_OFF = 0;
        final String PREFERRED_TTY_MODE = "preferred_tty_mode";
        return (android.provider.Settings.Secure.getInt(context.getContentResolver(),
                PREFERRED_TTY_MODE, TTY_MODE_OFF) != TTY_MODE_OFF);
    }

    static int getPhoneId(int subId) {
        try {
            // baoyilin modified for ICE2-1653 2017-07-06 start
            if (sGetPhoneId == null) {
                Class c = Class.forName("android.telephony.SubscriptionManager");
                sGetPhoneId = c.getMethod("getPhoneId", new Class[]{int.class});
            }
            int phoneId = (Integer) sGetPhoneId.invoke(null, subId);
            if (phoneId >= InCallServiceImpl.sPhoneCount || phoneId < 0) {
                phoneId = 0;
            }
            Log.d (LOG_TAG, "phoneid:" + phoneId);
            return phoneId;
        } catch (ClassNotFoundException e) {
            Log.e(LOG_TAG, " ex: " + e);
        } catch (IllegalArgumentException e) {
            Log.e(LOG_TAG, " ex: " + e);
        } catch (IllegalAccessException e) {
            Log.e(LOG_TAG, " ex: " + e);
        } catch (InvocationTargetException e) {
            Log.e(LOG_TAG, " ex: " + e);
        } catch (SecurityException e) {
            Log.e(LOG_TAG, " ex: " + e);
        } catch (NoSuchMethodException e) {
            Log.e(LOG_TAG, " ex: " + e);
        }
        // baoyilin modified for ICE2-1653 2017-07-06 end
        return 0;
    }

    static int getSubId(int phoneId) {
        try {
            // baoyilin modified for ICE2-1653 2017-07-06 start
            if (sGetSubId == null) {
                Class c = Class.forName("android.telephony.SubscriptionManager");
                sGetSubId = c.getMethod("getSubId", new Class[]{int.class});
            }
            int subId[] = (int[]) sGetSubId.invoke(null, phoneId);
            Log.d (LOG_TAG, "getSubId:" + subId[0]);
            if (subId != null && subId.length > 0) {
                return subId[0];
            } else {
                Log.e(LOG_TAG, "subId not valid: " + subId);
            }
        } catch (ClassNotFoundException e) {
            Log.e(LOG_TAG, " ex: " + e);
        } catch (IllegalArgumentException e) {
            Log.e(LOG_TAG, " ex: " + e);
        } catch (IllegalAccessException e) {
            Log.e(LOG_TAG, " ex: " + e);
        } catch (InvocationTargetException e) {
            Log.e(LOG_TAG, " ex: " + e);
        } catch (SecurityException e) {
            Log.e(LOG_TAG, " ex: " + e);
        } catch (NoSuchMethodException e) {
            Log.e(LOG_TAG, " ex: " + e);
        }
        // baoyilin modified for ICE2-1653 2017-07-06 end
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    static void switchToActiveSub(int subId) {
        try {
            IExtTelephony mExtTelephony = getIExtTelephony();
            Log.d(LOG_TAG, "switchToActiveSub, mExtTelephony:" + mExtTelephony);
            mExtTelephony.switchToActiveSub(subId);
        } catch (RemoteException ex) {
            Log.e(LOG_TAG, "Exception : " + ex);
        } catch (NullPointerException ex) {
            Log.e(LOG_TAG, "Exception : " + ex);
        }
    }

    static int getPhoneCount(Context context) {
        TelephonyManager tm = null;
        // baoyilin modified for ICE2-1653 2017-07-06
        if (context != null) {
            tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        }
        if (tm != null) {
            return tm.getPhoneCount();
        } else {
            Log.e(LOG_TAG, "tm is null" );
            return 1;
        }
    }

    static Boolean dsdaEnabled = null;
    static boolean isDsdaEnabled() {
        try {
            if (dsdaEnabled == null) {
                IExtTelephony mExtTelephony = getIExtTelephony();
                Log.d(LOG_TAG, "isDsdaEnabled, mExtTelephony:" + mExtTelephony);
                dsdaEnabled = mExtTelephony.isDsdaEnabled();
                return dsdaEnabled;
            }
        } catch (RemoteException ex) {
            Log.e(LOG_TAG, "Exception : " + ex);
        } catch (NullPointerException ex) {
            Log.e(LOG_TAG, "Exception : " + ex);
        }
        return (dsdaEnabled == null) ? false : dsdaEnabled;
    }

    public static void downgradeToVoiceCall(final Call call) {
        final VideoProfile videoProfile = new VideoProfile(VideoProfile.STATE_AUDIO_ONLY);
        changeToVideoClicked(call, videoProfile);
    }

    /**
     * This method converts the QtiCallConstants' Orientation modes to the ActivityInfo
     * screen orientation mode.
     */
    public static int toUiOrientationMode(int orientationMode) {
        switch(orientationMode) {
            case QtiCallConstants.ORIENTATION_MODE_LANDSCAPE:
                return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            case QtiCallConstants.ORIENTATION_MODE_PORTRAIT:
                return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            case QtiCallConstants.ORIENTATION_MODE_DYNAMIC:
                return ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
            default:
                return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        }
    }

    public static int toVideoIcon(int videoState) {
        if (VideoProfile.isBidirectional(videoState)) {
            return R.drawable.ic_videocam;
        } else if (VideoProfile.isTransmissionEnabled(videoState)) {
            return R.drawable.ic_tx_videocam;
        } else {
            return R.drawable.ic_rx_videocam;
        }
    }

    /**
     * Returns true if the CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO is set to false.
     * Note that - CAPABILITY_SUPPORTS_DOWNGRADE_TO_VOICE_LOCAL and
     * CAPABILITY_SUPPORTS_DOWNGRADE_TO_VOICE_REMOTE maps to
     * CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO
     */
    public static boolean hasVoiceCapabilities(Call call) {
        return call != null &&
                !call.can(android.telecom.Call.Details.CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO);
    }

    /**
     * Returns true if local has the VT Transmit and if remote capability has VT Receive set i.e.
     * Local can transmit and remote can receive
     */
    public static boolean hasTransmitVideoCapabilities(Call call) {
        return call != null &&
                call.can(android.telecom.Call.Details.CAPABILITY_SUPPORTS_VT_LOCAL_TX)
                && call.can(android.telecom.Call.Details.CAPABILITY_SUPPORTS_VT_REMOTE_RX);
    }

    /**
     * Returns true if local has the VT Receive and if remote capability has VT Transmit set i.e.
     * Remote can transmit and local can receive
     */
    public static boolean hasReceiveVideoCapabilities(Call call) {
        return call != null &&
                call.can(android.telecom.Call.Details.CAPABILITY_SUPPORTS_VT_LOCAL_RX)
                && call.can(android.telecom.Call.Details.CAPABILITY_SUPPORTS_VT_REMOTE_TX);
    }

    /**
     * Returns true if both voice and video capabilities (see above) are set
     */
    public static boolean hasVoiceOrVideoCapabilities(Call call) {
        return hasVoiceCapabilities(call) || hasTransmitVideoCapabilities(call)
                || hasReceiveVideoCapabilities(call);
    }
}
