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
 * MGD global config
 */
public class MGDConfig {

    /**
     * The max number of preload session , default is 5
     */
    int MAX_PRELOAD_SESSION_COUNT = 5;

    /**
     * When MGD server unavailable, MGD will not execute its flow and will execute
     * webview normal loading process. This time control MGD how log will not execute its flow.
     */
    long MGD_UNAVAILABLE_TIME = 6 * 60 * 60 * 1000;

    /**
     * The max size of MGD cache, default is 30M.
     */
    long MGD_CACHE_MAX_SIZE = 30 * 1024 * 1024;

    /**
     * The max size of MGD resource cache, default is 60M.
     */
    long MGD_RESOURCE_CACHE_MAX_SIZE = 60 * 1024 * 1024;

    /**
     * The time interval between check MGD cache, default is 24 hours.
     */
    long MGD_CACHE_CHECK_TIME_INTERVAL = 24 * 60 * 60 * 1000L;

    /**
     * The max number of tasks which is downloading in the same time.
     */
    public int MGD_MAX_NUM_OF_DOWNLOADING_TASK = 3;

    /**
     * The max age of MGD cache before expired.
     */
    int MGD_CACHE_MAX_AGE = 5 * 60 * 1000;

    /**
     * Whether verify file by compare SHA1. If this value is false, MGD will verify file by file's size.
     * Verify the file size is less time consuming than checking SHA1.
     */
    public boolean VERIFY_CACHE_FILE_WITH_SHA1 = true;

    /**
     * Whether auto call init db when create MGDEngine or not, default is true.
     */
    boolean AUTO_INIT_DB_WHEN_CREATE = true;

    /**
     * There will be a deadlock when ShouldInterceptRequest and getCookie are running at the same thread.
     * This bug was found on Android ( < 5.0) system. @see <a href="https://github.com/Tencent/VasMGD/issues/90">Issue 90</a> <br>
     * So MGD will call getCookie before sending MGD request If GET_COOKIE_WHEN_SESSION_CREATE is true.<br>
     * The value of this property should be true unless your app uses <a href="https://x5.tencent.com/tbs">X5 kernel</a>.
     */
    boolean GET_COOKIE_WHEN_SESSION_CREATE = true;

    private MGDConfig() {

    }

    /**
     * Builder for MGDConfig
     */
    public static class Builder {

        private final MGDConfig target;

        public Builder() {
            target = new MGDConfig();
        }

        public Builder setMaxPreloadSessionCount(int maxPreloadSessionCount) {
            target.MAX_PRELOAD_SESSION_COUNT = maxPreloadSessionCount;
            return this;
        }

        public Builder setUnavailableTime(long unavailableTime) {
            target.MGD_UNAVAILABLE_TIME = unavailableTime;
            return this;
        }

        public Builder setCacheVerifyWithSha1(boolean enable) {
            target.VERIFY_CACHE_FILE_WITH_SHA1 = enable;
            return this;
        }

        public Builder setCacheMaxSize(long maxSize) {
            target.MGD_CACHE_MAX_SIZE = maxSize;
            return this;
        }

        public Builder setResourceCacheMaxSize(long maxSize) {
            target.MGD_RESOURCE_CACHE_MAX_SIZE = maxSize;
            return this;
        }

        public Builder setCacheCheckTimeInterval(long time) {
            target.MGD_CACHE_CHECK_TIME_INTERVAL = time;
            return this;
        }

        public Builder setMaxNumOfDownloadingTasks(int num) {
            target.MGD_MAX_NUM_OF_DOWNLOADING_TASK = num;
            return this;
        }

        public Builder setAutoInitDBWhenCreate(boolean autoInitDBWhenCreate) {
            target.AUTO_INIT_DB_WHEN_CREATE = autoInitDBWhenCreate;
            return this;
        }

        public Builder setGetCookieWhenSessionCreate(boolean value) {
            target.GET_COOKIE_WHEN_SESSION_CREATE = value;
            return this;
        }

        public Builder setMGDCacheMaxAge(int maxAge) {
            target.MGD_CACHE_MAX_AGE = maxAge;
            return this;
        }

        public MGDConfig build() {
            return target;
        }
    }

}
