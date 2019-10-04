package org.openkilda.functionaltests.spec.configuration

import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.model.system.KildaConfigurationDto
import org.openkilda.model.FlowEncapsulationType

import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

@Narrative("""
Kilda configuration is a special lever that allows to change default flow encapsulation type while creating.
This spec assumes that 'transit_vlan' is always default type
""")
class ConfigurationSpec extends HealthCheckSpecification {
    @Shared
    FlowEncapsulationType defaultEncapsulationType = FlowEncapsulationType.TRANSIT_VLAN

    @Tags(HARDWARE)
    def "System takes into account default flow encapsulation type while creating a flow"() {
        when: "Update default flow encapsulation type"
        def newFlowEncapsulationType = FlowEncapsulationType.VXLAN
        def updateResponse = northbound.updateKildaConfiguration(
                new KildaConfigurationDto(flowEncapsulationType: newFlowEncapsulationType))

        then: "Correct response is returned"
        updateResponse.flowEncapsulationType == newFlowEncapsulationType.toString().toLowerCase()

        and: "Kilda configuration is really updated"
        northbound.getKildaConfiguration().flowEncapsulationType == newFlowEncapsulationType.toString().toLowerCase()

        when: "Create a flow without encapsulation type"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find {
            it.src.noviflow && !it.src.wb5164 && it.dst.noviflow && !it.dst.wb5164
        }
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.encapsulationType = null
        northboundV2.addFlow(flow)

        then: "Flow is created with default encapsulation type"
        northbound.getFlow(flow.flowId).encapsulationType == newFlowEncapsulationType.toString().toLowerCase()

        cleanup: "Restore default configuration and delete the flow"
        northbound.updateKildaConfiguration(
                new KildaConfigurationDto(flowEncapsulationType: defaultEncapsulationType))
        flowHelper.deleteFlow(flow.flowId)
    }

    @Tags(LOW_PRIORITY)
    def "System doesn't allow to update kilda configuration with wrong flow encapsulation type"() {
        when: "Try to set wrong flow encapsulation type"
        def incorrectValue = "TEST"
        northbound.updateKildaConfiguration(new KildaConfigurationDto(flowEncapsulationType: incorrectValue))

        then: "Human readable error is returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.BAD_REQUEST
        e.responseBodyAsString.to(MessageError).errorMessage ==
                "No enum constant org.openkilda.model.FlowEncapsulationType.$incorrectValue"
        def testIsCompleted = true

        cleanup: "Restore default configuration"
        if (!testIsCompleted) {
            northbound.updateKildaConfiguration(
                    new KildaConfigurationDto(flowEncapsulationType: defaultEncapsulationType))
        }
    }
}
