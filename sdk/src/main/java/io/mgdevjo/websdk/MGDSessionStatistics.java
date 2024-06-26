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

import java.net.HttpURLConnection;
/**
 * The Statistic model specifies the data models which are required to be used to provide
 * the performance data described by the specific attributes in a MGDSession.
 */

public class MGDSessionStatistics {

    /**
     * Original url
     */
    public String srcUrl;

    /**
     * MGD final mode{@link MGDSession#finalResultCode}
     */
    public int finalMode;

    /**
     * MGD original mode{@link MGDSession#srcResultCode}
     */
    public int originalMode;

    /**
     * MGD start {@link MGDSession#start()} time
     */
    public long MGDStartTime;

    /**
     * MGD flow start{@link MGDSession#runMGDFlow(boolean)} time
     */
    public long MGDFlowStartTime;

    /**
     * The time that MGD begin verify local data
     */
    public long cacheVerifyTime;

    /**
     * The time MGD initiate the http(s) request
     */
    public long connectionFlowStartTime;

    /**
     * The http(s) connect{@link HttpURLConnection#connect()} response time
     */
    public long connectionConnectTime;

    /**
     * The http(s) getResponseCode{@link HttpURLConnection#getResponseCode()} response time
     */
    public long connectionRespondTime;

    /**
     * MGD flow end time
     */
    public long connectionFlowFinishTime;

    /**
     * Is IP direct
     */
    public boolean isDirectAddress;


    /**
     * The time when website try get diff data.
     */
    public long diffDataCallbackTime;
}
