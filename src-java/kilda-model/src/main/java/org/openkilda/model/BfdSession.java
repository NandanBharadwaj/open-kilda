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

package org.openkilda.model;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.Index;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.typeconversion.Convert;

@Data
@NoArgsConstructor
@NodeEntity(label = "bfd_session")
public class BfdSession {

    public static final String SWITCH_PROPERTY_NAME = "switch";
    public static final String IP_ADDRESS_PROPERTY_NAME = "ip_address";
    public static final String REMOTE_SWITCH_PROPERTY_NAME = "remote_switch";
    public static final String REMOVE_IP_ADDRESS_PROPERTY_NAME = "remote_ip_address";
    public static final String PORT_PROPERTY_NAME = "port";
    public static final String DISCRIMINATOR_PROPERTY_NAME = "discriminator";

    // Hidden as needed for OGM only.
    @Id
    @GeneratedValue
    @Setter(AccessLevel.NONE)
    private Long entityId;

    @NonNull
    @Property(name = SWITCH_PROPERTY_NAME)
    @Convert(graphPropertyType = String.class)
    private SwitchId switchId;

    @Property(name = IP_ADDRESS_PROPERTY_NAME)
    private String ipAddress;

    @Property(name = REMOTE_SWITCH_PROPERTY_NAME)
    @Convert(graphPropertyType = String.class)
    private SwitchId remoteSwitchId;

    @Property(name = REMOVE_IP_ADDRESS_PROPERTY_NAME)
    private String remoteIpAddress;

    @NonNull
    @Property(name = PORT_PROPERTY_NAME)
    private Integer port;

    @Property(name = DISCRIMINATOR_PROPERTY_NAME)
    @Index(unique = true)
    private Integer discriminator;

    public BfdSession(@NonNull SwitchId switchId,
                      @NonNull Integer port) {
        this(switchId, null, null, null, port, null);
    }

    @Builder
    private BfdSession(SwitchId switchId, String ipAddress, SwitchId remoteSwitchId, String remoteIpAddress,
                         Integer port, Integer discriminator) {
        this.switchId = switchId;
        this.ipAddress = ipAddress;
        this.remoteSwitchId = remoteSwitchId;
        this.remoteIpAddress = remoteIpAddress;
        this.port = port;
        this.discriminator = discriminator;
    }

    /**
     * BfdSession builder with prefilled mandatory fields.
     */
    public static BfdSessionBuilder builder(@NonNull SwitchId switchId,
                                            @NonNull Integer port) {
        return new BfdSessionBuilder()
                .switchId(switchId)
                .port(port);
    }
}
