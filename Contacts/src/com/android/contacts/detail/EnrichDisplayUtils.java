/*
Copyright (c) 2016, The Linux Foundation. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of The Linux Foundation nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.contacts.detail;

import android.content.Context;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.util.Log;

import java.util.concurrent.ConcurrentHashMap;

import org.codeaurora.rcscommon.CallComposerData;
import org.codeaurora.rcscommon.RcsManager;

/**
 * Utility class to handle enrich call icon binding
 * to Enrich detailed view. Further handling Enrich call
 * interaction.
 */
public class EnrichDisplayUtils {

    private static final String TAG = "EnrichDisplayUtils";

    private static RcsManager sRcsManager = null;
    private static ConcurrentHashMap<String, Boolean> sEnrichCapabilityMap
            = new ConcurrentHashMap<String, Boolean>();
    private static EnrichUpdateCallback sEnrichUpdateCallback = null;
    public static final boolean DEBUG = false;


    /**
     * Initialize Rcs manager and local callbacks
     *
     * @param appContext
     * @param callback
     * @return
     */
    public static RcsManager initializeEnrichCall (Context appContext,
            EnrichUpdateCallback callback) {
        logd("initializeEnrichCall");

        sRcsManager = RcsManager.getInstance(appContext);
        sRcsManager.initialize();
        sEnrichUpdateCallback = callback;
        return sRcsManager;
    }

    /**
     * Connects with RCS library and verifies if phonenumber
     * defined in contact is enrich call capable.
     * If capable Quick contacts details will be refreshed.
     *
     * @param phoneNumber
     */
    public static void fetchEnrichCallCapabilities(final String phoneNumber) {
        if (TextUtils.isEmpty(phoneNumber) || (sRcsManager == null)) {
            return;
        }

        if (!sRcsManager.isEnrichedCallCapable(
                SubscriptionManager.getDefaultVoiceSubscriptionId())) {
            return;
        }
        sRcsManager.fetchEnrichedCallCapabilities(phoneNumber,
                new org.codeaurora.rcscommon.RichCallCapabilitiesCallback.Stub() {
                    @Override
                    public void onRichCallCapabilitiesFetch(boolean isCapable) {
                        logd("onPostCallCapabilitiesFetch for number: "
                                + phoneNumber + " , isCapable = " + isCapable);

                        sEnrichCapabilityMap.put(phoneNumber, new Boolean(isCapable));

                        if (isCapable) {
                            if (sEnrichUpdateCallback != null) {
                                sEnrichUpdateCallback.updateContact();
                            }
                        }
                    }

                }, SubscriptionManager.getDefaultVoiceSubscriptionId());
    }

    /**
     * Checks if phonenumber defined in contact is enrich call capable.
     * If already defined in local cache due to previous fetch returns status.
     * Else requests for new fetchEnrichCallCapabilities check.
     *
     * @param phoneNumber
     * @return
     */
    public static boolean isEnrichCallCapable(String phoneNumber) {
        if (TextUtils.isEmpty(phoneNumber)) {
            return false;
        }

        boolean result = false;
        if (sEnrichCapabilityMap.containsKey(phoneNumber)) {
            result = sEnrichCapabilityMap.get(phoneNumber);
        } else {
            fetchEnrichCallCapabilities(phoneNumber);
        }

        logd("isEnrichCallCapable for number: "
                + phoneNumber + " result = " + result);

        return result;
    }

    /**
     * Initiates enrich call via enrich call library. This will
     * show attachment activity. Once user attaches and initiates
     * call we will receive attachment via callback.
     * Once CallComposerData is received we will initiate call intent
     * with EXTRA CallComposerData.
     * If user presses back from attachement dialog we will get null
     * data.
     *
     * @param dataId
     * @param phoneNumber
     */
    public static void makeEnrichCall(final int dataId,
            final String phoneNumber) {
        if ((sRcsManager == null) || (sEnrichUpdateCallback == null)) {
            return;
        }

        logd("makeEnrichCall for " + phoneNumber);
        boolean isCallInitiated = sRcsManager.makeEnrichedCall(phoneNumber,
                new org.codeaurora.rcscommon.NewCallComposerCallback.Stub() {
                    public void onNewCallComposer(CallComposerData data) {
                        sEnrichUpdateCallback.endEnrichCall();


                        if (data != null) {
                            logd("Enrich data is :"
                                    + data.toString());

                            sEnrichUpdateCallback.startCallWithEnrichData(
                                    dataId, phoneNumber, data);
                        }

                    }
                }, SubscriptionManager.getDefaultVoiceSubscriptionId());

        if (isCallInitiated) {
            sEnrichUpdateCallback.beginEnrichCall();
        }

    }

    /**
     * Callback needed to update QuickContactActivity
     */
    public interface EnrichUpdateCallback {
        public void updateContact();

        public void beginEnrichCall();

        public void endEnrichCall();

        public void startCallWithEnrichData(int dataId,
                String phoneNumber,
                CallComposerData data);
    }

    /**
     * Clears enrich call data when QuickContact view is
     * closed
     */
    public static void releaseEnrichCall() {
        if (sRcsManager != null) {
            logd("releaseEnrichCall");

            sEnrichCapabilityMap.clear();
            sRcsManager.release();
        }
    }

    private static void logd(String msg, Object... args) {
        if (DEBUG) {
            Log.d(TAG, args == null || args.length == 0 ? msg : String.format(msg, args));
        }
    }

}
