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

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import io.mgdevjo.websdk.MGDConstants;
import io.mgdevjo.websdk.MGDEngine;
import io.mgdevjo.websdk.MGDRuntime;
import io.mgdevjo.websdk.MGDSession;
import io.mgdevjo.websdk.MGDUtils;
import io.mgdevjo.websdk.download.MGDDownloadClient.DownloadTask;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * an download initiator
 */
public class MGDDownloadEngine implements Handler.Callback {
    /**
     * log filter
     */
    public static final String TAG = MGDConstants.MGDWEB_SDK_LOG_PREFIX + "MGDDownloadEngine";

    /**
     * message code enqueue:
     * when downloading tasks more than config number, the task should enqueue for waiting.
     */
    private static final int MSG_ENQUEUE = 0;

    /**
     * message code: one download task is complete and the download queue is free.
     */
    private static final int MSG_DEQUEUE = 1;

    private ConcurrentMap<String, DownloadTask> resourceTasks = new ConcurrentHashMap<String, DownloadTask>();


    /**
     * A queue implementation using {@link LinkedHashMap}.
     * A queue with map function.
     */
    private static class MGDDownloadQueue
            extends LinkedHashMap<String, DownloadTask> {

        synchronized DownloadTask dequeue() {
            if (values().iterator().hasNext()) {
                DownloadTask task = values().iterator().next();
                return remove(task.mResourceUrl);
            }
            return null;
        }

        synchronized void enqueue(DownloadTask task) {
            if (task != null && !TextUtils.isEmpty(task.mResourceUrl)) {
                put(task.mResourceUrl, task);
            }
        }
    }

    /**
     * the download task queue.
     */
    private final MGDDownloadQueue mQueue;

    /**
     * download thread handler.
     */
    private Handler mHandler;

    /**
     * number of downloading tasks.
     */
    private AtomicInteger mNumOfDownloadingTask;

    /**
     * A download cache.
     */
    private MGDDownloadCache mCache;

    /**
     *
     * @param cache A specific implementation of {@link MGDDownloadCache}
     */
    public MGDDownloadEngine(MGDDownloadCache cache) {
        mQueue = new MGDDownloadQueue();
        HandlerThread queueThread = new HandlerThread("Download-Thread");
        queueThread.start();
        mHandler = new Handler(queueThread.getLooper(), this);

        mNumOfDownloadingTask = new AtomicInteger(0);
        mCache = cache;
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_ENQUEUE: {
                DownloadTask task = (DownloadTask) msg.obj;
                mQueue.enqueue(task);
                task.mState.set(DownloadTask.STATE_QUEUEING);
                MGDUtils.log(TAG, Log.INFO, "enqueue sub resource(" + task.mResourceUrl + ").");
                break;
            }
            case MSG_DEQUEUE: {
                if (!mQueue.isEmpty()) {
                    DownloadTask task = mQueue.dequeue();
                    startDownload(task);
                    MGDUtils.log(TAG, Log.INFO, "dequeue sub resource(" + task.mResourceUrl + ").");
                }
                break;
            }
            default:
                break;
        }
        return false;
    }

    /**
     * start downloading one resource.
     * if the responding cache exists and isn't expire, will use the cache directly and won't launch a http request;
     * if the number of downloading tasks is bigger than config, the task will be delayed before downloading pool is free.
     *
     * @param resourceUrl the resource's url
     * @param ipAddress if dns prefetch the ip address, will use the ip instead of host
     * @param cookie set the cookie for the download http request
     * @param callback a callback used for notify the download progress and result
     * @return the download task info
     */
    public DownloadTask download(String resourceUrl, String ipAddress, String cookie, MGDDownloadCallback callback) {
        if (TextUtils.isEmpty(resourceUrl)) {
            return null;
        }

        synchronized (mQueue) {
            if (mQueue.containsKey(resourceUrl)) {
                MGDUtils.log(TAG, Log.INFO, "sub resource download task has been in queue (" + resourceUrl + ").");
                return mQueue.get(resourceUrl);
            }
        }

        final DownloadTask task = new DownloadTask();
        task.mResourceUrl = resourceUrl;
        task.mCallbacks.add(callback);
        task.mCallbacks.add(new MGDDownloadCallback.SimpleDownloadCallback() {
            @Override
            public void onFinish() {
                task.mState.set(DownloadTask.STATE_DOWNLOADED);
                mHandler.sendEmptyMessage(MSG_DEQUEUE);
            }
        });

        // query cache
        byte[] resourceBytes = mCache.getResourceCache(resourceUrl);
        if (resourceBytes != null) {
            task.mInputStream = new ByteArrayInputStream(resourceBytes);
            task.mRspHeaders = mCache.getResourceCacheHeader(resourceUrl);
            task.mState.set(DownloadTask.STATE_LOAD_FROM_CACHE);
            MGDUtils.log(TAG, Log.INFO, "load sub resource(" + resourceUrl + ") from cache.");
            return task;
        }

        // no cache then start download
        task.mIpAddress = ipAddress;
        task.mCookie = cookie;
        if (mNumOfDownloadingTask.get() < MGDEngine.getInstance().getConfig().MGD_MAX_NUM_OF_DOWNLOADING_TASK) {
            startDownload(task);
        } else {
            Message enqueueMsg = mHandler.obtainMessage(MSG_ENQUEUE, task);
            mHandler.sendMessage(enqueueMsg);
        }
        return task;
    }

    /**
     * dispatch the download task to really download.
     *
     * @param task download task
     */
    private void startDownload(final DownloadTask task) {
        MGDEngine.getInstance().getRuntime().postTaskToSessionThread(new Runnable() {
            @Override
            public void run() {
                mNumOfDownloadingTask.incrementAndGet();
                task.mState.set(DownloadTask.STATE_DOWNLOADING);
                MGDDownloadClient engine = new MGDDownloadClient(task);
                engine.download();
            }
        });
    }

    /**
     * When the webview initiates a sub resource interception, the client invokes this method to retrieve the data
     *
     * @param url The url of sub resource
     * @param session current MGD session
     * @return Return the data to kernel
     */
    public Object onRequestSubResource(String url, MGDSession session) {
        if (MGDUtils.shouldLog(Log.INFO)) {
            MGDUtils.log(TAG, Log.INFO, "session onRequestSubResource: resource url(" + url + ").");
        }

        InputStream inputStream = null;
        Map<String, List<String>> headers = null;
        if (resourceTasks.containsKey(url)) {
            DownloadTask subRes = resourceTasks.get(url);
            subRes.mWasInterceptInvoked.set(true);
            if (subRes.mState.get() == DownloadTask.STATE_INITIATE
                    || subRes.mState.get() == DownloadTask.STATE_QUEUEING) {
                return null;
            } else {
                if (subRes.mInputStream == null) {
                    synchronized (subRes.mWasInterceptInvoked) {
                        try {
                            subRes.mWasInterceptInvoked.wait(3 * 1000);
                        } catch (InterruptedException e) {
                            MGDUtils.log(TAG, Log.ERROR, "session onRequestSubResource error: " + e.getMessage());
                        }
                    }
                }
                if (subRes.mInputStream == null) {
                    return null;
                }
                inputStream = subRes.mInputStream;
                headers = subRes.mRspHeaders;
            }
        } else {
            return null;
        }

        Object webResourceResponse;
        if (!session.isDestroyedOrWaitingForDestroy()) {
            String mime = MGDUtils.getMime(url);
            Map<String, String> filteredHeaders = MGDUtils.getFilteredHeaders(headers);
            webResourceResponse = MGDEngine.getInstance().getRuntime().createWebResourceResponse(mime,
                    session.getCharsetFromHeaders(filteredHeaders), inputStream, filteredHeaders);
        } else {
            webResourceResponse = null;
            MGDUtils.log(TAG, Log.ERROR, "session onRequestSubResource error: session is destroyed!");
        }
        return webResourceResponse;
    }

    /**
     * preload the sub resource in the "MGD-link" header.
     * @param preloadLinks The links which need to be preloaded.
     */
    public void addSubResourcePreloadTask(List<String> preloadLinks) {

        MGDRuntime runtime = MGDEngine.getInstance().getRuntime();
        for (final String link : preloadLinks) {
            if (!resourceTasks.containsKey(link)) {
                resourceTasks.put(link,
                        download(link,
                                runtime.getHostDirectAddress(link),
                                runtime.getCookie(link),
                                new MGDDownloadClient.SubResourceDownloadCallback(link)
                        )
                );
            }
        }
    }
}
