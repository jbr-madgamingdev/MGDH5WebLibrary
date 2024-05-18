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

package io.mgdevjo.websdk.download;

import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

import static java.net.HttpURLConnection.HTTP_OK;

import static io.mgdevjo.websdk.MGDSessionConnection.HTTP_HEAD_FIELD_COOKIE;

import io.mgdevjo.websdk.MGDConstants;
import io.mgdevjo.websdk.MGDSessionStream;
import io.mgdevjo.websdk.MGDUtils;

public class MGDDownloadClient implements MGDSessionStream.Callback {

    /**
     * log filter
     */
    public static final String TAG = MGDConstants.MGDWEB_SDK_LOG_PREFIX + "MGDDownloadClient";

    /**
     * download buffer size
     */
    private static final int READ_BUFFER_SIZE = 2048;

    /**
     * Task which record the download info
     */
    public static class DownloadTask {

        /**
         * download in initiate state.
         */
        public static final int STATE_INITIATE = 0;

        /**
         * download in queueing state.
         */
        public static final int STATE_QUEUEING = 1;

        /**
         * the task is in downloading state.
         */
        public static final int STATE_DOWNLOADING = 2;

        /**
         * the task is in download complete state.
         */
        public static final int STATE_DOWNLOADED = 3;

        /**
         * the task is load from cache, not from network.
         */
        public static final int STATE_LOAD_FROM_CACHE = 4;

        /**
         * url of the resource to be download
         */
        public String mResourceUrl;

        /**
         * ip address instead of host to launch a http request
         */
        public String mIpAddress;

        /**
         * cookie to be set in the http download request
         */
        public String mCookie;

        /**
         * the download request's response headers
         */
        public Map<String, List<String>> mRspHeaders;

        /**
         * the network stream or memory stream or the bridge stream
         */
        public InputStream mInputStream;

        /**
         * the task's download state
         */
        public AtomicInteger mState = new AtomicInteger(STATE_INITIATE);

        /**
         * whether the task's responding resource was intercepted by kernel
         */
        public final AtomicBoolean mWasInterceptInvoked = new AtomicBoolean(false);

        /**
         * list of download callback
         */
        public List<MGDDownloadCallback> mCallbacks = new ArrayList<MGDDownloadCallback>();
    }

    /**
     * A download connection implement.
     */
    private final MGDDownloadConnection mConn;

    /**
     * the responding download task
     */
    private DownloadTask mTask;

    private ByteArrayOutputStream mOutputStream;

    /**
     * whether the download task is finished or is a bridge stream
     */
    private boolean mDownloadFinished = false;

    public MGDDownloadClient(DownloadTask task) {
        mTask = task;
        mConn = new MGDDownloadConnection(task.mResourceUrl);
        mOutputStream = new ByteArrayOutputStream();
    }

    /**
     * download the resource and notify download progress
     *
     * @return response code
     */
    public int download() {
        onStart();

        int resultCode = mConn.connect();

        if (MGDConstants.ERROR_CODE_SUCCESS != resultCode) {
            onError(resultCode);
            return resultCode; // error case
        }

        int responseCode = mConn.getResponseCode();
        if (responseCode != HTTP_OK) {
            onError(responseCode);
            return responseCode;
        }

        mTask.mRspHeaders = mConn.getResponseHeaderFields();
        if (getResponseStream(mTask.mWasInterceptInvoked)) {
            return MGDConstants.ERROR_CODE_SUCCESS;
        }
        return MGDConstants.ERROR_CODE_UNKNOWN;
    }

    private boolean readServerResponse(AtomicBoolean breakCondition) {
        BufferedInputStream bufferedInputStream = mConn.getResponseStream();
        if (null == bufferedInputStream) {
            MGDUtils.log(TAG, Log.ERROR, "readServerResponse error: bufferedInputStream is null!");
            return false;
        }

        try {
            byte[] buffer = new byte[READ_BUFFER_SIZE];

            int total = mConn.connectionImpl.getContentLength();
            int n = 0, sum = 0;
            while (((breakCondition == null) || !breakCondition.get()) && -1 != (n = bufferedInputStream.read(buffer))) {
                mOutputStream.write(buffer, 0, n);
                sum += n;
                if (total > 0) {
                    onProgress(sum, total);
                }
            }

            if (n == -1) {
                mDownloadFinished = true;
                onSuccess(mOutputStream.toByteArray(), mConn.getResponseHeaderFields());
            }
        } catch (Exception e) {
            MGDUtils.log(TAG, Log.ERROR, "readServerResponse error:" + e.getMessage() + ".");
            return false;
        }

        return true;
    }

    private synchronized boolean getResponseStream(AtomicBoolean breakConditions) {
        if (readServerResponse(breakConditions)) {
            BufferedInputStream netStream = mDownloadFinished ? null : mConn.getResponseStream();
            mTask.mInputStream = new MGDSessionStream(this, mOutputStream, netStream);
            synchronized (mTask.mWasInterceptInvoked) {
                mTask.mWasInterceptInvoked.notify();
            }
            if (mDownloadFinished) {
                MGDUtils.log(TAG, Log.INFO, "sub resource compose a memory stream (" + mTask.mResourceUrl + ").");
            } else {
                MGDUtils.log(TAG, Log.INFO, "sub resource compose a bridge stream (" + mTask.mResourceUrl + ").");
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onClose(boolean readComplete, ByteArrayOutputStream outputStream) {
        MGDUtils.log(TAG, Log.INFO, "sub resource bridge stream on close(" + mTask.mResourceUrl + ").");
        if (!mDownloadFinished) {
            onSuccess(outputStream.toByteArray(), mConn.getResponseHeaderFields());
        }
    }

    private void onStart() {
        for (MGDDownloadCallback callback : mTask.mCallbacks) {
            if (callback != null) {
                callback.onStart();
            }
        }
    }

    private void onProgress(int pro, int total) {
        for (MGDDownloadCallback callback : mTask.mCallbacks) {
            if (callback != null) {
                callback.onProgress(pro, total);
            }
        }
    }

    private void onSuccess(byte[] content, Map<String, List<String>> rspHeaders) {
        for (MGDDownloadCallback callback : mTask.mCallbacks) {
            if (callback != null) {
                callback.onSuccess(content, rspHeaders);
            }
        }
        onFinish();
    }

    private void onError(int errCode) {
        for (MGDDownloadCallback callback : mTask.mCallbacks) {
            if (callback != null) {
                callback.onError(errCode);
            }
        }
        onFinish();
    }

    private void onFinish() {
        for (MGDDownloadCallback callback : mTask.mCallbacks) {
            if (callback != null) {
                callback.onFinish();
            }
        }
        mConn.disconnect();
    }

    public class MGDDownloadConnection {
        final URLConnection connectionImpl;

        private String url;

        private BufferedInputStream responseStream;

        public MGDDownloadConnection(String url) {
            this.url = url;
            connectionImpl = createConnection();
            initConnection(connectionImpl);
        }

        URLConnection createConnection() {
            String currentUrl = url;
            if (TextUtils.isEmpty(currentUrl)) {
                return null;
            }

            URLConnection connection = null;
            try {
                URL url = new URL(currentUrl);
                String originHost = null;

                if (!TextUtils.isEmpty(mTask.mIpAddress)) {
                    originHost = url.getHost();
                    url = new URL(currentUrl.replace(originHost, mTask.mIpAddress));
                    MGDUtils.log(TAG, Log.INFO, "create UrlConnection with DNS-Prefetch(" + originHost + " -> " + mTask.mIpAddress + ").");
                }
                connection = url.openConnection();
                if (connection != null) {
                    if (!TextUtils.isEmpty(originHost)) {
                        connection.setRequestProperty("Host", originHost);
                    }
                }
            } catch (Throwable e) {
                if (connection != null) {
                    connection = null;
                }
                MGDUtils.log(TAG, Log.ERROR, "create UrlConnection fail, error:" + e.getMessage() + ".");
            }
            return connection;
        }

        boolean initConnection(URLConnection connection) {
            if (null != connection) {
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(15000);

                connection.setRequestProperty("method", "GET");
                connection.setRequestProperty("Accept-Encoding", "gzip");
                connection.setRequestProperty("Accept-Language", "zh-CN,zh;");

                if (!TextUtils.isEmpty(mTask.mCookie)) {
                    connection.setRequestProperty(HTTP_HEAD_FIELD_COOKIE, mTask.mCookie);
                }
                return true;
            }
            return false;
        }

        synchronized int connect() {
            if (connectionImpl instanceof HttpURLConnection) {
                HttpURLConnection httpURLConnection = (HttpURLConnection) connectionImpl;
                try {
                    httpURLConnection.connect();
                    return MGDConstants.ERROR_CODE_SUCCESS;
                } catch (IOException e) {
                    return MGDConstants.ERROR_CODE_CONNECT_IOE;
                }
            }
            return MGDConstants.ERROR_CODE_UNKNOWN;
        }

        public void disconnect() {
            if (connectionImpl instanceof HttpURLConnection) {
                final HttpURLConnection httpURLConnection = (HttpURLConnection) connectionImpl;
                try {
                    httpURLConnection.disconnect();
                } catch (Exception e) {
                    MGDUtils.log(TAG, Log.ERROR, "disconnect error:" + e.getMessage());
                }
            }
        }

        BufferedInputStream getResponseStream() {
            if (null == responseStream && null != connectionImpl) {
                try {
                    InputStream inputStream = connectionImpl.getInputStream();
                    if ("gzip".equalsIgnoreCase(connectionImpl.getContentEncoding())) {
                        responseStream = new BufferedInputStream(new GZIPInputStream(inputStream));
                    } else {
                        responseStream = new BufferedInputStream(inputStream);
                    }
                } catch (Throwable e) {
                    MGDUtils.log(TAG, Log.ERROR, "getResponseStream error:" + e.getMessage() + ".");
                }
            }
            return responseStream;
        }

        int getResponseCode() {
            if (connectionImpl instanceof HttpURLConnection) {
                try {
                    return ((HttpURLConnection) connectionImpl).getResponseCode();
                } catch (IOException e) {
                    String errMsg = e.getMessage();
                    MGDUtils.log(TAG, Log.ERROR, "getResponseCode error:" + errMsg);
                    return MGDConstants.ERROR_CODE_CONNECT_IOE;
                }
            }
            return MGDConstants.ERROR_CODE_UNKNOWN;
        }

        Map<String, List<String>> getResponseHeaderFields() {
            if (null == connectionImpl) {
                return null;
            }
            return connectionImpl.getHeaderFields();
        }
    }

    /**
     * sub resource download callback.
     */
    public static class SubResourceDownloadCallback extends MGDDownloadCallback.SimpleDownloadCallback {

        private String resourceUrl;

        public SubResourceDownloadCallback(String url) {
            this.resourceUrl = url;
        }

        @Override
        public void onStart() {
            if (MGDUtils.shouldLog(Log.INFO)) {
                MGDUtils.log(TAG, Log.INFO, "session start download sub resource, url=" + resourceUrl);
            }
        }

        @Override
        public void onSuccess(byte[] content, Map<String, List<String>> rspHeaders) {
            // save cache files
            String fileName = MGDUtils.getMD5(resourceUrl);
            MGDUtils.saveResourceFiles(fileName, content, rspHeaders);
            // save resource data to db
            MGDUtils.saveMGDResourceData(resourceUrl, MGDUtils.getSHA1(content), content.length);

        }

        @Override
        public void onError(int errorCode) {
            if (MGDUtils.shouldLog(Log.INFO)) {
                MGDUtils.log(TAG, Log.INFO, "session download sub resource error: code = " + errorCode + ", url=" + resourceUrl);
            }
        }
    }

}
