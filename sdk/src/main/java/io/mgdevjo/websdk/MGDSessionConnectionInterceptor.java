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

import android.content.Intent;

/**
 * <code>MGDSessionConnectionInterceptor</code> provide a <code>MGDSessionConnection</code>.
 * If an {@link MGDSessionConfig}does not set <code>MGDSessionConnectionInterceptor</code>
 * MGD will use {@link io.mgdevjo.websdk.MGDSessionConnection.SessionConnectionDefaultImpl}
 * as default.
 *
 */

public abstract class MGDSessionConnectionInterceptor {

    public abstract MGDSessionConnection getConnection(MGDSession session, Intent intent);

    public static MGDSessionConnection getMGDSessionConnection(MGDSession session, Intent intent) {
        MGDSessionConnectionInterceptor interceptor = session.config.connectionInterceptor;
        if (interceptor != null) {
            return interceptor.getConnection(session, intent);
        }
        return new MGDSessionConnection.SessionConnectionDefaultImpl(session, intent);
    }
}
