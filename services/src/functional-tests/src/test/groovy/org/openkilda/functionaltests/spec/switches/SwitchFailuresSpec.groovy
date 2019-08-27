package org.openkilda.functionaltests.spec.switches

import static org.junit.Assume.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.FlowHelperV2
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.northbound.NorthboundServiceV2

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Ignore
import spock.lang.Narrative

@Narrative("""
This spec verifies different situations when Kilda switches suddenly disconnect from the controller.
Note: For now it is only runnable on virtual env due to no ability to disconnect hardware switches
""")
@Tags(VIRTUAL)
class SwitchFailuresSpec extends HealthCheckSpecification {
    @Autowired
    FlowHelperV2 flowHelperV2
    @Autowired
    NorthboundServiceV2 northboundV2

    def "ISL is still able to properly fail even if switches have reconnected"() {
        given: "A flow"
        def isl = topology.getIslsForActiveSwitches().find { it.aswitch && it.dstSwitch }
        assumeTrue("No a-switch ISL found for the test", isl.asBoolean())
        def flow = flowHelper.randomFlow(isl.srcSwitch, isl.dstSwitch)
        flowHelper.addFlow(flow)

        when: "Two neighbouring switches of the flow go down simultaneously"
        lockKeeper.knockoutSwitch(isl.srcSwitch)
        lockKeeper.knockoutSwitch(isl.dstSwitch)
        def timeSwitchesBroke = System.currentTimeMillis()
        def untilIslShouldFail = { timeSwitchesBroke + discoveryTimeout * 1000 - System.currentTimeMillis() }

        and: "ISL between those switches looses connection"
        lockKeeper.removeFlows([isl.aswitch])

        and: "Switches go back up"
        lockKeeper.reviveSwitch(isl.srcSwitch)
        lockKeeper.reviveSwitch(isl.dstSwitch)

        then: "ISL still remains up right before discovery timeout should end"
        sleep(untilIslShouldFail() - 2000)
        islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED

        and: "ISL fails after discovery timeout"
        //TODO(rtretiak): Using big timeout here. This is an abnormal behavior
        Wrappers.wait(untilIslShouldFail() / 1000 + WAIT_OFFSET * 1.5) {
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED
        }

        //depends whether there are alt paths available
        and: "The flow goes down OR changes path to avoid failed ISL after reroute timeout"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            def currentIsls = pathHelper.getInvolvedIsls(PathHelper.convert(northbound.getFlowPath(flow.id)))
            def pathChanged = !currentIsls.contains(isl) && !currentIsls.contains(isl.reversed)
            assert pathChanged || northbound.getFlowStatus(flow.id).status == FlowState.DOWN
        }

        and: "Cleanup: restore connection, remove the flow"
        lockKeeper.addFlows([isl.aswitch])
        flowHelper.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
    }

    @Ignore("Not ready yet")
    //expected to work only via v2 API
    def "System can handle situation when switch reconnects while flow is being created"() {

        when: "Start creating a flow between switches and lose connection to src before rules are set"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        northboundV2.addFlow(flow)
        sleep(50)
        lockKeeper.knockoutSwitch(srcSwitch)

        then: "Flows is 'In progress' retrying to install rules while switch is still marked as ACTIVATED"
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getSwitch(srcSwitch.dpId).state == SwitchChangeType.DEACTIVATED
            assert northbound.getFlowStatus(flow.flowId).status != FlowState.IN_PROGRESS
        }

        and: "Flow eventually goes DOWN when switch officially disconnects"
        northbound.getFlowStatus(flow.flowId).status == FlowState.DOWN

        when: "Switch returns back UP"
        lockKeeper.reviveSwitch(srcSwitch)
        Wrappers.wait(WAIT_OFFSET) { northbound.getSwitch(srcSwitch.dpId).state == SwitchChangeType.ACTIVATED }

        then: "Flow still has no path associated"
        with(northbound.getFlowPath(flow.flowId)) {
            forwardPath.empty
            reversePath.empty
        }

        and: "Src and dst switch validation shows no missing rules"
        [srcSwitch, dstSwitch].each { sw ->
            def validation = northbound.validateSwitch(sw.dpId)
            validation.verifyRuleSectionsAreEmpty(["missing", "proper"])
            validation.verifyMeterSectionsAreEmpty(["missing", "misconfigured", "proper", "excess"])
        }

        when: "Try to validate flow"
        northbound.validateFlow(flow.flowId)

        then: "Error is returned, explaining that this is impossible for DOWN flows"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.UNPROCESSABLE_ENTITY
        e.responseBodyAsString.to(MessageError).errorDescription ==
                "Could not validate flow: Flow $flow.flowId is in DOWN state"

        when: "Bring switch back"
        lockKeeper.reviveSwitch(srcSwitch)

        and: "Reroute the flow"
        def rerouteResponse = northbound.rerouteFlow(flow.flowId)

        then: "Flow is rerouted and in UP state"
        rerouteResponse.rerouted
        Wrappers.wait(WAIT_OFFSET) { northbound.getFlowStatus(flow.flowId).status == FlowState.UP }

        and: "Has a path now"
        with(northbound.getFlowPath(flow.flowId)) {
            !forwardPath.empty
            !reversePath.empty
        }

        and: "Can be validated"
        northbound.validateFlow(flow.flowId).each { assert it.discrepancies.empty }

        and: "Flow can be removed"
        flowHelper.deleteFlow(flow.flowId)
        //sync switch, it may have excess rules if they happen to install before it disconnects. Kilda does not handle it
        northbound.synchronizeSwitch(srcSwitch.dpId, true)
    }

    @Ignore("Not ready yet")
    def "No discrepancies when target transit switch disconnects while flow is being rerouted to it"() {
        given: "A flow with alternative paths available"
        assumeTrue("This test is only viable for h&s reroutes", northbound.getFeatureToggles().flowsRerouteViaFlowHs)
        def switchPair = topologyHelper.getAllNotNeighboringSwitchPairs().find { it.paths.size() > 2 } ?:
                assumeTrue("No suiting switches found", false)
        def flow = flowHelperV2.randomFlow(switchPair)
        northboundV2.addFlow(flow)
        def originalPath = PathHelper.convert(northbound.getFlowPath(flow.flowId))

        and: "There is a more preferable alternative path"
        Switch uniqueSwitch = null
        def preferredPath = switchPair.paths.find { path ->
            uniqueSwitch = pathHelper.getInvolvedSwitches(path).find {
                !pathHelper.getInvolvedSwitches(originalPath).contains(it)
            }
            uniqueSwitch && path != originalPath
        }
        assert preferredPath.asBoolean(), "Didn't find a proper alternative path"
        switchPair.paths.findAll { it != preferredPath }.each { pathHelper.makePathMorePreferable(preferredPath, it) }

        when: "Init reroute of the flow to a better path"
        new Thread({
            with(northbound.rerouteFlow(flow.flowId)) { rerouted }
        }).start()

        and: "Immediately disconnect a switch on the new path"
        lockKeeper.knockoutSwitch(uniqueSwitch)

        then: "The flow is UP and valid"
        Wrappers.wait(WAIT_OFFSET * 3) {
            assert northbound.getFlowStatus(flow.flowId).status == FlowState.UP
        }
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        and: "The flow did not actually change path and fell back to original path"
        PathHelper.convert(northbound.getFlowPath(flow.flowId)) == originalPath

        and: "Revive switch, remove the flow and reset costs"
        lockKeeper.reviveSwitch(uniqueSwitch)
        Wrappers.wait(WAIT_OFFSET) { northbound.getSwitch(uniqueSwitch.dpId).state == SwitchChangeType.ACTIVATED }
        flowHelper.deleteFlow(flow.flowId)
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        Wrappers.wait(discoveryInterval) { northbound.getAllLinks().each { it.state == IslChangeType.DISCOVERED } }
    }
}
