package org.bitbucket.openkilda.floodlight.kafka;

import static org.bitbucket.openkilda.messaging.Utils.MAPPER;

import org.bitbucket.openkilda.floodlight.pathverification.IPathVerificationService;
import org.bitbucket.openkilda.floodlight.switchmanager.ISwitchManager;
import org.bitbucket.openkilda.floodlight.switchmanager.MeterPool;
import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.Topic;
import org.bitbucket.openkilda.messaging.command.CommandData;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.bitbucket.openkilda.messaging.command.discovery.DiscoverPathCommandData;
import org.bitbucket.openkilda.messaging.command.flow.InstallEgressFlow;
import org.bitbucket.openkilda.messaging.command.flow.InstallIngressFlow;
import org.bitbucket.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.bitbucket.openkilda.messaging.command.flow.InstallTransitFlow;
import org.bitbucket.openkilda.messaging.command.flow.RemoveFlow;
import org.bitbucket.openkilda.messaging.error.ErrorData;
import org.bitbucket.openkilda.messaging.error.ErrorMessage;
import org.bitbucket.openkilda.messaging.error.ErrorType;
import org.bitbucket.openkilda.messaging.payload.flow.OutputVlanType;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KafkaMessageCollector implements IFloodlightModule {
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageCollector.class);
    private static final String INPUT_TOPIC = "kilda-test";//Topic.WFM_OFS_FLOW.getId();
    private static final String OUTPUT_TOPIC = "kilda-test";//Topic.OFS_WFM_FLOW.getId();
    private final MeterPool meterPool = new MeterPool();
    private Properties kafkaProps;
    private IPathVerificationService pathVerificationService;
    private KafkaMessageProducer kafkaProducer;
    private ISwitchManager switchManager;
    private String zookeeperHosts;

    /**
     * IFloodLightModule Methods
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> services = new ArrayList<>(2);
        services.add(IPathVerificationService.class);
        services.add(ISwitchManager.class);
        return services;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        pathVerificationService = context.getServiceImpl(IPathVerificationService.class);
        switchManager = context.getServiceImpl(ISwitchManager.class);
        kafkaProducer = context.getServiceImpl(KafkaMessageProducer.class);
        Map<String, String> configParameters = context.getConfigParams(this);
        kafkaProps = new Properties();
        kafkaProps.put("bootstrap.servers", configParameters.get("bootstrap-servers"));
        kafkaProps.put("group.id", "kilda-message-collector");
        kafkaProps.put("enable.auto.commit", "true");
        //kafkaProps.put("auto.commit.interval.ms", "1000");
        kafkaProps.put("session.timeout.ms", "30000");
        kafkaProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        kafkaProps.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        zookeeperHosts = configParameters.get("zookeeper-hosts");
    }

    @Override
    public void startUp(FloodlightModuleContext floodlightModuleContext) throws FloodlightModuleException {
        logger.info("Starting {}", this.getClass().getCanonicalName());
        try {
            ExecutorService parseRecordExecutor = Executors.newFixedThreadPool(10);
            ExecutorService consumerExecutor = Executors.newSingleThreadExecutor();
            consumerExecutor.execute(new Consumer(
                    Collections.singletonList(INPUT_TOPIC), kafkaProps, parseRecordExecutor));
        } catch (Exception exception) {
            logger.error("error", exception);
        }
    }

    class ParseRecord implements Runnable {
        final ConsumerRecord record;

        public ParseRecord(ConsumerRecord record) {
            this.record = record;
        }

        private void doControllerMsg(CommandMessage message) {
            CommandData data = message.getData();
            if (data instanceof DiscoverIslCommandData) {
                doDiscoverIslCommand(data);
            } else if (data instanceof DiscoverPathCommandData) {
                doDiscoverPathCommand(data);
            } else if (data instanceof InstallIngressFlow) {
                doInstallIngressFlow(message);
            } else if (data instanceof InstallEgressFlow) {
                doInstallEgressFlow(message);
            } else if (data instanceof InstallTransitFlow) {
                doInstallTransitFlow(message);
            } else if (data instanceof InstallOneSwitchFlow) {
                doInstallOneSwitchFlow(message);
            } else if (data instanceof RemoveFlow) {
                doDeleteFlow(message);
            } else {
                logger.error("unknown data type: {}", data.toString());
            }
        }

        private void doDiscoverIslCommand(CommandData data) {
            DiscoverIslCommandData command = (DiscoverIslCommandData) data;
            logger.debug("sending discover ISL to {}", command);
            pathVerificationService.sendDiscoveryMessage(DatapathId.of(command.getSwitchId()),
                    OFPort.of(command.getPortNo()));
        }

        private void doDiscoverPathCommand(CommandData data) {
            DiscoverPathCommandData command = (DiscoverPathCommandData) data;
            logger.debug("sending discover Path to {}", command);
        }

        /**
         * Installs ingress flow on the switch.
         *
         * @param message command message for flow installation
         */
        private void doInstallIngressFlow(final CommandMessage message) {
            InstallIngressFlow command = (InstallIngressFlow) message.getData();
            logger.debug("Creating an ingress flow: {}", command);

            int meterId = meterPool.allocate(command.getSwitchId(), command.getId());

            ImmutablePair<Long, Boolean> meterInstalled = switchManager.installMeter(
                    DatapathId.of(command.getSwitchId()),
                    command.getBandwidth(),
                    1024,
                    meterId);

            if (!meterInstalled.getRight()) {
                ErrorMessage error = new ErrorMessage(new ErrorData(ErrorType.INTERNAL_ERROR, command.getId()),
                        System.currentTimeMillis(), message.getCorrelationId());
                kafkaProducer.postMessage(OUTPUT_TOPIC, error);
            } else {
                command.setTransactionId(meterInstalled.getLeft());
                kafkaProducer.postMessage(OUTPUT_TOPIC, message);
            }

            ImmutablePair<Long, Boolean> flowInstalled = switchManager.installIngressFlow(
                    DatapathId.of(command.getSwitchId()),
                    command.getId(),
                    command.getCookie(),
                    command.getInputPort(),
                    command.getOutputPort(),
                    command.getInputVlanId(),
                    command.getTransitVlanId(),
                    command.getOutputVlanType(),
                    meterId);

            if (!flowInstalled.getRight()) {
                ErrorMessage error = new ErrorMessage(new ErrorData(ErrorType.INTERNAL_ERROR, command.getId()),
                        System.currentTimeMillis(), message.getCorrelationId());
                kafkaProducer.postMessage(OUTPUT_TOPIC, error);
            } else {
                command.setTransactionId(flowInstalled.getLeft());
                kafkaProducer.postMessage(OUTPUT_TOPIC, message);
            }
        }

        /**
         * Installs egress flow on the switch.
         *
         * @param message command message for flow installation
         */
        private void doInstallEgressFlow(final CommandMessage message) {
            InstallEgressFlow command = (InstallEgressFlow) message.getData();
            logger.debug("Creating an egress flow: {}", command);

            ImmutablePair<Long, Boolean> flowInstalled = switchManager.installEgressFlow(
                    DatapathId.of(command.getSwitchId()),
                    command.getId(),
                    command.getCookie(),
                    command.getInputPort(),
                    command.getOutputPort(),
                    command.getTransitVlanId(),
                    command.getOutputVlanId(),
                    command.getOutputVlanType());

            if (!flowInstalled.getRight()) {
                ErrorMessage error = new ErrorMessage(new ErrorData(ErrorType.INTERNAL_ERROR, command.getId()),
                        System.currentTimeMillis(), message.getCorrelationId());
                kafkaProducer.postMessage(OUTPUT_TOPIC, error);
            } else {
                command.setTransactionId(flowInstalled.getLeft());
                kafkaProducer.postMessage(OUTPUT_TOPIC, message);
            }
        }

        /**
         * Installs transit flow on the switch.
         *
         * @param message command message for flow installation
         */
        private void doInstallTransitFlow(final CommandMessage message) {
            InstallTransitFlow command = (InstallTransitFlow) message.getData();
            logger.debug("Creating a transit flow: {}", command);

            ImmutablePair<Long, Boolean> flowInstalled = switchManager.installTransitFlow(
                    DatapathId.of(command.getSwitchId()),
                    command.getId(),
                    command.getCookie(),
                    command.getInputPort(),
                    command.getOutputPort(),
                    command.getTransitVlanId());

            if (!flowInstalled.getRight()) {
                ErrorMessage error = new ErrorMessage(new ErrorData(ErrorType.INTERNAL_ERROR, command.getId()),
                        System.currentTimeMillis(), message.getCorrelationId());
                kafkaProducer.postMessage(OUTPUT_TOPIC, error);
            } else {
                command.setTransactionId(flowInstalled.getLeft());
                kafkaProducer.postMessage(OUTPUT_TOPIC, message);
            }
        }

        /**
         * Installs flow through one switch.
         *
         * @param message command message for flow installation
         */
        private void doInstallOneSwitchFlow(final CommandMessage message) {
            InstallOneSwitchFlow command = (InstallOneSwitchFlow) message.getData();
            logger.debug("creating a flow through one switch: {}", command);

            int sourceMeterId = meterPool.allocate(command.getSwitchId(), command.getId());
            int destinationMeterId = meterPool.allocate(command.getSwitchId(), command.getId());

            ImmutablePair<Long, Boolean> sourceMeterInstalled = switchManager.installMeter(
                    DatapathId.of(command.getSwitchId()),
                    command.getBandwidth(),
                    1024,
                    sourceMeterId);

            if (!sourceMeterInstalled.getRight()) {
                ErrorMessage error = new ErrorMessage(new ErrorData(ErrorType.INTERNAL_ERROR, command.getId()),
                        System.currentTimeMillis(), message.getCorrelationId());
                kafkaProducer.postMessage(OUTPUT_TOPIC, error);
            } else {
                command.setTransactionId(sourceMeterInstalled.getLeft());
                kafkaProducer.postMessage(OUTPUT_TOPIC, message);
            }

            OutputVlanType directOutputVlanType = command.getOutputVlanType();
            ImmutablePair<Long, Boolean> forwardFlowInstalled = switchManager.installOneSwitchFlow(
                    DatapathId.of(command.getSwitchId()),
                    command.getId(),
                    command.getCookie(),
                    command.getInputPort(),
                    command.getOutputPort(),
                    command.getInputVlanId(),
                    command.getOutputVlanId(),
                    directOutputVlanType,
                    sourceMeterId);

            if (!forwardFlowInstalled.getRight()) {
                ErrorMessage error = new ErrorMessage(new ErrorData(ErrorType.INTERNAL_ERROR, command.getId()),
                        System.currentTimeMillis(), message.getCorrelationId());
                kafkaProducer.postMessage(OUTPUT_TOPIC, error);
            } else {
                command.setTransactionId(forwardFlowInstalled.getLeft());
                kafkaProducer.postMessage(OUTPUT_TOPIC, message);
            }

            ImmutablePair<Long, Boolean> destinationMeterInstalled = switchManager.installMeter(
                    DatapathId.of(command.getSwitchId()),
                    command.getBandwidth(),
                    1024,
                    destinationMeterId);

            if (!destinationMeterInstalled.getRight()) {
                ErrorMessage error = new ErrorMessage(new ErrorData(ErrorType.INTERNAL_ERROR, command.getId()),
                        System.currentTimeMillis(), message.getCorrelationId());
                kafkaProducer.postMessage(OUTPUT_TOPIC, error);
            } else {
                command.setTransactionId(destinationMeterInstalled.getLeft());
                kafkaProducer.postMessage(OUTPUT_TOPIC, message);
            }

            OutputVlanType reverseOutputVlanType;
            switch (directOutputVlanType) {
                case POP:
                    reverseOutputVlanType = OutputVlanType.PUSH;
                    break;
                case PUSH:
                    reverseOutputVlanType = OutputVlanType.POP;
                    break;
                default:
                    reverseOutputVlanType = directOutputVlanType;
                    break;
            }
            ImmutablePair<Long, Boolean> reverseFlowInstalled = switchManager.installOneSwitchFlow(
                    DatapathId.of(command.getSwitchId()),
                    command.getId(),
                    command.getCookie(),
                    command.getOutputPort(),
                    command.getInputPort(),
                    command.getOutputVlanId(),
                    command.getInputVlanId(),
                    reverseOutputVlanType,
                    destinationMeterId);

            if (!reverseFlowInstalled.getRight()) {
                ErrorMessage error = new ErrorMessage(new ErrorData(ErrorType.INTERNAL_ERROR, command.getId()),
                        System.currentTimeMillis(), message.getCorrelationId());
                kafkaProducer.postMessage(OUTPUT_TOPIC, error);
            } else {
                command.setTransactionId(reverseFlowInstalled.getLeft());
                kafkaProducer.postMessage(OUTPUT_TOPIC, message);
            }
        }

        /**
         * Removes flow.
         *
         * @param message command message for flow installation
         */
        private void doDeleteFlow(final CommandMessage message) {
            RemoveFlow command = (RemoveFlow) message.getData();
            logger.debug("deleting a flow: {}", command);

            DatapathId dpid = DatapathId.of(command.getSwitchId());
            ImmutablePair<Long, Boolean> flowDeleted = switchManager.deleteFlow(
                    dpid, command.getId(), command.getCookie());

            if (!flowDeleted.getRight()) {
                ErrorMessage error = new ErrorMessage(new ErrorData(ErrorType.INTERNAL_ERROR, command.getId()),
                        System.currentTimeMillis(), message.getCorrelationId());
                kafkaProducer.postMessage(OUTPUT_TOPIC, error);
            } else {
                command.setTransactionId(flowDeleted.getLeft());
                kafkaProducer.postMessage(OUTPUT_TOPIC, message);
            }

            Integer meterId = meterPool.deallocate(command.getSwitchId(), command.getId());

            if (flowDeleted.getRight() && meterId != null) {
                ImmutablePair<Long, Boolean> meterDeleted = switchManager.deleteMeter(dpid, meterId);
                if (!meterDeleted.getRight()) {
                    ErrorMessage error = new ErrorMessage(new ErrorData(ErrorType.INTERNAL_ERROR, command.getId()),
                            System.currentTimeMillis(), message.getCorrelationId());
                    kafkaProducer.postMessage(OUTPUT_TOPIC, error);
                } else {
                    command.setTransactionId(meterDeleted.getLeft());
                    kafkaProducer.postMessage(OUTPUT_TOPIC, message);
                }
            }
        }

        private void parseRecord(ConsumerRecord record) {
            try {
                if (record.value() instanceof String) {
                    String value = (String) record.value();
                    Message message = MAPPER.readValue(value, Message.class);
                    if (message instanceof CommandMessage) {
                        logger.debug("got a command message");
                        CommandMessage cmdMessage = (CommandMessage) message;
                        switch (cmdMessage.getData().getDestination()) {
                            case CONTROLLER:
                                doControllerMsg(cmdMessage);
                                break;
                            default:
                                break;
                        }
                    }
                } else {
                    logger.error("{} not of type String", record.value());
                }
            } catch (Exception exception) {
                logger.error("error parsing record.", exception);
            }
        }

        @Override
        public void run() {
            parseRecord(record);
        }
    }

    class Consumer implements Runnable {
        final List<String> topics;
        final Properties kafkaProps;
        final ExecutorService parseRecordExecutor;

        public Consumer(List<String> topics, Properties kafkaProps, ExecutorService parseRecordExecutor) {
            this.topics = topics;
            this.kafkaProps = kafkaProps;
            this.parseRecordExecutor = parseRecordExecutor;
        }

        @Override
        public void run() {
            KafkaConsumer<String, String> consumer = new KafkaConsumer<>(kafkaProps);
            consumer.subscribe(topics);

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(100);
                for (ConsumerRecord<String, String> record : records) {
                    logger.debug("received message: {} - {}", record.offset(), record.value());
                    parseRecordExecutor.execute(new ParseRecord(record));
                }
            }
        }
    }
}
