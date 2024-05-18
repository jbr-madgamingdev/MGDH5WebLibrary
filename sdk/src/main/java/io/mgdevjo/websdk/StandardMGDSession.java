/*
 *
 *  * MGD is pleased to support the open source community by making MGDWebSDK available.
 *  *
 *  * Copyright (C) 2024 MAD Gaming Development, a Vertex-Digital company. All rights reserved.
 *  * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *  *
 *  * https://opensource.org/licenses/BSD-3-Clause
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 *  *
 *  *
 *
 */


package io.mgdevjo.websdk;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * A subclass of MGDSession.
 * StandardMGDSession only uses the way of {@link MGDSessionClient#loadUrl(String, Bundle)}
 * (not loadData). When client initiates a resource interception, the user can set response and header
 * information (such as csp) for the kernel.
 *
 * <p>
 * See also {@link QuickMGDSession}
 */
public class StandardMGDSession extends MGDSession implements Handler.Callback {

    /**
     * Log filter
     */
    private static final String TAG = MGDConstants.MGDWEB_SDK_LOG_PREFIX + "StandardMGDSession";

    private static final String TEMPLATE_CHANGE_BUNDLE_PARAMS_REFRESH = "refresh";

    private static final int CLIENT_CORE_MSG_BEGIN = COMMON_MSG_END;

    /**
     * The message will be sent When client is ready.
     */
    private static final int CLIENT_MSG_CLIENT_READY = CLIENT_CORE_MSG_BEGIN + 1;

    private final Object webResponseLock = new Object();

    /**
     * Whether {@link MGDSession#pendingWebResourceStream} is read from cache or not
     */
    private final AtomicBoolean isCachePendingStream = new AtomicBoolean(false);

    StandardMGDSession(String id, String url, MGDSessionConfig config) {
        super(id, url, config);
    }

    public int getSrcResultCode() {
        return srcResultCode;
    }


    @Override
    public boolean handleMessage(Message msg) {

        // fix issue[https://github.com/Tencent/VasMGD/issues/89]
        if (super.handleMessage(msg)) {
            return true; // handled by super class
        }

        switch (msg.what) {
            case CLIENT_MSG_CLIENT_READY: {
                sessionClient.loadUrl(srcUrl, new Bundle());
                break;
            }

            case CLIENT_MSG_NOTIFY_RESULT: {
                if (msg.arg2 == MGD_RESULT_CODE_DATA_UPDATE) {
                    Bundle data = msg.getData();
                    pendingDiffData = data.getString(DATA_UPDATE_BUNDLE_PARAMS_DIFF);
                } else if (msg.arg2 == MGD_RESULT_CODE_TEMPLATE_CHANGE) {
                    Bundle data = msg.getData();
                    if (data.getBoolean(TEMPLATE_CHANGE_BUNDLE_PARAMS_REFRESH, false)) {
                        MGDUtils.log(TAG, Log.INFO, "handleClientCoreMessage_TemplateChange:load url with preload=2, webCallback is null? ->" + (null != diffDataCallback));
                        sessionClient.loadUrl(srcUrl, null);
                    }
                }
                setResult(msg.arg1, msg.arg2, true);
                break;
            }
            case CLIENT_MSG_ON_WEB_READY: {
                diffDataCallback = (MGDDiffDataCallback) msg.obj;
                setResult(srcResultCode, finalResultCode, true);
                break;
            }

            default: {
                if (MGDUtils.shouldLog(Log.DEBUG)) {
                    MGDUtils.log(TAG, Log.DEBUG, "session(" + sId + ") can not  recognize refresh type: " + msg.what);
                }
                return false;
            }

        }
        return true;
    }

    public boolean onClientReady() {
        if (STATE_NONE == sessionState.get()) {
            start();
        }
        if (Looper.getMainLooper() == Looper.myLooper()) {
            sessionClient.loadUrl(srcUrl, new Bundle());
        } else {
            Message msg = mainHandler.obtainMessage(CLIENT_MSG_CLIENT_READY);
            mainHandler.sendMessage(msg);
        }
        return true;
    }

    public boolean onWebReady(MGDDiffDataCallback callback) {
        MGDUtils.log(TAG, Log.INFO, "session(" + sId + ") onWebReady: webCallback has set ? ->" + (null != diffDataCallback));

        if (null != diffDataCallback) {
            diffDataCallback = null;
            MGDUtils.log(TAG, Log.WARN, "session(" + sId + ") onWebReady: call more than once.");
        }

        Message msg = Message.obtain();
        msg.what = CLIENT_MSG_ON_WEB_READY;
        msg.obj = callback;
        mainHandler.sendMessage(msg);

        return true;
    }

    protected Object onRequestResource(String url) {
        if (!isMatchCurrentUrl(url)) {
            return null;
        }

        if (MGDUtils.shouldLog(Log.DEBUG)) {
            MGDUtils.log(TAG, Log.DEBUG, "session(" + sId + ")  onClientRequestResource:url = " + url);
        }

        wasInterceptInvoked.set(true);
        long startTime = System.currentTimeMillis();
        if (sessionState.get() == STATE_RUNNING) {
            synchronized (sessionState) {
                try {
                    if (sessionState.get() == STATE_RUNNING) {
                        MGDUtils.log(TAG, Log.INFO, "session(" + sId + ") now wait for pendingWebResourceStream!");
                        sessionState.wait(30 * 1000);
                    }
                } catch (Throwable e) {
                    MGDUtils.log(TAG, Log.ERROR, "session(" + sId + ") wait for pendingWebResourceStream failed" + e.getMessage());
                }
            }
        } else {
            if (MGDUtils.shouldLog(Log.DEBUG)) {
                MGDUtils.log(TAG, Log.DEBUG, "session(" + sId + ") is not in running state: " + sessionState);
            }
        }

        MGDUtils.log(TAG, Log.INFO, "session(" + sId + ") have pending stream? -> " + (pendingWebResourceStream != null) + ", cost " + (System.currentTimeMillis() - startTime) + "ms.");

        synchronized (webResponseLock) {
            if (null != pendingWebResourceStream) {
                Object webResourceResponse;
                if (!isDestroyedOrWaitingForDestroy()) {
                    String mime = MGDUtils.getMime(srcUrl);
                    webResourceResponse = MGDEngine.getInstance().getRuntime().createWebResourceResponse(mime,
                            isCachePendingStream.get() ? MGDUtils.DEFAULT_CHARSET : getCharsetFromHeaders(),
                            pendingWebResourceStream,
                            isCachePendingStream.get() ? getCacheHeaders() : getHeaders());
                } else {
                    webResourceResponse = null;
                    MGDUtils.log(TAG, Log.ERROR, "session(" + sId + ") onClientRequestResource error: session is destroyed!");

                }
                pendingWebResourceStream = null;
                return webResourceResponse;
            }
        }

        return null;
    }

    @Override
    protected void handleFlow_LoadLocalCache(String localHtml) {
        if (!TextUtils.isEmpty(localHtml)) {
            synchronized (webResponseLock) {
                pendingWebResourceStream = new ByteArrayInputStream(localHtml.getBytes());
                isCachePendingStream.set(true);
            }
            switchState(STATE_RUNNING, STATE_READY, true);
        }

        for (WeakReference<MGDSessionCallback> ref : sessionCallbackList) {
            MGDSessionCallback callback = ref.get();
            if (callback != null) {
                callback.onSessionLoadLocalCache(localHtml);
            }
        }
    }


    /**
     *
     * MGD will always read the new data from the server until the local page finish.
     * If the server data is not read finished MGD will split the read and unread data
     * into a bridgedStream{@link MGDSessionStream}, otherwise all the read data will be
     * encapsulated as an inputStream{@link java.io.ByteArrayInputStream}. When client
     * initiates a resource interception, MGD will provide the bridgedStream or inputStream to
     * the kernel.
     *
     * <p>
     * If need save and separate data, MGD will save the server data and separate the server
     * data to template and data.
     *
     */

    @Override
    protected void handleFlow_TemplateChange(String newHtml) {
        try {
            MGDUtils.log(TAG, Log.INFO, "handleFlow_TemplateChange.");
            long startTime = System.currentTimeMillis();

            String htmlString = newHtml;
            // When serverRsp is empty
            if (TextUtils.isEmpty(htmlString)) {
                pendingWebResourceStream = server.getResponseStream(wasOnPageFinishInvoked);
                if (pendingWebResourceStream == null) {
                    MGDUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleFlow_TemplateChange error:server.getResponseStream = null!");
                    return;
                }

                htmlString = server.getResponseData(false);
            }

            String cacheOffline = server.getResponseHeaderField(MGDSessionConnection.CUSTOM_HEAD_FILED_CACHE_OFFLINE);

            Message msg = mainHandler.obtainMessage(CLIENT_MSG_NOTIFY_RESULT);
            msg.arg1 = msg.arg2 = MGD_RESULT_CODE_TEMPLATE_CHANGE;

            synchronized (webResponseLock) {
                if (!wasInterceptInvoked.get()) {
                    if (!TextUtils.isEmpty(htmlString)) {
                        msg.arg2 = MGD_RESULT_CODE_HIT_CACHE;
                        MGDUtils.log(TAG, Log.INFO, "session(" + sId + ") handleFlow_TemplateChange:oh yeah, templateChange load hit 304.");
                    } else {
                        MGDUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleFlow_TemplateChange error:responseDataTuple not complete!");
                        return;
                    }
                } else {
                    if (MGDUtils.needRefreshPage(cacheOffline)) {
                        Bundle data = new Bundle();
                        data.putBoolean(TEMPLATE_CHANGE_BUNDLE_PARAMS_REFRESH, true);
                        msg.setData(data);
                    } else {
                        msg.arg2 = MGD_RESULT_CODE_HIT_CACHE;
                    }
                }
                isCachePendingStream.set(false);
            }

            mainHandler.sendMessage(msg);

            for (WeakReference<MGDSessionCallback> ref : sessionCallbackList) {
                MGDSessionCallback callback = ref.get();
                if (callback != null) {
                    callback.onSessionTemplateChanged(htmlString);
                }
            }

            if (MGDUtils.shouldLog(Log.DEBUG)) {
                MGDUtils.log(TAG, Log.DEBUG, "session(" + sId + ") read byte stream cost " + (System.currentTimeMillis() - startTime) + " ms, wasInterceptInvoked: " + wasInterceptInvoked.get());
            }

            //save and separate data
            if (MGDUtils.needSaveData(config.SUPPORT_CACHE_CONTROL, cacheOffline, server.getResponseHeaderFields())) {
                switchState(STATE_RUNNING, STATE_READY, true);
                if (!TextUtils.isEmpty(htmlString)) {
                    postTaskToSaveMGDCache(htmlString);
                }
            } else if (OFFLINE_MODE_FALSE.equals(cacheOffline)) {
                MGDUtils.removeSessionCache(id);
                MGDUtils.log(TAG, Log.INFO, "handleClientCoreMessage_TemplateChange:offline mode is 'false', so clean cache.");
            } else {
                MGDUtils.log(TAG, Log.INFO, "session(" + sId + ") handleFlow_TemplateChange:offline->" + cacheOffline + " , so do not need cache to file.");
            }

        } catch (Throwable e) {
            MGDUtils.log(TAG, Log.DEBUG, "session(" + sId + ") handleFlow_TemplateChange error:" + e.getMessage());
        }
    }

    @Override
    protected void handleFlow_HttpError(int responseCode) {
        for (WeakReference<MGDSessionCallback> ref : sessionCallbackList) {
            MGDSessionCallback callback = ref.get();
            if (callback != null) {
                callback.onSessionHttpError(responseCode);
            }
        }
    }

    @Override
    protected void handleFlow_ServiceUnavailable() {
        for (WeakReference<MGDSessionCallback> ref : sessionCallbackList) {
            MGDSessionCallback callback = ref.get();
            if (callback != null) {
                callback.onSessionUnAvailable();
            }
        }
    }

    /**
     *
     * MGD will always read the new data from the server until client initiates a resource interception
     * If the server data is not read finished MGD will split the read and unread data into a
     * bridgedStream{@link MGDSessionStream}, otherwise all the read data will be encapsulated as an
     * inputStream{@link java.io.ByteArrayInputStream}. When client initiates a resource interception,
     * MGD will provide the bridgedStream or inputStream to the kernel.
     *
     * <p>
     * If need save and separate data, MGD will save the server data and separate the server data to template and data
     *
     */
    protected void handleFlow_FirstLoad() {
        synchronized (webResponseLock) {
            pendingWebResourceStream = server.getResponseStream(wasInterceptInvoked);
        }

        if (null == pendingWebResourceStream) {
            MGDUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleFlow_FirstLoad error:server.getResponseStream is null!");
            return;
        }

        Message msg = mainHandler.obtainMessage(CLIENT_MSG_NOTIFY_RESULT);
        msg.arg1 = msg.arg2 = MGD_RESULT_CODE_FIRST_LOAD;
        String htmlString = server.getResponseData(false);

        if (!TextUtils.isEmpty(htmlString)) {
            try {
                msg.arg2 = MGD_RESULT_CODE_HIT_CACHE;
                MGDUtils.log(TAG, Log.INFO, "session(" + sId + ") handleFlow_FirstLoad:oh yeah, first load hit 304.");
            } catch (Throwable e) {
                synchronized (webResponseLock) {
                    pendingWebResourceStream = null;
                }
                MGDUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleFlow_FirstLoad error:" + e.getMessage() + ".");
            }
        }

        isCachePendingStream.set(false);

        mainHandler.sendMessage(msg);

        for (WeakReference<MGDSessionCallback> ref : sessionCallbackList) {
            MGDSessionCallback callback = ref.get();
            if (callback != null) {
                callback.onSessionFirstLoad(htmlString);
            }
        }

        boolean hasCacheData = !TextUtils.isEmpty(htmlString);
        MGDUtils.log(TAG, Log.INFO, "session(" + sId + ") handleFlow_FirstLoad:hasCacheData=" + hasCacheData + ".");

        String cacheOffline = server.getResponseHeaderField(MGDSessionConnection.CUSTOM_HEAD_FILED_CACHE_OFFLINE);
        if (MGDUtils.needSaveData(config.SUPPORT_CACHE_CONTROL, cacheOffline, server.getResponseHeaderFields())) {
            if (hasCacheData) {
                switchState(STATE_RUNNING, STATE_READY, true);

                postTaskToSaveMGDCache(htmlString);
            }
        } else {
            MGDUtils.log(TAG, Log.INFO, "session(" + sId + ") handleFlow_FirstLoad:offline->" + cacheOffline + " , so do not need cache to file.");
        }
    }

    /**
     *
     * MGD obtains the difference data between the server and the local data first,then MGD will
     * build the template and server data into html.If client did not load url before, the new html
     * will be encapsulated as an inputStream{@link java.io.ByteArrayInputStream},When client initiates
     * a resource interception, MGD provides the inputStream to the kernel.
     *
     * If client did load url before, MGD provides the diff data to page when page obtains the diff data.
     *
     * @param serverRsp Server response data.
     */
    protected void handleFlow_DataUpdate(String serverRsp) {
        MGDUtils.log(TAG, Log.INFO, "session(" + sId + ") handleFlow_DataUpdate: start.");

        try {
            String htmlSha1 = null;
            String htmlString = null;

            if (TextUtils.isEmpty(serverRsp)) {
                serverRsp = server.getResponseData(true);
            } else {
                htmlString = server.getResponseData(false);
                htmlSha1 = server.getResponseHeaderField(MGDSessionConnection.CUSTOM_HEAD_FILED_HTML_SHA1);
            }

            if (TextUtils.isEmpty(serverRsp)) {
                return;
            }

            final String eTag = server.getResponseHeaderField(getCustomHeadFieldEtagKey());
            final String templateTag = server.getResponseHeaderField(MGDSessionConnection.CUSTOM_HEAD_FILED_TEMPLATE_TAG);

            String cacheOffline = server.getResponseHeaderField(MGDSessionConnection.CUSTOM_HEAD_FILED_CACHE_OFFLINE);

            long startTime = System.currentTimeMillis();
            JSONObject serverRspJson = new JSONObject(serverRsp);
            final JSONObject serverDataJson = serverRspJson.optJSONObject("data");
            JSONObject diffDataJson = MGDUtils.getDiffData(id, serverDataJson);
            Bundle diffDataBundle = new Bundle();
            if (null != diffDataJson) {
                diffDataBundle.putString(DATA_UPDATE_BUNDLE_PARAMS_DIFF, diffDataJson.toString());
            } else {
                MGDUtils.log(TAG, Log.ERROR, "handleFlow_DataUpdate:getDiffData error.");
                MGDEngine.getInstance().getRuntime().notifyError(sessionClient, srcUrl, MGDConstants.ERROR_CODE_MERGE_DIFF_DATA_FAIL);
            }
            if (MGDUtils.shouldLog(Log.DEBUG)) {
                MGDUtils.log(TAG, Log.DEBUG, "handleFlow_DataUpdate:getDiffData cost " + (System.currentTimeMillis() - startTime) + " ms.");
            }

            if (MGDUtils.needRefreshPage(cacheOffline)) {
                if (MGDUtils.shouldLog(Log.INFO)) {
                    MGDUtils.log(TAG, Log.INFO, "handleFlow_DataUpdate:loadData was invoked, quick notify web data update.");
                }
                Message msg = mainHandler.obtainMessage(CLIENT_MSG_NOTIFY_RESULT);
                msg.arg1 = msg.arg2 = MGD_RESULT_CODE_DATA_UPDATE;
                msg.setData(diffDataBundle);
                mainHandler.sendMessage(msg);
            }

            for (WeakReference<MGDSessionCallback> ref : sessionCallbackList) {
                MGDSessionCallback callback = ref.get();
                if (callback != null) {
                    callback.onSessionDataUpdated(serverRsp);
                }
            }

            startTime = System.currentTimeMillis();

            if (TextUtils.isEmpty(htmlString)) {
                htmlSha1 = serverRspJson.optString("html-sha1");
                htmlString = MGDUtils.buildHtml(id, serverDataJson, htmlSha1, serverRsp.length());
            }

            if (MGDUtils.shouldLog(Log.DEBUG)) {
                MGDUtils.log(TAG, Log.DEBUG, "handleFlow_DataUpdate:buildHtml cost " + (System.currentTimeMillis() - startTime) + " ms.");
            }

            if (!TextUtils.isEmpty(htmlString) && !wasInterceptInvoked.get() && MGDUtils.needRefreshPage(cacheOffline)) {
                synchronized (webResponseLock) {
                    pendingWebResourceStream = new ByteArrayInputStream(htmlString.getBytes());
                    isCachePendingStream.set(false);
                }
                MGDUtils.log(TAG, Log.INFO, "session(" + sId + ") handleFlow_DataUpdate:oh yeah, dataUpdate load hit 304.");
                mainHandler.removeMessages(CLIENT_MSG_NOTIFY_RESULT);
                Message msg = mainHandler.obtainMessage(CLIENT_MSG_NOTIFY_RESULT);
                msg.arg1 = MGD_RESULT_CODE_DATA_UPDATE;
                msg.arg2 = MGD_RESULT_CODE_HIT_CACHE;
                mainHandler.sendMessage(msg);
            }

            if (TextUtils.isEmpty(htmlString)) {
                MGDEngine.getInstance().getRuntime().notifyError(sessionClient, srcUrl, MGDConstants.ERROR_CODE_BUILD_HTML_ERROR);
            }

            if (null == diffDataJson || null == htmlString
                    || !MGDUtils.needSaveData(config.SUPPORT_CACHE_CONTROL, cacheOffline, server.getResponseHeaderFields())) {
                MGDUtils.log(TAG, Log.INFO, "session(" + sId + ") handleFlow_DataUpdate: clean session cache.");
                MGDUtils.removeSessionCache(id);
            }

            switchState(STATE_RUNNING, STATE_READY, true);

            Thread.yield();

            startTime = System.currentTimeMillis();
            Map<String, List<String>> headers = server.getResponseHeaderFields();

            for (WeakReference<MGDSessionCallback> ref : sessionCallbackList) {
                MGDSessionCallback callback = ref.get();
                if (callback != null) {
                    callback.onSessionSaveCache(htmlString, null, serverDataJson.toString());
                }
            }

            if (MGDUtils.saveSessionFiles(id, htmlString, null, serverDataJson.toString(), headers)) {
                long htmlSize = new File(MGDFileUtils.getMGDHtmlPath(id)).length();
                MGDUtils.saveMGDData(id, eTag, templateTag, htmlSha1, htmlSize, headers);
                MGDUtils.log(TAG, Log.INFO, "session(" + sId + ") handleFlow_DataUpdate: finish save session cache, cost " + (System.currentTimeMillis() - startTime) + " ms.");

            } else {
                MGDUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleFlow_DataUpdate: save session files fail.");
                MGDEngine.getInstance().getRuntime().notifyError(sessionClient, srcUrl, MGDConstants.ERROR_CODE_WRITE_FILE_FAIL);
            }

        } catch (Throwable e) {
            MGDUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleFlow_DataUpdate error:" + e.getMessage());
        }

    }
}
