package org.openremote.manager.rules.flow;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.openremote.container.Container;
import org.openremote.container.ContainerService;
import org.openremote.container.timer.TimerService;
import org.openremote.manager.rules.RulesBuilder;
import org.openremote.manager.rules.RulesEngine;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.manager.web.ManagerWebService;
import org.openremote.model.rules.flow.*;
import org.openremote.model.rules.flow.definition.NodeImplementation;
import org.openremote.model.rules.flow.definition.NodePair;
import org.openremote.model.value.Values;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class NodeStorageService implements ContainerService {
    //TODO: Not sure what to do here yet, so for now it just stores a hardcoded list of node definitions and implementations

    private final List<NodePair> nodePairs = new ArrayList<>();

    @Override
    public int getPriority() {
        return ContainerService.DEFAULT_PRIORITY;
    }

    @Override
    public void init(Container container) throws Exception {
        container.getService(ManagerWebService.class).getApiSingletons().add(
                new FlowResourceImpl(
                        container.getService(TimerService.class),
                        container.getService(ManagerIdentityService.class),
                        this
                )
        );
    }

    @Override
    public void start(Container container) throws Exception {
        //TODO: remove this

        nodePairs.add(new NodePair(
                new Node(NodeType.INPUT, "Boolean", new NodeInternal[]{
                        new NodeInternal("Value", new Picker("Boolean", PickerType.DROPDOWN, new Option[]{
                                new Option("True", true),
                                new Option("False", false)
                        }))
                }, new NodeSocket[0], new NodeSocket[]{
                        new NodeSocket("value", NodeDataType.BOOLEAN)
                }),
                info -> ((boolean) info.getInternals()[0].getValue())
        ));

        nodePairs.add(new NodePair(
                new Node(NodeType.INPUT, "Number", new NodeInternal[]{
                        new NodeInternal("Value", new Picker("Number", PickerType.NUMBER))
                }, new NodeSocket[0], new NodeSocket[]{
                        new NodeSocket("value", NodeDataType.NUMBER)
                }),
                info -> info.getInternals()[0].getValue()
        ));

        nodePairs.add(new NodePair(
                new Node(NodeType.INPUT, "Text", new NodeInternal[]{
                        new NodeInternal("Value", new Picker("Text", PickerType.MULTILINE))
                }, new NodeSocket[0], new NodeSocket[]{
                        new NodeSocket("value", NodeDataType.STRING)
                }),
                info -> info.getInternals()[0].getValue()
        ));

        nodePairs.add(new NodePair(
                new Node(NodeType.INPUT, "Read attribute", new NodeInternal[]{
                        new NodeInternal("Attribute", new Picker("Asset Attribute", PickerType.ASSET_ATTRIBUTE))
                }, new NodeSocket[0], new NodeSocket[]{
                        new NodeSocket("value", NodeDataType.ANY)
                }),
                info -> (info.getInternals()[0].getValue())
        ));

/*        nodePairs.add(new NodePair(
                new Node(NodeType.OUTPUT, "Write attribute", new NodeInternal[]{
                        new NodeInternal("Attribute", new Picker("Asset Attribute", PickerType.ASSET_ATTRIBUTE))
                }, new NodeSocket[]{
                        new NodeSocket("value", NodeDataType.ANY)
                }, new NodeSocket[0]),
                info -> ((RulesBuilder.Action) facts -> {
                    AssetAttributeInternalValue assetAttributePair = (AssetAttributeInternalValue) info.getInternals()[0].getValue();
                    NodeSocket inputSocket = info.getInputs()[0];
                    Node inputNode = info.getCollection().getNodeById(inputSocket.getNodeId());
                    Object value = getImplementationFor(inputNode.getName()).execute(
                            new NodeExecutionRequestInfo(info.getCollection(), inputNode, inputSocket)
                    );

                    try {
                        facts.updateAssetState(assetAttributePair.getAssetId(), assetAttributePair.getAttributeName(), Values.parseOrNull(Container.JSON.writeValueAsString(value)));
                    } catch (JsonProcessingException e) {
                        RulesEngine.LOG.severe("Flow rule error: node " + inputNode.getName() + " outputs invalid value");
                    }
                })
        ));*/

        nodePairs.add(new NodePair(
                new Node(NodeType.OUTPUT, "Log", new NodeInternal[]{
                        new NodeInternal("Level", new Picker("Log level", PickerType.DROPDOWN, new Option[]{
                                new Option("Info", 0),
                                new Option("Warning", 1),
                                new Option("Severe", 2),
                        }))
                }, new NodeSocket[]{
                        new NodeSocket("message", NodeDataType.ANY)
                }, new NodeSocket[0]),
                info -> ((RulesBuilder.Action) facts -> {

                    NodeSocket inputSocket = info.getInputs()[0];
                    Node inputNode = info.getCollection().getNodeById(inputSocket.getNodeId());
                    Object value = getImplementationFor(inputNode.getName()).execute(
                            new NodeExecutionRequestInfo(info.getCollection(), inputNode, inputSocket)
                    );

                    try {
                        RulesEngine.LOG.log(Level.WARNING, "Flow rule " + info.getCollection().getName() + ": " + Container.JSON.writeValueAsString(value));
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                })
        ));
    }

    @Override
    public void stop(Container container) throws Exception {

    }

    public List<NodePair> getNodePairs() {
        return Collections.unmodifiableList(nodePairs);
    }

    public List<Node> getNodes() {
        return Collections.unmodifiableList(nodePairs.stream().map(NodePair::getDefinition).collect(Collectors.toList()));
    }

    public List<NodeImplementation> getNodeImplementations() {
        return Collections.unmodifiableList(nodePairs.stream().map(NodePair::getImplementation).collect(Collectors.toList()));
    }

    public NodeImplementation getImplementationFor(String nodeName) throws IllegalArgumentException {
        for (NodePair pair : nodePairs) {
            if (pair.getDefinition().getName().equals((nodeName)))
                return pair.getImplementation();
        }
        throw new IllegalArgumentException("Invalid node name");
    }
}
