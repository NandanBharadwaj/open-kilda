/* Copyright 2019 Telstra Open Source
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

package org.openkilda.floodlight.command.flow.ingress;

import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.MeterConfig;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;

import lombok.Getter;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
abstract class SingleSwitchFlowBlankCommand extends AbstractIngressFlowSegmentCommand {
    protected final FlowEndpoint egressEndpoint;

    SingleSwitchFlowBlankCommand(
            MessageContext messageContext, UUID commandId, String flowId, Cookie cookie,
            FlowEndpoint endpoint, MeterConfig meterConfig, FlowEndpoint egressEndpoint) {
        super(messageContext, endpoint.getDatapath(), commandId, flowId, cookie, endpoint, meterConfig);
        this.egressEndpoint = egressEndpoint;
    }

    @Override
    protected void validate() {
        super.validate();

        if (! getSwitchId().equals(egressEndpoint.getDatapath())) {
            throw new IllegalArgumentException(String.format(
                    "Ingress(%s) and egress(%s) switches must match in %s",
                    getSwitchId(), egressEndpoint.getDatapath(), getClass().getName()));
        }
    }

    @Override
    protected List<OFAction> makeTransformActions(OFFactory of) {
        List<Integer> currentVlanStack = new ArrayList<>(2);
        // outer VLAN ID was removed by pre-match rule
        if (FlowEndpoint.isVlanIdSet(endpoint.getInnerVlanId())) {
            currentVlanStack.add(endpoint.getInnerVlanId());
        }

        List<Integer> desiredVlanStack = egressEndpoint.getVlanStack();
        return OfAdapter.INSTANCE.makeVlanReplaceActions(of, currentVlanStack, desiredVlanStack);
    }

    @Override
    protected OFAction makeOutputAction(OFFactory of) {
        if (endpoint.getPortNumber().equals(egressEndpoint.getPortNumber())) {
            return super.makeOutputAction(of, OFPort.IN_PORT);
        }
        return super.makeOutputAction(of, OFPort.of(egressEndpoint.getPortNumber()));
    }
}
