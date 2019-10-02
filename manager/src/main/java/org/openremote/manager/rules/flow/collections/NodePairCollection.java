package org.openremote.manager.rules.flow.collections;

import org.openremote.manager.rules.flow.NodePair;
import org.openremote.manager.rules.flow.NodeStorageService;

import java.util.List;

public interface NodePairCollection {
    List<NodePair> generate(NodeStorageService storageService);
}
