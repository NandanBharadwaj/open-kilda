/* Copyright 2017 Telstra Open Source
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

package org.openkilda.messaging.info.flow;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.model.FlowDto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

/**
 * Represents a flow read northbound response.
 */
@Value
public class FlowReadResponse extends InfoData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The response payload.
     */
    @JsonProperty("payload")
    protected FlowDto payload;

    @JsonProperty("diverse_with")
    protected List<String> diverseWith;

    /**
     * Instance constructor.
     *
     * @param payload response payload
     */
    @JsonCreator
    public FlowReadResponse(@JsonProperty("payload") FlowDto payload,
                            @JsonProperty("diverse_with") List<String> diverseWith) {
        this.payload = payload;
        this.diverseWith = diverseWith;
    }
}
