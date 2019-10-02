package org.openremote.manager.rules;

import org.jeasy.rules.api.Rule;
import org.jeasy.rules.core.RuleBuilder;
import org.openremote.container.Container;
import org.openremote.manager.rules.flow.NodeExecutionRequestInfo;
import org.openremote.manager.rules.flow.NodeStorageService;
import org.openremote.model.query.AssetQuery;
import org.openremote.model.rules.AssetState;
import org.openremote.model.rules.Assets;
import org.openremote.model.rules.Notifications;
import org.openremote.model.rules.Users;
import org.openremote.model.rules.flow.*;

import java.util.*;
import java.util.stream.Collectors;

public class FlowRulesBuilder extends RulesBuilder {
    private Map<Action, Long> lastRanMap = new LinkedHashMap<>();

    private List<NodeCollection> nodeCollections = new ArrayList<>();
    private NodeStorageService nodeStorageService;
    private Notifications notification;
    private Users users;
    private Assets assets;

    public void add(NodeCollection nodeCollection) {
        nodeCollections.add(nodeCollection);
    }

    public Rule[] build() {

        if (nodeStorageService == null)
            throw new NullPointerException("No node storage service set");
        int count = 0;
        List<Rule> rules = new ArrayList<>();
        for (NodeCollection collection : nodeCollections) {
            for (Node node : collection.getNodes()) {
                if (node.getType() != NodeType.OUTPUT) continue;
                try {
                    RulesEngine.RULES_LOG.info("Flow rule created");
                    rules.add(createRule(collection.getName() + " - " + count, collection, node));
                    count++;
                } catch (Exception e) {
                    RulesEngine.RULES_LOG.severe("Flow rule error: " + e.getMessage());
                }
            }
        }
        return rules.toArray(new Rule[0]);
    }

    private Rule createRule(String name, NodeCollection collection, Node outputNode) throws Exception {
        Object implementationResult = nodeStorageService.getImplementationFor(outputNode.getName()).execute(new NodeExecutionRequestInfo(collection, outputNode, null, null, assets, users, notification));

        if (implementationResult == null)
            throw new NullPointerException(outputNode.getName() + " node returns null");

        if (!(implementationResult instanceof Action))
            throw new Exception(outputNode.getName() + " node does not return an action");

        Action action = (Action) implementationResult;

        Condition condition = facts -> {
            List<Node> connectedTree = backtrackFrom(collection, outputNode);

            //TODO: should there be hardcoded (always available no matter the user configuration) asset (read, write) nodes?
            // Definitely don't check by name and assume internal types if not (and even if, this isn't great)
            return connectedTree.stream().filter(c -> c.getName().equals("Read attribute")).anyMatch(c -> {
                AssetAttributeInternalValue internal = Container.JSON.convertValue(c.getInternals()[0].getValue(), AssetAttributeInternalValue.class);
                String assetId = internal.getAssetId();
                String attributeName = internal.getAttributeName();
                List<AssetState> allAssets = facts.matchAssetState(new AssetQuery().
                        select(AssetQuery.Select.selectAll()).ids(assetId).attributeName(attributeName)
                ).collect(Collectors.toList());

                return allAssets.stream().anyMatch(state -> {
                    long timestamp = state.getTimestamp();
                    RulesEngine.RULES_LOG.info("Firing rule when " + timestamp + " is more than " + lastRanMap.get(action));
                    return timestamp > lastRanMap.getOrDefault(action, -1L);
                });
            });
        };

        return new RuleBuilder().
                name(name).
                description(collection.getDescription()).
                when(facts -> {
                    Object result;
                    try {
                        result = condition.evaluate((RulesFacts) facts);
                    } catch (Exception ex) {
                        throw new RuntimeException("Error evaluating condition of rule '" + name + "': " + ex.getMessage(), ex);
                    }
                    if (result instanceof Boolean) {
                        return (boolean) result;
                    } else {
                        throw new IllegalArgumentException("Error evaluating condition of rule '" + name + "': result is not boolean but " + result);
                    }
                }).
                then(facts -> {
                    action.execute((RulesFacts) facts);
                    lastRanMap.put(action, ((RulesFacts) facts).timerService.getCurrentTimeMillis());
                }).
                build();
    }

    private List<Node> backtrackFrom(NodeCollection collection, Node node) {
        List<Node> total = new ArrayList<>();
        List<Node> children = new ArrayList<>();

        for (NodeSocket s : node.getInputs()) {
            children.addAll(Arrays.stream(collection.getConnections()).filter(c -> c.getTo().equals(s)).map(c -> collection.getNodeById(c.getFrom().getNodeId())).collect(Collectors.toList()));
        }

        for (Node child : children) {
            total.add(child);
            total.addAll(backtrackFrom(collection, child));
        }

        return total;
    }

    public NodeStorageService getNodeStorageService() {
        return nodeStorageService;
    }

    public void setNodeStorageService(NodeStorageService nodeStorageService) {
        this.nodeStorageService = nodeStorageService;
    }

    public Notifications getNotification() {
        return notification;
    }

    public void setNotification(Notifications notification) {
        this.notification = notification;
    }

    public Users getUsers() {
        return users;
    }

    public void setUsers(Users users) {
        this.users = users;
    }

    public Assets getAssets() {
        return assets;
    }

    public void setAssets(Assets assets) {
        this.assets = assets;
    }
}
