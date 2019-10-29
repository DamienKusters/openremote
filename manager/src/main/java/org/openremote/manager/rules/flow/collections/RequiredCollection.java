package org.openremote.manager.rules.flow.collections;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.openremote.container.Container;
import org.openremote.manager.rules.RulesBuilder;
import org.openremote.manager.rules.RulesEngine;
import org.openremote.manager.rules.flow.NodePair;
import org.openremote.manager.rules.flow.NodeStorageService;
import org.openremote.model.query.AssetQuery;
import org.openremote.model.rules.AssetState;
import org.openremote.model.rules.flow.*;
import org.openremote.model.value.Value;
import org.openremote.model.value.Values;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RequiredCollection implements NodePairCollection {
    @Override
    public List<NodePair> generate(NodeStorageService storageService) {
        List<NodePair> nodePairs = new ArrayList<>();

        nodePairs.add(new NodePair(
                new Node(NodeType.INPUT, "Read attribute", new NodeInternal[]{
                        new NodeInternal("Attribute", new Picker("Asset Attribute", PickerType.ASSET_ATTRIBUTE))
                }, new NodeSocket[0], new NodeSocket[]{
                        new NodeSocket("value", NodeDataType.ANY)
                }),
                info -> {
                    AssetAttributeInternalValue assetAttributePair = Container.JSON.convertValue(info.getInternals()[0].getValue(), AssetAttributeInternalValue.class);
                    String assetId = assetAttributePair.getAssetId();
                    String attributeName = assetAttributePair.getAttributeName();
                    Optional<AssetState> readValue = info.getFacts().matchFirstAssetState(new AssetQuery().ids(assetId).attributeName(attributeName));
                    if (!readValue.isPresent()) return null;
                    return readValue.get().getValue().orElse(null);
                }
        ));

        nodePairs.add(new NodePair(
                new Node(NodeType.OUTPUT, "Write attribute", new NodeInternal[]{
                        new NodeInternal("Attribute", new Picker("Asset Attribute", PickerType.ASSET_ATTRIBUTE))
                }, new NodeSocket[]{
                        new NodeSocket("value", NodeDataType.ANY)
                }, new NodeSocket[0]),
                info -> ((RulesBuilder.Action) facts -> {
                    info.setFacts(facts);
                    Object value = info.getValueFromInput(0, storageService);
                    if (value == null) {
                        RulesEngine.LOG.warning("Flow rule error: node " + info.getNode().getName() + " receives invalid value");
                        return;
                    }
                    AssetAttributeInternalValue assetAttributePair = Container.JSON.convertValue(info.getInternals()[0].getValue(), AssetAttributeInternalValue.class);
                    Optional<AssetState> existingValue = info.getFacts().matchFirstAssetState(new AssetQuery().ids(assetAttributePair.getAssetId()).attributeName(assetAttributePair.getAttributeName()));

                    if (existingValue.isPresent())
                        if (existingValue.get().getValue().isPresent())
                            if (existingValue.get().getValue().get().equals(value)) return;

                    try {
                        if (value instanceof Value) {
                            info.getAssets().dispatch(
                                    assetAttributePair.getAssetId(),
                                    assetAttributePair.getAttributeName(),
                                    (Value) value
                            );
                        } else {
                            info.getAssets().dispatch(
                                    assetAttributePair.getAssetId(),
                                    assetAttributePair.getAttributeName(),
                                    Values.parseOrNull(Container.JSON.writeValueAsString(value))
                            );
                        }
                    } catch (JsonProcessingException e) {
                        RulesEngine.LOG.severe("Flow rule error: node " + info.getNode().getName() + " receives invalid value");
                    }
                })
        ));

        return nodePairs;
    }
}
