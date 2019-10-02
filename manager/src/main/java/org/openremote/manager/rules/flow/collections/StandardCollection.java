package org.openremote.manager.rules.flow.collections;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.openremote.container.Container;
import org.openremote.manager.rules.RulesBuilder;
import org.openremote.manager.rules.RulesEngine;
import org.openremote.manager.rules.flow.NodePair;
import org.openremote.manager.rules.flow.NodeStorageService;
import org.openremote.model.rules.flow.*;
import org.openremote.model.value.BooleanValue;
import org.openremote.model.value.NumberValue;
import org.openremote.model.value.Values;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class StandardCollection implements NodePairCollection {
    public List<NodePair> generate(NodeStorageService storage) {
        List<NodePair> nodePairs = new ArrayList<>();

        nodePairs.add(new NodePair(
                new Node(NodeType.INPUT, "Boolean", new NodeInternal[]{
                        new NodeInternal("Value", new Picker("Boolean", PickerType.DROPDOWN, new Option[]{
                                new Option("True", true),
                                new Option("False", false)
                        }))
                }, new NodeSocket[0], new NodeSocket[]{
                        new NodeSocket("value", NodeDataType.BOOLEAN)
                }),
                info -> Values.create((boolean) info.getInternals()[0].getValue())
        ));

        nodePairs.add(new NodePair(
                new Node(NodeType.INPUT, "Number", new NodeInternal[]{
                        new NodeInternal("Value", new Picker("Number", PickerType.NUMBER))
                }, new NodeSocket[0], new NodeSocket[]{
                        new NodeSocket("value", NodeDataType.NUMBER)
                }),
                info -> Values.create((float) info.getInternals()[0].getValue())
        ));

        nodePairs.add(new NodePair(
                new Node(NodeType.INPUT, "Text", new NodeInternal[]{
                        new NodeInternal("Value", new Picker("Text", PickerType.MULTILINE))
                }, new NodeSocket[0], new NodeSocket[]{
                        new NodeSocket("value", NodeDataType.STRING)
                }),
                info -> Values.create((String) info.getInternals()[0].getValue())
        ));

        nodePairs.add(new NodePair(
                new Node(NodeType.PROCESSOR, "Add", new NodeInternal[0], new NodeSocket[]{
                        new NodeSocket("a", NodeDataType.NUMBER),
                        new NodeSocket("b", NodeDataType.NUMBER),
                }, new NodeSocket[]{
                        new NodeSocket("c", NodeDataType.NUMBER),
                }),
                info -> {
                    NumberValue a = (NumberValue) info.getValueFromInput(0, storage);
                    NumberValue b = (NumberValue) info.getValueFromInput(1, storage);
                    return Values.create(a.getNumber() + b.getNumber());
                }
        ));

        nodePairs.add(new NodePair(
                new Node(NodeType.PROCESSOR, "And", new NodeInternal[0], new NodeSocket[]{
                        new NodeSocket("a", NodeDataType.BOOLEAN),
                        new NodeSocket("b", NodeDataType.BOOLEAN),
                }, new NodeSocket[]{
                        new NodeSocket("c", NodeDataType.BOOLEAN),
                }),
                info -> {
                    BooleanValue a = (BooleanValue) info.getValueFromInput(0, storage);
                    BooleanValue b = (BooleanValue) info.getValueFromInput(1, storage);
                    return Values.create(a.getBoolean() && b.getBoolean());
                }
        ));

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
                    info.setFacts(facts);
                    Object value = info.getValueFromInput(0, storage);

                    try {
                        switch ((int) info.getInternals()[0].getValue()) {
                            case 0:
                                RulesEngine.LOG.log(Level.INFO, "Flow rule " + info.getCollection().getName() + ": " + Container.JSON.writeValueAsString(value));
                                break;
                            case 1:
                                RulesEngine.LOG.log(Level.WARNING, "Flow rule " + info.getCollection().getName() + ": " + Container.JSON.writeValueAsString(value));
                                break;
                            case 2:
                                RulesEngine.LOG.log(Level.SEVERE, "Flow rule " + info.getCollection().getName() + ": " + Container.JSON.writeValueAsString(value));
                                break;
                        }

                    } catch (JsonProcessingException e) {
                        RulesEngine.LOG.severe("Flow rule error: node " + info.getNode().getName() + " receives invalid value");
                    }
                })
        ));

        return nodePairs;
    }
}
