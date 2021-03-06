/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.floodlight.error;

import org.openkilda.messaging.model.Ping;

public class PingImpossibleException extends Exception {
    private final Ping ping;

    private final Ping.Errors error;

    public PingImpossibleException(Ping ping, Ping.Errors error) {
        super(String.format("Unable to send ping %s - %s", ping, error));
        this.ping = ping;
        this.error = error;
    }

    public Ping getPing() {
        return ping;
    }

    public Ping.Errors getError() {
        return error;
    }
}
