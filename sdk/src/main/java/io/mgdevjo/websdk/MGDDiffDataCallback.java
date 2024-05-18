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
 * This interface is used to call the difference data between local and server data
 * to the client.
 *
 */
public interface MGDDiffDataCallback {

    /**
     * Called when sonic processes the local data and the server data.
     * When the page requests the latest data, MGD will send the latest
     * data to page by this method.
     *
     * @param resultData The result to page.
     */
    void callback(String resultData);

}
