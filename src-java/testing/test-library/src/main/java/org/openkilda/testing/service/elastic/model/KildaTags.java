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

package org.openkilda.testing.service.elastic.model;

public final class KildaTags {
    public static final String NORTHBOUND = "kilda-northbound";
    public static final String STORM_WORKER = "storm-worker_log";
    public static final String TOPOLOGY_ENGINE = "kilda-tpe";
    public static final String FLOODLIGHT = "kilda-floodlight";

    private KildaTags() {
        throw new UnsupportedOperationException();
    }

}
