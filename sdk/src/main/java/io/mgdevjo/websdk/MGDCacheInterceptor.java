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

import android.text.TextUtils;
import android.util.Log;

import java.io.File;

/**
 * <code>MGDCacheInterceptor</code> provide local data.
 * if a {@link MGDSessionConfig} does not set a MGDCacheInterceptor
 * MGD will use {@link MGDSessionConnection.SessionConnectionDefaultImpl} as default.
 *
 */
public abstract class MGDCacheInterceptor {

    public static final String TAG = MGDConstants.MGDWEB_SDK_LOG_PREFIX + "MGDCacheInterceptor";

    private final MGDCacheInterceptor nextInterceptor;

    public MGDCacheInterceptor(MGDCacheInterceptor next) {
        nextInterceptor = next;
    }

    public MGDCacheInterceptor next() {
        return nextInterceptor;
    }

    public abstract String getCacheData(MGDSession session);

    static String getMGDCacheData(MGDSession session) {
        MGDCacheInterceptor interceptor = session.config.cacheInterceptor;
        if (null == interceptor) {
            return MGDCacheInterceptorDefaultImpl.getCacheData(session);
        }

        String htmlString = null;
        while (null != interceptor) {
            htmlString = interceptor.getCacheData(session);
            if (null != htmlString) {
                break;
            }
            interceptor = interceptor.next();
        }
        return htmlString;
    }

    /**
     * <code>MGDCacheInterceptorDefaultImpl</code> provide a default implement for MGDCacheInterceptor.
     */
    private static class MGDCacheInterceptorDefaultImpl {

        public static final String TAG = MGDConstants.MGDWEB_SDK_LOG_PREFIX + "DefaultMGDCacheInterceptor";

        public static String getCacheData(MGDSession session) {
            if (session == null) {
                MGDUtils.log(TAG, Log.INFO, "getCache is null");
                return null;
            }

            MGDDataHelper.SessionData sessionData = MGDDataHelper.getSessionData(session.id);
            boolean verifyError;
            String htmlString = "";
            // verify local data
            if (TextUtils.isEmpty(sessionData.eTag) || TextUtils.isEmpty(sessionData.htmlSha1)) {
                verifyError = true;
                MGDUtils.log(TAG, Log.INFO, "session(" + session.sId + ") runMGDFlow : session data is empty.");
            } else {
                MGDDataHelper.updateMGDCacheHitCount(session.id);
                File htmlCacheFile = new File(MGDFileUtils.getMGDHtmlPath(session.id));
                htmlString = MGDFileUtils.readFile(htmlCacheFile);
                verifyError = TextUtils.isEmpty(htmlString);
                if (verifyError) {
                    MGDUtils.log(TAG, Log.ERROR, "session(" + session.sId + ") runMGDFlow error:cache data is null.");
                } else {
                    if (MGDEngine.getInstance().getConfig().VERIFY_CACHE_FILE_WITH_SHA1) {
                        if (!MGDFileUtils.verifyData(htmlString, sessionData.htmlSha1)) {
                            verifyError = true;
                            htmlString = "";
                            MGDEngine.getInstance().getRuntime().notifyError(session.sessionClient, session.srcUrl, MGDConstants.ERROR_CODE_DATA_VERIFY_FAIL);
                            MGDUtils.log(TAG, Log.ERROR, "session(" + session.sId + ") runMGDFlow error:verify html cache with sha1 fail.");
                        } else {
                            MGDUtils.log(TAG, Log.INFO, "session(" + session.sId + ") runMGDFlow verify html cache with sha1 success.");
                        }
                    } else {
                        if (sessionData.htmlSize != htmlCacheFile.length()) {
                            verifyError = true;
                            htmlString = "";
                            MGDEngine.getInstance().getRuntime().notifyError(session.sessionClient, session.srcUrl, MGDConstants.ERROR_CODE_DATA_VERIFY_FAIL);
                            MGDUtils.log(TAG, Log.ERROR, "session(" + session.sId + ") runMGDFlow error:verify html cache with size fail.");
                        }
                    }
                }
            }
            // if the local data is faulty, delete it
            if (verifyError) {
                long startTime = System.currentTimeMillis();
                MGDUtils.removeSessionCache(session.id);
                sessionData.reset();
                MGDUtils.log(TAG, Log.INFO, "session(" + session.sId + ") runMGDFlow:verify error so remove session cache, cost " + +(System.currentTimeMillis() - startTime) + "ms.");
            }
            return htmlString;
        }
    }

}
