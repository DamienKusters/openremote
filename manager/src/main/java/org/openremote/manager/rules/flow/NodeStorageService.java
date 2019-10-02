package org.openremote.manager.rules.flow;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.openremote.container.Container;
import org.openremote.container.ContainerService;
import org.openremote.container.timer.TimerService;
import org.openremote.manager.rules.RulesBuilder;
import org.openremote.manager.rules.RulesEngine;
import org.openremote.manager.rules.flow.collections.RequiredCollection;
import org.openremote.manager.rules.flow.collections.StandardCollection;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.manager.web.ManagerWebService;
import org.openremote.model.query.AssetQuery;
import org.openremote.model.rules.AssetState;
import org.openremote.model.rules.flow.*;
import org.openremote.model.value.Value;
import org.openremote.model.value.Values;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
        nodePairs.addAll(new RequiredCollection().generate(this));
        nodePairs.addAll(new StandardCollection().generate(this));
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
