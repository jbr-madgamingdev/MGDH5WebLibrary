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

/**
 * The life circle callback of MGDSession
 */

interface MGDSessionCallback {

    /**
     * This method will be called while MGD load local cache.
     * @param cacheHtml Current MGD local cache.
     */
    void onSessionLoadLocalCache(String cacheHtml);

    /**
     * This method will be called when MGD receive updated data from server.
     * @param serverRsp Server response data.
     */
    void onSessionDataUpdated(String serverRsp);

    /**
     * This method will be called when MGD first send request and receive server data
     * @param html The new html content from server.
     */
    void onSessionFirstLoad(String html);

    /**
     * This method will be called when MGD receive error.
     * @param responseCode Http response code
     */
    void onSessionHttpError(int responseCode);

    /**
     * This method will be called when current MGD cache is the same with remote server.
     */
    void onSessionHitCache();

    /**
     * This method will be called when MGD request is invalid and server return "Cache-Offline: http"
     */
    void onSessionUnAvailable();

    /**
     * This method will be called when MGD handle template change.
     * @param newHtml
     */
    void onSessionTemplateChanged(String newHtml);

    /**
     * This method will be called when MGD save cache from server.
     *
     * @param htmlString The whole html content.
     * @param templateString The template content.
     * @param dataString The data content.
     */
    void onSessionSaveCache(String htmlString, String templateString, String dataString);

    /**
     * This method will be called when MGD start to send request.
     */
    void onMGDSessionStart() ;

    /**
     * This method will be called when MGD session destroy.
     */
    void onSessionDestroy();

    /**
     * This method will be called when MGD session refresh.
     */
    void onMGDSessionRefresh();

    /**
     * an empty implementation of {@link MGDSessionCallback}
     */
    public class SimpleCallbackImpl implements MGDSessionCallback {

        @Override
        public void onSessionLoadLocalCache(String cacheHtml) {

        }

        @Override
        public void onSessionDataUpdated(String serverRsp) {

        }

        @Override
        public void onSessionFirstLoad(String html) {

        }

        @Override
        public void onSessionHttpError(int responseCode) {

        }

        @Override
        public void onSessionHitCache() {

        }

        @Override
        public void onSessionUnAvailable() {

        }

        @Override
        public void onSessionTemplateChanged(String newHtml) {

        }

        @Override
        public void onSessionSaveCache(String htmlString, String templateString, String dataString) {

        }

        @Override
        public void onMGDSessionStart() {

        }

        @Override
        public void onSessionDestroy() {

        }

        @Override
        public void onMGDSessionRefresh() {

        }
    }
    
}
