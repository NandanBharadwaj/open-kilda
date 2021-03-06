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

package org.openkilda.simulator.interfaces;

import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.stats.PortStatsEntry;
import org.openkilda.model.SwitchId;
import org.openkilda.simulator.classes.IPortImpl;
import org.openkilda.simulator.classes.SimulatorException;

import org.projectfloodlight.openflow.types.DatapathId;

import java.util.List;
import java.util.Map;

public interface ISwitch {
    void modState(SwitchChangeType state) throws SimulatorException;

    void activate();

    void deactivate();

    boolean isActive();

    int getControlPlaneLatency();

    void setControlPlaneLatency(int controlPlaneLatency);

    DatapathId getDpid();

    String getDpidAsString();

    void setDpid(DatapathId dpid);

    void setDpid(SwitchId dpid);

    List<IPortImpl> getPorts();

    IPortImpl getPort(int portNum) throws SimulatorException;

    int addPort(IPortImpl port) throws SimulatorException;

    int getMaxPorts();

    void setMaxPorts(int maxPorts);

    Map<Long, IFlow> getFlows();

    IFlow getFlow(long cookie) throws SimulatorException;

    void addFlow(IFlow flow) throws SimulatorException;

    void modFlow(IFlow flow) throws SimulatorException;

    void delFlow(long cookie) throws SimulatorException;

    List<PortStatsEntry> getPortStats();

    PortStatsEntry getPortStats(int portNum);
}
