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

import java.io.File;
import java.util.List;
import java.util.Map;

import io.mgdevjo.websdk.MGDConstants;
import io.mgdevjo.websdk.MGDEngine;
import io.mgdevjo.websdk.MGDFileUtils;
import io.mgdevjo.websdk.MGDResourceDataHelper;
import io.mgdevjo.websdk.MGDUtils;

/**
 * WebSDK download cache manager
 */
public abstract class MGDDownloadCache {

    /**
     * get the cached content according to the url
     *
     * @param url the download url
     * @return bytes of cached content of the url
     */
    public abstract byte[] getResourceCache(String url);

    /**
     * get the cached response headers according to the url
     *
     * @param url the download url
     * @return cached headers of the url
     */
    public abstract Map<String, List<String>> getResourceCacheHeader(String url);

    /**
     *
     * @return Sub resource cache
     */
    public static MGDDownloadCache getSubResourceCache() {
        return new MGDResourceCache();
    }

    /**
     * An sub resource cache implementation {@link MGDDownloadCache}
     */
    public static class MGDResourceCache extends MGDDownloadCache {

        /**
         * log filter
         */
        public static final String TAG = MGDConstants.MGDWEB_SDK_LOG_PREFIX + "MGDDownloadCache";

        public byte[] getResourceCache(String resourceUrl) {
            if (TextUtils.isEmpty(resourceUrl)) {
                return null;
            }
            String resourceId = MGDUtils.getMD5(resourceUrl);
            MGDResourceDataHelper.ResourceData resourceData = MGDResourceDataHelper.getResourceData(resourceId);

            // the resource cache expired
            if (resourceData.expiredTime < System.currentTimeMillis()) {
                return null;
            }

            boolean verifyError;
            byte[] resourceBytes = null;
            // verify local data
            if (TextUtils.isEmpty(resourceData.resourceSha1)) {
                verifyError = true;
                MGDUtils.log(TAG, Log.INFO, "get resource data(" + resourceUrl + "): resource data is empty.");
            } else {
                String resourcePath = MGDFileUtils.getMGDResourcePath(resourceId);
                File resourceFile = new File(resourcePath);
                resourceBytes = MGDFileUtils.readFileToBytes(resourceFile);
                verifyError = resourceBytes == null || resourceBytes.length <= 0;
                if (verifyError) {
                    MGDUtils.log(TAG, Log.ERROR, "get resource data(" + resourceUrl + ") error:cache data is null.");
                } else {
                    if (MGDEngine.getInstance().getConfig().VERIFY_CACHE_FILE_WITH_SHA1) {
                        if (!MGDFileUtils.verifyData(resourceBytes, resourceData.resourceSha1)) {
                            verifyError = true;
                            resourceBytes = null;
                            MGDUtils.log(TAG, Log.ERROR, "get resource data(" + resourceUrl + ") error:verify html cache with sha1 fail.");
                        } else {
                            MGDUtils.log(TAG, Log.INFO, "get resource data(" + resourceUrl + ") verify html cache with sha1 success.");
                        }
                    } else {
                        if (resourceData.resourceSize != resourceFile.length()) {
                            verifyError = true;
                            resourceBytes = null;
                            MGDUtils.log(TAG, Log.ERROR, "get resource data(" + resourceUrl + ") error:verify html cache with size fail.");
                        }
                    }
                }
            }
            // if the local data is faulty, delete it
            if (verifyError) {
                long startTime = System.currentTimeMillis();
                MGDUtils.removeResourceCache(resourceId);
                resourceData.reset();
                MGDUtils.log(TAG, Log.INFO, "get resource data(" + resourceUrl + ") :verify error so remove session cache, cost " + +(System.currentTimeMillis() - startTime) + "ms.");
            }
            return resourceBytes;
        }

        public Map<String, List<String>> getResourceCacheHeader(String resourceUrl) {
            String resourceName = MGDUtils.getMD5(resourceUrl);
            String headerPath = MGDFileUtils.getMGDResourceHeaderPath(resourceName);
            return MGDFileUtils.getHeaderFromLocalCache(headerPath);
        }
    }
}
