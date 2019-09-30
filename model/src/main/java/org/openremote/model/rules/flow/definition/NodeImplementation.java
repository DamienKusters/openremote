package org.openremote.model.rules.flow.definition;

import org.openremote.model.rules.flow.NodeExecutionRequestInfo;

import java.util.Optional;

public interface NodeImplementation<T> {
    Optional<T> execute(NodeExecutionRequestInfo info);
}
