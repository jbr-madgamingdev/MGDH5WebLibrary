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
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * A subclass of MGDSession.
 * QuickMGDSession mainly uses {@link MGDSessionClient#loadDataWithBaseUrlAndHeader(String, String, String, String, String, HashMap)}
 * to load data. Sometime, it will use {@link MGDSessionClient#loadUrl(String, Bundle)} instead. By using
 * {@link MGDSessionClient#loadDataWithBaseUrlAndHeader(String, String, String, String, String, HashMap)}, WebView will
 * quickly load web pages without the network affecting.
 *
 * <p>
 *  ATTENTION:
 *  Standard WebView don't have head information (such as csp) when it calls
 *  {@link MGDSessionClient#loadDataWithBaseUrlAndHeader(String, String, String, String, String, HashMap)} method.
 *  So this session mode may cause a security risk. However, you can put the csp contents into the html to avoid this risk caused by the lack of csp.
 *
 * <p>
 * See also {@link StandardMGDSession}
 */

public class QuickMGDSession extends MGDSession implements Handler.Callback {

    /**
     * Log filter
     */
    private static final String TAG = MGDConstants.MGDWEB_SDK_LOG_PREFIX + "QuickMGDSession";

    /**
     * The beginning of message
     */
    private static final int CLIENT_CORE_MSG_BEGIN = COMMON_MSG_END;

    /**
     * Message type : this message is sent after verify local data.
     * This message may be removed before other messages being sent,such as <code>CLIENT_CORE_MSG_DATA_UPDATE</code>
     * message.
     */
    private static final int CLIENT_CORE_MSG_PRE_LOAD = CLIENT_CORE_MSG_BEGIN + 1;

    /**
     * Message type : this message is sent after http(s) response when local
     * data does not exist.
     */
    private static final int CLIENT_CORE_MSG_FIRST_LOAD = CLIENT_CORE_MSG_BEGIN + 2;

    /**
     * Message type : this message is sent after http(s) response when the
     * local template content is the same as the server template.
     */
    private static final int CLIENT_CORE_MSG_DATA_UPDATE = CLIENT_CORE_MSG_BEGIN + 3;

    /**
     * Message type : this message is sent after http(s) response when the
     * local template content is not the same as the server template.
     */
    private static final int CLIENT_CORE_MSG_TEMPLATE_CHANGE = CLIENT_CORE_MSG_BEGIN + 4;

    /**
     * Message type : this message is sent when http(s) connect fail.
     */
    private static final int CLIENT_CORE_MSG_CONNECTION_ERROR = CLIENT_CORE_MSG_BEGIN + 5;

    /**
     * Message type : this message is sent when the "cache-offline" content
     * of the header information is "http". This means MGD server unavailable.
     */
    private static final int CLIENT_CORE_MSG_SERVICE_UNAVAILABLE = CLIENT_CORE_MSG_BEGIN + 6;

    /**
     * The end of message. The message which is not in the message range would not be handle.
     */
    private static final int CLIENT_CORE_MSG_END = CLIENT_CORE_MSG_SERVICE_UNAVAILABLE + 1;

    /**
     * The pending message : before client ready, MGD will store the latest message to pendingClientCoreMessage.
     * If this pendingClientCoreMessage not null, MGD will handle this message when client ready.
     */
    private Message pendingClientCoreMessage;

    /**
     * Preload message type : local data does not exist.
     */
    private static final int PRE_LOAD_NO_CACHE = 1;

    /**
     * Preload message type : local data exists.
     */
    private static final int PRE_LOAD_WITH_CACHE = 2;

    /**
     * First loaded message type : server data has not finished reading when send this message.
     */
    private static final int FIRST_LOAD_NO_DATA = 1;

    /**
     * First loaded message type : server data has finished reading when send this message.
     */
    private static final int FIRST_LOAD_WITH_DATA = 2;

    /**
     * Whether refresh page content when template change or not.
     */
    private static final int TEMPLATE_CHANGE_REFRESH = 1;

    /**
     * Whether client invokes loadUrl method or not.
     */
    private final AtomicBoolean wasLoadUrlInvoked = new AtomicBoolean(false);

    /**
     * Whether client invokes loadDataWithBaseUrlAndHeader method or not.
     */
    private final AtomicBoolean wasLoadDataInvoked = new AtomicBoolean(false);


    QuickMGDSession(String id, String url, MGDSessionConfig config) {
        super(id, url, config);
    }

    @Override
    public boolean handleMessage(Message msg) {

        if (super.handleMessage(msg)) {
            return true; // handled by super class
        }

        if (CLIENT_CORE_MSG_BEGIN < msg.what && msg.what < CLIENT_CORE_MSG_END && !clientIsReady.get()) {
            pendingClientCoreMessage = Message.obtain(msg);
            MGDUtils.log(TAG, Log.INFO, "session(" + sId + ") handleMessage: client not ready, core msg = " + msg.what + ".");
            return true;
        }

        switch (msg.what) {
            case CLIENT_CORE_MSG_PRE_LOAD:
                handleClientCoreMessage_PreLoad(msg);
                break;
            case CLIENT_CORE_MSG_FIRST_LOAD:
                handleClientCoreMessage_FirstLoad(msg);
                break;
            case CLIENT_CORE_MSG_CONNECTION_ERROR:
                handleClientCoreMessage_ConnectionError(msg);
                break;
            case CLIENT_CORE_MSG_SERVICE_UNAVAILABLE:
                handleClientCoreMessage_ServiceUnavailable(msg);
                break;
            case CLIENT_CORE_MSG_DATA_UPDATE:
                handleClientCoreMessage_DataUpdate(msg);
                break;
            case CLIENT_CORE_MSG_TEMPLATE_CHANGE:
                handleClientCoreMessage_TemplateChange(msg);
                break;
            case CLIENT_MSG_NOTIFY_RESULT:
                setResult(msg.arg1, msg.arg2, true);
                break;
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

    /**
     * Handle the connection error message. Client will invoke loadUrl method if this method is not
     * invoked before, or do nothing.
     *
     * @param msg The message
     */
    private void handleClientCoreMessage_ConnectionError(Message msg) {
        if (wasLoadUrlInvoked.compareAndSet(false, true)) {
            if (MGDUtils.shouldLog(Log.INFO)) {
                MGDUtils.log(TAG, Log.INFO, "handleClientCoreMessage_ConnectionError: load src url.");
            }
            sessionClient.loadUrl(srcUrl, null);
        }
    }

    /**
     * Handle the server unavailable message. Client will invoke loadUrl method if this method is not
     * invoked before, or do nothing.
     *
     * @param msg The message
     */
    private void handleClientCoreMessage_ServiceUnavailable(Message msg) {
        if (wasLoadUrlInvoked.compareAndSet(false, true)) {
            if (MGDUtils.shouldLog(Log.INFO)) {
                MGDUtils.log(TAG, Log.INFO, "handleClientCoreMessage_ServiceUnavailable:load src url.");
            }
            sessionClient.loadUrl(srcUrl, null);
        }
    }

    /**
     * Handle the preload message. If the type of this message is <code>PRE_LOAD_NO_CACHE</code>  and client did not
     * initiate request for load url,client will invoke loadUrl method. If the type of this message is
     * <code>PRE_LOAD_WITH_CACHE</code> and and client did not initiate request for loadUrl,client will load local data.
     *
     * @param msg The message
     */
    private void handleClientCoreMessage_PreLoad(Message msg) {
        switch (msg.arg1) {
            case PRE_LOAD_NO_CACHE: {
                if (wasLoadUrlInvoked.compareAndSet(false, true)) {
                    MGDUtils.log(TAG, Log.INFO, "session(" + sId + ") handleClientCoreMessage_PreLoad:PRE_LOAD_NO_CACHE load url.");
                    sessionClient.loadUrl(srcUrl, null);
                } else {
                    MGDUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleClientCoreMessage_PreLoad:wasLoadUrlInvoked = true.");
                }
            }
            break;
            case PRE_LOAD_WITH_CACHE: {
                if (wasLoadDataInvoked.compareAndSet(false, true)) {
                    MGDUtils.log(TAG, Log.INFO, "session(" + sId + ") handleClientCoreMessage_PreLoad:PRE_LOAD_WITH_CACHE load data.");
                    String html = (String) msg.obj;
                    sessionClient.loadDataWithBaseUrlAndHeader(srcUrl, html, "text/html",
                            MGDUtils.DEFAULT_CHARSET, srcUrl, getCacheHeaders());
                } else {
                    MGDUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleClientCoreMessage_PreLoad:wasLoadDataInvoked = true.");
                }
            }
            break;
        }
    }

    /**
     * Handle first load message.If the type of this message is <code>FIRST_LOAD_NO_DATA</code> and client did not
     * initiated request for load url,the quickSonicSession will do nothing but assign a value to MGD by invoke
     * setResult method.
     *
     * If the type of this message is <code>FIRST_LOAD_WITH_DATA</code> and client did not initiated request for load url,
     * client will load the html content that comes from server . In this case, the value of <code>finalResultCode</code>
     * will be set as <code>MGD_RESULT_CODE_HIT_CACHE</code>.If client has a request for load url before,the value of
     * <code>finalResultCode</code> will be set as <code>MGD_RESULT_CODE_FIRST_LOAD</code>.
     *
     * @param msg The message
     */
    private void handleClientCoreMessage_FirstLoad(Message msg) {
        switch (msg.arg1) {
            case FIRST_LOAD_NO_DATA: {
                if (wasInterceptInvoked.get()) {
                    MGDUtils.log(TAG, Log.INFO, "session(" + sId + ") handleClientCoreMessage_FirstLoad:FIRST_LOAD_NO_DATA.");
                    setResult(MGD_RESULT_CODE_FIRST_LOAD, MGD_RESULT_CODE_FIRST_LOAD, true);
                } else {
                    MGDUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleClientCoreMessage_FirstLoad:url was not invoked.");
                }
            }
            break;
            case FIRST_LOAD_WITH_DATA: {
                if (wasLoadUrlInvoked.compareAndSet(false, true)) {
                    MGDUtils.log(TAG, Log.INFO, "session(" + sId + ") handleClientCoreMessage_FirstLoad:oh yeah, first load hit 304.");
                    sessionClient.loadDataWithBaseUrlAndHeader(srcUrl, (String) msg.obj, "text/html",
                            getCharsetFromHeaders(), srcUrl, getHeaders());
                    setResult(MGD_RESULT_CODE_FIRST_LOAD, MGD_RESULT_CODE_HIT_CACHE, false);
                } else {
                    MGDUtils.log(TAG, Log.INFO, "session(" + sId + ") FIRST_LOAD_WITH_DATA load url was invoked.");
                    setResult(MGD_RESULT_CODE_FIRST_LOAD, MGD_RESULT_CODE_FIRST_LOAD, true);
                }
            }
            break;
        }
    }

    /**
     * Handle data update message. If client had loaded local data before, sonic will store the difference data between
     * server and local data to <code>pendingDiffData</code>. The value of <code>finalResultCode</code> will be set as
     * <code>MGD_RESULT_CODE_DATA_UPDATE</code>.
     *
     * If client had not loaded local data before, client will load new html content which is from the combination of local
     * template content and server data.The value of <code>finalResultCode</code> will be set as <code>MGD_RESULT_CODE_HIT_CACHE</code>.
     *
     * @param msg The message
     */
    private void handleClientCoreMessage_DataUpdate(Message msg) {
        String htmlString = (String) msg.obj;
        String diffData = msg.getData().getString(DATA_UPDATE_BUNDLE_PARAMS_DIFF);
        if (wasLoadDataInvoked.get()) {
            pendingDiffData = diffData;
            if (!TextUtils.isEmpty(diffData)) {
                MGDUtils.log(TAG, Log.INFO, "handleClientCoreMessage_DataUpdate:try to notify web callback.");
                setResult(MGD_RESULT_CODE_DATA_UPDATE, MGD_RESULT_CODE_DATA_UPDATE, true);
            } else {
                MGDUtils.log(TAG, Log.INFO, "handleClientCoreMessage_DataUpdate:diffData is null, cache-offline = store , do not refresh.");
                setResult(MGD_RESULT_CODE_DATA_UPDATE, MGD_RESULT_CODE_HIT_CACHE, true);
            }
            return;
        } else {
            if (!TextUtils.isEmpty(htmlString)) {
                MGDUtils.log(TAG, Log.INFO, "handleClientCoreMessage_DataUpdate:oh yeah data update hit 304, now clear pending data ->" + (null != pendingDiffData) + ".");
                pendingDiffData = null;
                sessionClient.loadDataWithBaseUrlAndHeader(srcUrl, htmlString, "text/html", getCharsetFromHeaders(), srcUrl, getHeaders());
                setResult(MGD_RESULT_CODE_DATA_UPDATE, MGD_RESULT_CODE_HIT_CACHE, false);
                return;
            }
        }
        MGDUtils.log(TAG, Log.ERROR, "handleClientCoreMessage_DataUpdate error:call load url.");
        sessionClient.loadUrl(srcUrl, null);
        setResult(MGD_RESULT_CODE_DATA_UPDATE, MGD_RESULT_CODE_FIRST_LOAD, false);
    }

    /**
     * Handle template change message.If client had loaded local data before and the page need refresh,
     * client will load data or loadUrl according to whether the message has latest data. And the
     * <code>finalResultCode</code> will be set as <code>SONIC_RESULT_CODE_TEMPLATE_CHANGE</code>.
     *
     * If client had not loaded local data before, client will load the latest html content which comes
     * from server and the <code>finalResultCode</code> will be set as <code>SONIC_RESULT_CODE_HIT_CACHE</code>.
     *
     * @param msg The message
     */
    private void handleClientCoreMessage_TemplateChange(Message msg) {
        MGDUtils.log(TAG, Log.INFO, "handleClientCoreMessage_TemplateChange wasLoadDataInvoked = " + wasLoadDataInvoked.get() + ",msg arg1 = " + msg.arg1);

        if (wasLoadDataInvoked.get()) {
            if (TEMPLATE_CHANGE_REFRESH == msg.arg1) {
                String html = (String) msg.obj;
                if (TextUtils.isEmpty(html)) {
                    MGDUtils.log(TAG, Log.INFO, "handleClientCoreMessage_TemplateChange:load url with preload=2, webCallback is null? ->" + (null != diffDataCallback));
                    sessionClient.loadUrl(srcUrl, null);
                } else {
                    MGDUtils.log(TAG, Log.INFO, "handleClientCoreMessage_TemplateChange:load data.");
                    sessionClient.loadDataWithBaseUrlAndHeader(srcUrl, html, "text/html",
                            getCharsetFromHeaders(), srcUrl, getHeaders());
                }
                setResult(MGD_RESULT_CODE_TEMPLATE_CHANGE, MGD_RESULT_CODE_TEMPLATE_CHANGE, false);
            } else {
                MGDUtils.log(TAG, Log.INFO, "handleClientCoreMessage_TemplateChange:not refresh.");
                setResult(MGD_RESULT_CODE_TEMPLATE_CHANGE, MGD_RESULT_CODE_HIT_CACHE, true);
            }
        } else {
            MGDUtils.log(TAG, Log.INFO, "handleClientCoreMessage_TemplateChange:oh yeah template change hit 304.");
            if (msg.obj instanceof String) {
                String html = (String) msg.obj;
                sessionClient.loadDataWithBaseUrlAndHeader(srcUrl, html, "text/html",
                        getCharsetFromHeaders(), srcUrl, getHeaders());
                setResult(MGD_RESULT_CODE_TEMPLATE_CHANGE, MGD_RESULT_CODE_HIT_CACHE, false);
            } else {
                MGDUtils.log(TAG, Log.ERROR, "handleClientCoreMessage_TemplateChange error:call load url.");
                sessionClient.loadUrl(srcUrl, null);
                setResult(MGD_RESULT_CODE_TEMPLATE_CHANGE, MGD_RESULT_CODE_FIRST_LOAD, false);
            }
        }
        diffDataCallback = null;
        mainHandler.removeMessages(CLIENT_MSG_ON_WEB_READY);
    }

    /**
     * Handle load local cache of html if exist.
     * This handle is called before connection.
     *
     * @param cacheHtml local cache of html
     */
    @Override
    protected void handleFlow_LoadLocalCache(String cacheHtml) {
        Message msg = mainHandler.obtainMessage(CLIENT_CORE_MSG_PRE_LOAD);
        if (!TextUtils.isEmpty(cacheHtml)) {
            msg.arg1 = PRE_LOAD_WITH_CACHE;
            msg.obj = cacheHtml;
        } else {
            MGDUtils.log(TAG, Log.INFO, "session(" + sId + ") runSonicFlow has no cache, do first load flow.");
            msg.arg1 = PRE_LOAD_NO_CACHE;
        }
        mainHandler.sendMessage(msg);

        for (WeakReference<MGDSessionCallback> ref : sessionCallbackList) {
            MGDSessionCallback callback = ref.get();
            if (callback != null) {
                callback.onSessionLoadLocalCache(cacheHtml);
            }
        }
    }

    public boolean onWebReady(MGDDiffDataCallback callback) {
        MGDUtils.log(TAG, Log.INFO, "session(" + sId + ") onWebReady: webCallback has set ? ->" + (null != diffDataCallback));

        if (null != diffDataCallback) {
            this.diffDataCallback = null;
            MGDUtils.log(TAG, Log.WARN, "session(" + sId + ") onWebReady: call more than once.");
        }

        Message msg = Message.obtain();
        msg.what = CLIENT_MSG_ON_WEB_READY;
        msg.obj = callback;
        mainHandler.sendMessage(msg);

        return true;
    }

    public boolean onClientReady() {
        if (clientIsReady.compareAndSet(false, true)) {
            MGDUtils.log(TAG, Log.INFO, "session(" + sId + ") onClientReady: have pending client core message ? -> " + (null != pendingClientCoreMessage) + ".");
            if (null != pendingClientCoreMessage) {
                Message message = pendingClientCoreMessage;
                pendingClientCoreMessage = null;
                handleMessage(message);
            } else if (STATE_NONE == sessionState.get()) {
                start();
            }
            return true;
        }
        return false;
    }

    protected Object onRequestResource(String url) {
        if (wasInterceptInvoked.get() || !isMatchCurrentUrl(url)) {
            return null;
        }

        if (!wasInterceptInvoked.compareAndSet(false, true)) {
            MGDUtils.log(TAG, Log.ERROR, "session(" + sId + ")  onClientRequestResource error:Intercept was already invoked, url = " + url);
            return null;
        }

        if (MGDUtils.shouldLog(Log.DEBUG)) {
            MGDUtils.log(TAG, Log.DEBUG, "session(" + sId + ")  onClientRequestResource:url = " + url);
        }

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

        if (null != pendingWebResourceStream) {
            Object webResourceResponse;
            if (!isDestroyedOrWaitingForDestroy()) {
                String mime = MGDUtils.getMime(srcUrl);
                webResourceResponse = MGDEngine.getInstance().getRuntime().createWebResourceResponse(mime,
                        getCharsetFromHeaders(), pendingWebResourceStream, getHeaders());
            } else {
                webResourceResponse = null;
                MGDUtils.log(TAG, Log.ERROR, "session(" + sId + ") onClientRequestResource error: session is destroyed!");

            }
            pendingWebResourceStream = null;
            return webResourceResponse;
        }

        return null;
    }

    protected void handleFlow_HttpError(int responseCode){
        if (config.RELOAD_IN_BAD_NETWORK) {
            mainHandler.removeMessages(CLIENT_CORE_MSG_PRE_LOAD);
            Message msg = mainHandler.obtainMessage(CLIENT_CORE_MSG_CONNECTION_ERROR);
            msg.arg1 = responseCode;
            mainHandler.sendMessage(msg);
        }
        for (WeakReference<MGDSessionCallback> ref : sessionCallbackList) {
            MGDSessionCallback callback = ref.get();
            if (callback != null) {
                callback.onSessionHttpError(responseCode);
            }
        }
    }

    protected void handleFlow_ServiceUnavailable(){
        mainHandler.removeMessages(CLIENT_CORE_MSG_PRE_LOAD);
        Message msg = mainHandler.obtainMessage(CLIENT_CORE_MSG_SERVICE_UNAVAILABLE);
        mainHandler.sendMessage(msg);

    }

    /**
     *
     * In this case MGD will always read the new data from the server until the local page finish.
     * If the server data is read finished, sonic will send a <code>CLIENT_CORE_MSG_TEMPLATE_CHANGE</code>
     * message.
     *
     * If the server data is not read finished sonic will split the read and unread data into a
     * bridgedStream{@link MGDSessionStream}. When the client initiates a resource interception,
     * MGD will provide the bridgedStream to the kernel.
     *
     * <p>
     * If need save and separate data, sonic will save the server data and separate the server data to template and data.
     *  @param newHtml html content from server
     *
     */
    protected void handleFlow_TemplateChange(String newHtml) {
        try {
            MGDUtils.log(TAG, Log.INFO, "handleFlow_TemplateChange.");
            String htmlString = newHtml;
            long startTime = System.currentTimeMillis();

            // When serverRsp is empty
            if (TextUtils.isEmpty(htmlString)) {
                pendingWebResourceStream = server.getResponseStream(wasOnPageFinishInvoked);
                if (pendingWebResourceStream == null) {
                    MGDUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleFlow_TemplateChange error:server.getResponseStream = null!");
                    return;
                }

                htmlString = server.getResponseData(clientIsReload.get());
            }

            String cacheOffline = server.getResponseHeaderField(MGDSessionConnection.CUSTOM_HEAD_FILED_CACHE_OFFLINE);

            if (!clientIsReload.get()) {
                // send CLIENT_CORE_MSG_TEMPLATE_CHANGE message
                mainHandler.removeMessages(CLIENT_CORE_MSG_PRE_LOAD);
                Message msg = mainHandler.obtainMessage(CLIENT_CORE_MSG_TEMPLATE_CHANGE);
                msg.obj = htmlString;
                if (!OFFLINE_MODE_STORE.equals(cacheOffline)) {
                    msg.arg1 = TEMPLATE_CHANGE_REFRESH;
                }
                mainHandler.sendMessage(msg);
            } else {
                Message msg = mainHandler.obtainMessage(CLIENT_MSG_NOTIFY_RESULT);
                msg.arg1 = MGD_RESULT_CODE_TEMPLATE_CHANGE;
                msg.arg2 = MGD_RESULT_CODE_TEMPLATE_CHANGE;
                mainHandler.sendMessage(msg);
            }

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

    /**
     *
     * In this case MGD will always read the new data from the server until the client
     * initiates a resource interception.
     *
     * If the server data is read finished, MGD will send <code>CLIENT_CORE_MSG_FIRST_LOAD</code>
     * message with the new html content from server.
     *
     * If the server data is not read finished MGD will split the read and unread data into
     * a bridgedStream{@link MGDSessionStream}.When client initiates a resource interception,
     * MGD will provide the bridgedStream to the kernel.
     *
     * <p>
     * If need save and separate data, MGD will save the server data and separate the server data
     * to template and data.
     *
     */
    protected void handleFlow_FirstLoad() {
        pendingWebResourceStream = server.getResponseStream(wasInterceptInvoked);
        if (null == pendingWebResourceStream) {
            MGDUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleFlow_FirstLoad error:server.getResponseStream is null!");
            return;
        }

        String htmlString = server.getResponseData(false);


        boolean hasCompletionData = !TextUtils.isEmpty(htmlString);
        MGDUtils.log(TAG, Log.INFO, "session(" + sId + ") handleFlow_FirstLoad:hasCompletionData=" + hasCompletionData + ".");

        mainHandler.removeMessages(CLIENT_CORE_MSG_PRE_LOAD);
        Message msg = mainHandler.obtainMessage(CLIENT_CORE_MSG_FIRST_LOAD);
        msg.obj = htmlString;
        msg.arg1 = hasCompletionData ? FIRST_LOAD_WITH_DATA : FIRST_LOAD_NO_DATA;
        mainHandler.sendMessage(msg);
        for (WeakReference<MGDSessionCallback> ref : sessionCallbackList) {
            MGDSessionCallback callback = ref.get();
            if (callback != null) {
                callback.onSessionFirstLoad(htmlString);
            }
        }

        String cacheOffline = server.getResponseHeaderField(MGDSessionConnection.CUSTOM_HEAD_FILED_CACHE_OFFLINE);
        if (MGDUtils.needSaveData(config.SUPPORT_CACHE_CONTROL, cacheOffline, server.getResponseHeaderFields())) {
            if (hasCompletionData && !wasLoadUrlInvoked.get() && !wasInterceptInvoked.get()) {
                switchState(STATE_RUNNING, STATE_READY, true);
                postTaskToSaveMGDCache(htmlString);
            }
        } else {
            MGDUtils.log(TAG, Log.INFO, "session(" + sId + ") handleFlow_FirstLoad:offline->" + cacheOffline + " , so do not need cache to file.");
        }
    }

    /**
     *
     * In this case MGD obtains the difference data between the server and the local
     * data first,then MGD will build the template and server data into html,
     * then send a <code>CLIENT_CORE_MSG_DATA_UPDATE</code> message.
     *
     * @param serverRsp Server response data
     */
    protected void handleFlow_DataUpdate(String serverRsp) {
        MGDUtils.log(TAG, Log.INFO, "session(" + sId + ") handleFlow_DataUpdate: start.");

        try {
            String htmlString = null;

            if (TextUtils.isEmpty(serverRsp)) {
                serverRsp = server.getResponseData(true);
            } else {
                htmlString = server.getResponseData(false);
            }

            if (TextUtils.isEmpty(serverRsp)) {
                MGDUtils.log(TAG, Log.ERROR, "handleFlow_DataUpdate:getResponseData error.");
                return;
            }


            final String eTag = server.getResponseHeaderField(getCustomHeadFieldEtagKey());
            final String templateTag = server.getResponseHeaderField(MGDSessionConnection.CUSTOM_HEAD_FILED_TEMPLATE_TAG);

            String cacheOffline = server.getResponseHeaderField(MGDSessionConnection.CUSTOM_HEAD_FILED_CACHE_OFFLINE);

            long startTime = System.currentTimeMillis();
            JSONObject serverRspJson = new JSONObject(serverRsp);
            final JSONObject serverDataJson = serverRspJson.optJSONObject("data");
            String htmlSha1 = serverRspJson.optString("html-sha1");

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

            boolean hasSentDataUpdateMessage = false;
            if (wasLoadDataInvoked.get()) {
                if (MGDUtils.shouldLog(Log.INFO)) {
                    MGDUtils.log(TAG, Log.INFO, "handleFlow_DataUpdate:loadData was invoked, quick notify web data update.");
                }
                Message msg = mainHandler.obtainMessage(CLIENT_CORE_MSG_DATA_UPDATE);
                if (!OFFLINE_MODE_STORE.equals(cacheOffline)) {
                    msg.setData(diffDataBundle);
                }
                mainHandler.sendMessage(msg);
                hasSentDataUpdateMessage = true;
            }

            startTime = System.currentTimeMillis();
            if (TextUtils.isEmpty(htmlString)) {
                htmlString = MGDUtils.buildHtml(id, serverDataJson, htmlSha1, serverRsp.length());
            }

            if (MGDUtils.shouldLog(Log.DEBUG)) {
                MGDUtils.log(TAG, Log.DEBUG, "handleFlow_DataUpdate:buildHtml cost " + (System.currentTimeMillis() - startTime) + " ms.");
            }

            if (TextUtils.isEmpty(htmlString)) {
                MGDEngine.getInstance().getRuntime().notifyError(sessionClient, srcUrl, MGDConstants.ERROR_CODE_BUILD_HTML_ERROR);
            }

            if (!hasSentDataUpdateMessage) {
                mainHandler.removeMessages(CLIENT_CORE_MSG_PRE_LOAD);
                Message msg = mainHandler.obtainMessage(CLIENT_CORE_MSG_DATA_UPDATE);
                msg.obj = htmlString;
                mainHandler.sendMessage(msg);
            }

            for (WeakReference<MGDSessionCallback> ref : sessionCallbackList) {
                MGDSessionCallback callback = ref.get();
                if (callback != null) {
                    callback.onSessionDataUpdated(serverRsp);
                }
            }

            if (null == diffDataJson || null == htmlString || !MGDUtils.needSaveData(config.SUPPORT_CACHE_CONTROL, cacheOffline, server.getResponseHeaderFields())) {
                MGDUtils.log(TAG, Log.INFO, "session(" + sId + ") handleFlow_DataUpdate: clean session cache.");
                MGDUtils.removeSessionCache(id);
                return;
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

    @Override
    protected void clearSessionData() {
        if (null != pendingClientCoreMessage) {
            pendingClientCoreMessage = null;
        }
    }
}

