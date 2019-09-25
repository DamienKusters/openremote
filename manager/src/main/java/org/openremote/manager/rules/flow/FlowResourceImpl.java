package org.openremote.manager.rules.flow;

import org.openremote.container.timer.TimerService;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.manager.web.ManagerWebResource;
import org.openremote.model.http.RequestParams;
import org.openremote.model.rules.flow.FlowResource;
import org.openremote.model.rules.flow.Node;
import org.openremote.model.rules.flow.NodeType;

import java.util.logging.Logger;

public class FlowResourceImpl extends ManagerWebResource implements FlowResource {

    private static final Logger LOG = Logger.getLogger(FlowResourceImpl.class.getName());
    final private NodeStorageService nodeStorageService;

    public FlowResourceImpl(TimerService timerService, ManagerIdentityService identityService, NodeStorageService nodeStorageService) {
        super(timerService, identityService);
        this.nodeStorageService = nodeStorageService;
        for (Node node : nodeStorageService.getNodes()) {
            LOG.info("Node found: " + node.getName());
        }
    }

    @Override
    public Node[] getAllNodeDefinitions(RequestParams requestParams) {
        return nodeStorageService.getNodes().toArray(new Node[0]);
    }

    @Override
    public Node[] getAllNodeDefinitionsByType(RequestParams requestParams, NodeType type) {
        return nodeStorageService.getNodes().stream().filter((n) -> n.getType().equals(type)).toArray(Node[]::new);
    }

    @Override
    public Node getNodeDefinition(RequestParams requestParams, String name) {
        return nodeStorageService.getNodes().stream().filter(n -> n.getName().equals(name)).findFirst().orElse(null);
    }
}
