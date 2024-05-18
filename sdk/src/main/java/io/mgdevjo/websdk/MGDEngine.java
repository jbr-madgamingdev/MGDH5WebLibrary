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
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * Interacts with the overall MGDSessions running in the system.
 * Instances of this class can be used to query or fetch the information, such as MGDSession MGDRuntime.
 */
public class MGDEngine {

    /**
     * Log filter
     */
    private final static String TAG = MGDConstants.MGDWEB_SDK_LOG_PREFIX + "MGDEngine";

    /**
     * MGDRuntime
     */
    private final MGDRuntime runtime;

    /**
     * Global config
     */
    private final MGDConfig config;

    /**
     * Single instance
     */
    private static MGDEngine sInstance;

    /**
     * Map containing preload session with capacity limits.
     */
    private final ConcurrentHashMap<String, MGDSession> preloadSessionPool = new ConcurrentHashMap<String, MGDSession>(5);

    /**
     * Map containing weak reference of running sessions.
     */
    private final ConcurrentHashMap<String, MGDSession> runningSessionHashMap = new ConcurrentHashMap<String, MGDSession>(5);


    private MGDEngine(MGDRuntime runtime, MGDConfig config) {
        this.runtime = runtime;
        this.config = config;
    }

    /**
     * Returns a MGDEngine instance
     * <p>
     * Make sure {@link #createInstance(MGDRuntime, MGDConfig)} has been called.
     *
     * @return MGDEngine instance
     * @throws IllegalStateException if {@link #createInstance(MGDRuntime, MGDConfig)} hasn't been called
     */
    public static synchronized MGDEngine getInstance() {
        if (null == sInstance) {
            throw new IllegalStateException("MGDEngine::createInstance() needs to be called before MGDEngine::getInstance()");
        }
        return sInstance;
    }

    /**
     * Check if {@link #getInstance()} is ready or not.
     * <p><b>Note: {@link #createInstance(MGDRuntime, MGDConfig)} must be called if {@code false} is returned.</b></p>
     * @return
     *      Return <code>true</code> if {@link #sInstance} is not null, <code>false</code> otherwise
     */
    public static synchronized boolean isGetInstanceAllowed() {
        return null != sInstance;
    }

    /**
     * Create MGDEngine instance. Meanwhile it will initialize engine and MGDRuntime.
     * @param runtime MGDRuntime
     * @param config MGDConfig
     * @return MGDEngine object
     */
    public static synchronized MGDEngine createInstance(@NonNull MGDRuntime runtime, @NonNull MGDConfig config) {
        if (null == sInstance) {
            sInstance = new MGDEngine(runtime, config);
            if (config.AUTO_INIT_DB_WHEN_CREATE) {
                sInstance.initMGDDB();
            }
        }

        return sInstance;
    }

    /**
     * Init MGD DB which will upgrade to new version of database.
     */
    public void initMGDDB() {
        MGDDBHelper.createInstance(getRuntime().getContext()).getWritableDatabase(); // init and update db
    }

    /**
     * @return MGDRuntime object
     */
    public MGDRuntime getRuntime() {
        return runtime;
    }

    /**
     * @return MGDConfig object
     */
    public MGDConfig getConfig() {
        return config;
    }


    /**
     * Whether MGD Service is available or not
     * @return return true if MGD Service is available , false else others.
     */
    public boolean isMGDAvailable() {
        return !MGDDBHelper.getInstance().isUpgrading();
    }

    /**
     * Create session ID
     *
     * @param url    session url
     * @param isAccountRelated
     *   Session Id will contain {@link com.tencent.MGD.sdk.MGDRuntime#getCurrentUserAccount()}  if {@code isAccountRelated } is true.
     * @return String Object of session ID
     */
    public static String makeSessionId(String url, boolean isAccountRelated) {
        return getInstance().getRuntime().makeSessionId(url, isAccountRelated);
    }

    /**
     * This method will preCreate MGD session .
     * And maps the specified session id to the specified value in this table {@link #preloadSessionPool} if there is no same MGD session.
     * At the same time, if the number of {@link #preloadSessionPool} exceeds {@link MGDConfig#MAX_PRELOAD_SESSION_COUNT},
     * preCreateSession will return false and not create any MGD session.
     *
     * <p><b>Note: this method is intended for preload scene.</b></p>
     * @param url           url for preCreate MGD session
     * @param sessionConfig MGDSession config
     * @return
     *  If this method preCreate MGD session and associated with {@code sessionId} in this table {@link #preloadSessionPool} successfully,
     *  it will return true,
     *  <code>false</code> otherwise.
     */
    public synchronized boolean preCreateSession(@NonNull String url, @NonNull MGDSessionConfig sessionConfig) {
        if (isMGDAvailable()) {
            String sessionId = makeSessionId(url, sessionConfig.IS_ACCOUNT_RELATED);
            if (!TextUtils.isEmpty(sessionId)) {
                MGDSession mgdSession = lookupSession(sessionConfig, sessionId, false);
                if (null != mgdSession) {
                    runtime.log(TAG, Log.ERROR, "preCreateSession：sessionId(" + sessionId + ") is already in preload pool.");
                    return false;
                }
                if (preloadSessionPool.size() < config.MAX_PRELOAD_SESSION_COUNT) {
                    if (isSessionAvailable(sessionId) && runtime.isNetworkValid()) {
                        mgdSession = internalCreateSession(sessionId, url, sessionConfig);
                        if (null != mgdSession) {
                            preloadSessionPool.put(sessionId, mgdSession);
                            return true;
                        }
                    }
                } else {
                    runtime.log(TAG, Log.ERROR, "create id(" + sessionId + ") fail for preload size is bigger than " + config.MAX_PRELOAD_SESSION_COUNT + ".");
                }
            }
        } else {
            runtime.log(TAG, Log.ERROR, "preCreateSession fail for MGD service is unavailable!");
        }
        return false;
    }

    /**
     *
     * @param url           url for MGDSession Object
     * @param sessionConfig MGDSession config
     * @return This method will create and return MGDSession Object when url is legal.
     */
    public synchronized MGDSession createSession(@NonNull String url, @NonNull MGDSessionConfig sessionConfig) {
        if (isMGDAvailable()) {
            String sessionId = makeSessionId(url, sessionConfig.IS_ACCOUNT_RELATED);
            if (!TextUtils.isEmpty(sessionId)) {
                MGDSession mgdSession = lookupSession(sessionConfig, sessionId, true);
                if (null != mgdSession) {
                    mgdSession.setIsPreload(url);
                } else if (isSessionAvailable(sessionId)) { // 缓存中未存在
                    mgdSession = internalCreateSession(sessionId, url, sessionConfig);
                }
                return mgdSession;
            }
        } else {
            runtime.log(TAG, Log.ERROR, "createSession fail for MGD service is unavailable!");
        }
        return null;
    }


    /**
     *
     * @param sessionId possible sessionId
     * @param pick      When {@code pick} is true and there is MGDSession in {@link #preloadSessionPool},
     *                  it will remove from {@link #preloadSessionPool}
     * @return
     *          Return valid MGDSession Object from {@link #preloadSessionPool} if the specified sessionId is a key in {@link #preloadSessionPool}.
     */
    private MGDSession lookupSession(MGDSessionConfig config, String sessionId, boolean pick) {
        if (!TextUtils.isEmpty(sessionId) && config != null) {
            MGDSession mgdSession = preloadSessionPool.get(sessionId);
            if (mgdSession != null) {
                //判断session缓存是否过期,以及sessionConfig是否发生变化
                if (!config.equals(mgdSession.config) ||
                        mgdSession.config.PRELOAD_SESSION_EXPIRED_TIME > 0 && System.currentTimeMillis() - mgdSession.createdTime > mgdSession.config.PRELOAD_SESSION_EXPIRED_TIME) {
                    if (runtime.shouldLog(Log.ERROR)) {
                        runtime.log(TAG, Log.ERROR, "lookupSession error:sessionId(" + sessionId + ") is expired.");
                    }
                    preloadSessionPool.remove(sessionId);
                    mgdSession.destroy();
                    return null;
                }

                if (pick) {
                    preloadSessionPool.remove(sessionId);
                }
            }
            return mgdSession;
        }
        return null;
    }

    /**
     * Create MGD session internal
     *
     * @param sessionId session id
     * @param url origin url
     * @param sessionConfig session config
     * @return Return new MGDSession if there was no mapping for the sessionId in {@link #runningSessionHashMap}
     */
    private MGDSession internalCreateSession(String sessionId, String url, MGDSessionConfig sessionConfig) {
        if (!runningSessionHashMap.containsKey(sessionId)) {
            MGDSession mgdSession;
            if (sessionConfig.sessionMode == MGDConstants.SESSION_MODE_QUICK) {
                mgdSession = new QuickMGDSession(sessionId, url, sessionConfig);
            } else {
                mgdSession = new StandardMGDSession(sessionId, url, sessionConfig);
            }
            mgdSession.addSessionStateChangedCallback(sessionCallback);

            if (sessionConfig.AUTO_START_WHEN_CREATE) {
                mgdSession.start();
            }
            return mgdSession;
        }
        if (runtime.shouldLog(Log.ERROR)) {
            runtime.log(TAG, Log.ERROR, "internalCreateSession error:sessionId(" + sessionId + ") is running now.");
        }
        return null;
    }

    /**
     * If the server fails or specifies HTTP pattern, MGDSession won't use MGD pattern Within {@link com.tencent.MGD.sdk.MGDConfig#MGD_UNAVAILABLE_TIME} ms
     * @param sessionId session id
     * @return Test if the sessionId is available.
     */
    private boolean isSessionAvailable(String sessionId) {
        long unavailableTime = MGDDataHelper.getLastMGDUnavailableTime(sessionId);
        if (System.currentTimeMillis() > unavailableTime) {
            return true;
        }
        if (runtime.shouldLog(Log.ERROR)) {
            runtime.log(TAG, Log.ERROR, "sessionId(" + sessionId + ") is unavailable and unavailable time until " + unavailableTime + ".");
        }
        return false;
    }

    /**
     * Removes all of the cache from {@link #preloadSessionPool} and deletes file caches from SDCard.
     *
     * @return
     *      Returns {@code false} if {@link #runningSessionHashMap} is not empty.
     *      Returns {@code true} if all of the local file cache has been deleted, <code>false</code> otherwise
     */
    public synchronized boolean cleanCache() {
        if (!preloadSessionPool.isEmpty()) {
            runtime.log(TAG, Log.INFO, "cleanCache: remove all preload sessions, size=" + preloadSessionPool.size() + ".");
            Collection<MGDSession> mgdSessions = preloadSessionPool.values();
            for (MGDSession session : mgdSessions) {
                session.destroy();
            }
            preloadSessionPool.clear();
        }

        if (!runningSessionHashMap.isEmpty()) {
            runtime.log(TAG, Log.ERROR, "cleanCache fail, running session map's size is " + runningSessionHashMap.size() + ".");
            return false;
        }

        runtime.log(TAG, Log.INFO, "cleanCache: remove all sessions cache.");

        return MGDUtils.removeAllSessionCache();
    }

    /**
     * Removes the sessionId and its corresponding MGDSession from {@link #preloadSessionPool}.
     *
     * @param sessionId A unique session id
     * @return Return {@code true} If there is no specified sessionId in {@link #runningSessionHashMap}, <code>false</code> otherwise.
     */
    public synchronized boolean removeSessionCache(@NonNull String sessionId) {
        MGDSession mgdSession = preloadSessionPool.get(sessionId);
        if (null != mgdSession) {
            mgdSession.destroy();
            preloadSessionPool.remove(sessionId);
            runtime.log(TAG, Log.INFO, "sessionId(" + sessionId + ") removeSessionCache: remove preload session.");
        }

        if (!runningSessionHashMap.containsKey(sessionId)) {
            runtime.log(TAG, Log.INFO, "sessionId(" + sessionId + ") removeSessionCache success.");
            MGDUtils.removeSessionCache(sessionId);
            return true;
        }
        runtime.log(TAG, Log.ERROR, "sessionId(" + sessionId + ") removeSessionCache fail: session is running.");
        return false;
    }

    /**
     * It will Post a task to trim MGD cache
     * if the last time of check MGD cache exceed {@link MGDConfig#MGD_CACHE_CHECK_TIME_INTERVAL}.
     */
    public void trimMGDCache() {
        MGDFileUtils.checkAndTrimCache();
        MGDFileUtils.checkAndTrimResourceCache();
    }

    /**
     * <p>A callback receives notifications from a MGDSession.
     * Notifications indicate session related events, such as the running or the
     * destroy of the MGDSession.
     * It is intended to handle cache of MGDSession correctly to avoid concurrent modification.
     * </p>
     *
     */
    private final MGDSession.Callback sessionCallback = new MGDSession.Callback() {
        @Override
        public void onSessionStateChange(MGDSession session, int oldState, int newState, Bundle extraData) {
            MGDUtils.log(TAG, Log.DEBUG, "onSessionStateChange:session(" + session.sId + ") from state " + oldState + " -> " + newState);
            switch (newState) {
                case MGDSession.STATE_RUNNING:
                    runningSessionHashMap.put(session.id, session);
                    break;
                case MGDSession.STATE_DESTROY:
                    runningSessionHashMap.remove(session.id);
                    break;
            }
        }
    };
}
