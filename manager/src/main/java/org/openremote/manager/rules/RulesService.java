/*
 * Copyright 2017, OpenRemote Inc.
 *
 * See the CONTRIBUTORS.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openremote.manager.rules;

import org.apache.camel.builder.RouteBuilder;
import org.openremote.container.Container;
import org.openremote.container.ContainerService;
import org.openremote.container.message.MessageBrokerSetupService;
import org.openremote.container.persistence.PersistenceEvent;
import org.openremote.container.persistence.PersistenceService;
import org.openremote.container.timer.TimerService;
import org.openremote.manager.asset.AssetProcessingException;
import org.openremote.manager.asset.AssetProcessingService;
import org.openremote.manager.asset.AssetStorageService;
import org.openremote.manager.asset.AssetUpdateProcessor;
import org.openremote.manager.concurrent.ManagerExecutorService;
import org.openremote.manager.event.ClientEventService;
import org.openremote.manager.notification.NotificationService;
import org.openremote.manager.rules.flow.NodeStorageService;
import org.openremote.manager.rules.geofence.GeofenceAssetAdapter;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.model.Constants;
import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetAttribute;
import org.openremote.model.attribute.MetaItemType;
import org.openremote.model.attribute.AttributeEvent.Source;
import org.openremote.model.query.AssetQuery;
import org.openremote.model.query.filter.AttributeMetaPredicate;
import org.openremote.model.query.filter.BooleanPredicate;
import org.openremote.model.query.filter.LocationAttributePredicate;
import org.openremote.model.rules.*;
import org.openremote.model.rules.geofence.GeofenceDefinition;
import org.openremote.model.security.ClientRole;
import org.openremote.model.security.Tenant;
import org.openremote.model.util.Pair;
import org.openremote.model.value.ObjectValue;

import javax.persistence.EntityManager;
import java.util.*;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.SEVERE;
import static org.openremote.container.concurrent.GlobalLock.withLock;
import static org.openremote.container.concurrent.GlobalLock.withLockReturning;
import static org.openremote.container.persistence.PersistenceEvent.PERSISTENCE_TOPIC;
import static org.openremote.container.persistence.PersistenceEvent.isPersistenceEventForEntityType;
import static org.openremote.container.util.MapAccess.getString;
import static org.openremote.model.AbstractValueTimestampHolder.VALUE_TIMESTAMP_FIELD_NAME;
import static org.openremote.model.asset.AssetAttribute.attributesFromJson;
import static org.openremote.model.asset.AssetAttribute.getAddedOrModifiedAttributes;

/**
 * Manages {@link RulesEngine}s for stored {@link Ruleset}s and processes asset attribute updates.
 * <p>
 * If an updated attribute {@link AssetAttribute#isRuleState}, this implementation of {@link AssetUpdateProcessor}
 * converts the update message to an {@link AssetState} fact. This service keeps the facts and thus the state of rule
 * facts are in sync with the asset state changes that occur. If an asset attribute value changes, the {@link
 * AssetState} in the rules engines will be updated to reflect the change.
 * <p>
 * If an updated attribute {@link AssetAttribute#isRuleEvent}, another temporary {@link AssetState} fact is inserted in
 * the rules engines in scope. This fact expires automatically if the lifetime set in {@link
 * RulesService#RULE_EVENT_EXPIRES} is reached, or if the lifetime set in the attribute {@link
 * MetaItemType#RULE_EVENT_EXPIRES} is reached.
 * <p>
 * Each asset attribute update is processed in the following order:
 * <ol>
 * <li>Global Rulesets</li>
 * <li>Tenant Rulesets</li>
 * <li>Asset Rulesets (in hierarchical order from oldest ancestor down)</li>
 * </ol>
 * Processing order of rulesets with the same scope or same parent is not guaranteed.
 */
public class RulesService extends RouteBuilder implements ContainerService, AssetUpdateProcessor {

    public static final String RULE_EVENT_EXPIRES = "RULE_EVENT_EXPIRES";
    public static final String RULE_EVENT_EXPIRES_DEFAULT = "1h";
    private static final Logger LOG = Logger.getLogger(RulesService.class.getName());

    protected List<GeofenceAssetAdapter> geofenceAssetAdapters = new ArrayList<>();
    protected final Map<String, RulesEngine<TenantRuleset>> tenantEngines = new HashMap<>();
    protected final Map<String, RulesEngine<AssetRuleset>> assetEngines = new HashMap<>();
    protected TimerService timerService;
    protected ManagerExecutorService executorService;
    protected PersistenceService persistenceService;
    protected RulesetStorageService rulesetStorageService;
    protected ManagerIdentityService identityService;
    protected AssetStorageService assetStorageService;
    protected NotificationService notificationService;
    protected AssetProcessingService assetProcessingService;
    protected ClientEventService clientEventService;
    protected NodeStorageService nodeStorageService;
    protected RulesEngine<GlobalRuleset> globalEngine;
    protected Tenant[] tenants;
    protected AssetLocationPredicateProcessor locationPredicateRulesConsumer;
    protected Map<RulesEngine, List<RulesEngine.AssetStateLocationPredicates>> engineAssetLocationPredicateMap = new HashMap<>();
    protected Set<String> assetsWithModifiedLocationPredicates = new HashSet<>();
    // Keep global list of asset states that have been pushed to any engines
    // The objects are already in memory inside the rule engines but keeping them
    // here means we can quickly insert facts into newly started engines
    protected Set<AssetState> assetStates = new HashSet<>();
    protected String configEventExpires;

    @Override
    public int getPriority() {
        return ContainerService.DEFAULT_PRIORITY;
    }

    @Override
    public void init(Container container) throws Exception {
        timerService = container.getService(TimerService.class);
        executorService = container.getService(ManagerExecutorService.class);
        persistenceService = container.getService(PersistenceService.class);
        rulesetStorageService = container.getService(RulesetStorageService.class);
        identityService = container.getService(ManagerIdentityService.class);
        notificationService = container.getService(NotificationService.class);
        assetStorageService = container.getService(AssetStorageService.class);
        assetProcessingService = container.getService(AssetProcessingService.class);
        clientEventService = container.getService(ClientEventService.class);
        nodeStorageService = container.getService(NodeStorageService.class);

        clientEventService.addSubscriptionAuthorizer((auth, subscription) -> {

            if (subscription.isEventType(RulesEngineStatusEvent.class) || subscription.isEventType(RulesetChangedEvent.class)) {

                if (auth.isSuperUser()) {
                    return true;
                }

                // Regular user must have role
                if (!auth.hasResourceRole(ClientRole.READ_ASSETS.getValue(), Constants.KEYCLOAK_CLIENT_ID)) {
                    return false;
                }

                boolean isRestrictedUser = identityService.getIdentityProvider().isRestrictedUser(auth.getUserId());

                return !isRestrictedUser;
            }

            return false;
        });

        ServiceLoader.load(GeofenceAssetAdapter.class).forEach(geofenceAssetAdapter -> {
            LOG.fine("Adding GeofenceAssetAdapter: " + geofenceAssetAdapter.getClass().getName());
            geofenceAssetAdapters.add(geofenceAssetAdapter);
        });

        geofenceAssetAdapters.addAll(container.getServices(GeofenceAssetAdapter.class));
        geofenceAssetAdapters.sort(Comparator.comparingInt(GeofenceAssetAdapter::getPriority));
        container.getService(MessageBrokerSetupService.class).getContext().addRoutes(this);
        configEventExpires = getString(container.getConfig(), RULE_EVENT_EXPIRES, RULE_EVENT_EXPIRES_DEFAULT);
    }

    @Override
    public void configure() throws Exception {
        // If any ruleset was modified in the database then check its' status and undeploy, deploy, or update it
        from(PERSISTENCE_TOPIC)
            .routeId("RulesetPersistenceChanges")
            .filter(isPersistenceEventForEntityType(Ruleset.class))
            .process(exchange -> {
                PersistenceEvent persistenceEvent = exchange.getIn().getBody(PersistenceEvent.class);
                processRulesetChange((Ruleset) persistenceEvent.getEntity(), persistenceEvent.getCause());
            });

        // If any tenant was modified in the database then check its' status and undeploy, deploy or update any
        // associated rulesets
        from(PERSISTENCE_TOPIC)
            .routeId("RuleEngineTenantChanges")
            .filter(isPersistenceEventForEntityType(Tenant.class))
            .process(exchange -> {
                PersistenceEvent persistenceEvent = exchange.getIn().getBody(PersistenceEvent.class);
                Tenant tenant = (Tenant) persistenceEvent.getEntity();
                processTenantChange(tenant, persistenceEvent.getCause());
            });

        // If any asset was modified in the database, detect changed attributes
        from(PERSISTENCE_TOPIC)
            .routeId("RuleEngineAssetChanges")
            .filter(isPersistenceEventForEntityType(Asset.class))
            .process(exchange -> {
                PersistenceEvent persistenceEvent = exchange.getIn().getBody(PersistenceEvent.class);
                final Asset eventAsset = (Asset) persistenceEvent.getEntity();
                processAssetChange(eventAsset, persistenceEvent);
            });
    }

    @Override
    public void start(Container container) throws Exception {

        if (!geofenceAssetAdapters.isEmpty()) {
            LOG.info("GeoefenceAssetAdapters found: " + geofenceAssetAdapters.size());
            locationPredicateRulesConsumer = this::onEngineLocationRulesChanged;

            for (GeofenceAssetAdapter geofenceAssetAdapter : geofenceAssetAdapters) {
                geofenceAssetAdapter.start(container);
            }
        }

        LOG.info("Deploying global rulesets");
        rulesetStorageService.findGlobalRulesets(true, null, true).forEach(this::deployGlobalRuleset);

        LOG.info("Deploying tenant rulesets");
        tenants = identityService.getIdentityProvider().getTenants();
        rulesetStorageService.findTenantRulesets(false, true, null, true)
            .stream()
            .filter(rd ->
                Arrays.stream(tenants)
                    .anyMatch(tenant -> rd.getRealm().equals(tenant.getRealm()))
            ).forEach(this::deployTenantRuleset);

        LOG.info("Deploying asset rulesets");
        // Group by asset ID then tenant and check tenant is enabled
        deployAssetRulesets(rulesetStorageService.findAssetRulesets(null, null, false, true, null, true));

        LOG.info("Loading all assets with fact attributes to initialize state of rules engines");
        Stream<Pair<Asset, Stream<AssetAttribute>>> assetRuleAttributes = findRuleStateAttributes();

        // Push each rule attribute as an asset update through the rule engine chain
        // that will ensure the insert only happens to the engines in scope
        assetRuleAttributes
            .forEach(pair -> {
                Asset asset = pair.key;
                pair.value.forEach(ruleAttribute -> {
                    AssetState assetState = new AssetState(asset, ruleAttribute, Source.INTERNAL);
                    updateAssetState(assetState, true, true);
                });
            });
    }

    @Override
    public void stop(Container container) throws Exception {
        withLock(getClass().getSimpleName() + "::stop", () -> {
            for (GeofenceAssetAdapter geofenceAssetAdapter : geofenceAssetAdapters) {
                try {
                    geofenceAssetAdapter.stop(container);
                } catch (Exception e) {
                    LOG.log(SEVERE, "Exception thrown whilst stopping geofence adapter", e);
                }
            }

            // TODO: Do rule engines need to be stopped on shutdown
            assetEngines.forEach((assetId, rulesEngine) -> rulesEngine.stop(true));
            assetEngines.clear();
            tenantEngines.forEach((realm, rulesEngine) -> rulesEngine.stop(true));
            tenantEngines.clear();

            if (globalEngine != null) {
                globalEngine.stop(true);
                globalEngine = null;
            }
        });
    }

    @Override
    public boolean processAssetUpdate(EntityManager em,
                                      Asset asset,
                                      AssetAttribute attribute,
                                      Source source) throws AssetProcessingException {
        // We might process two facts for a single attribute update, if that is what the user wants

        // First as asset state
        if (attribute.isRuleState()) {
            updateAssetState(
                new AssetState(asset, attribute, source),
                false, // Don't skip the error check on the rules engines
                !attribute.isRuleEvent() // If it's not a rule event, fire immediately
            );
        }

        // Then as asset event (if there wasn't an error), this will also fire the rules engines
        if (attribute.isRuleEvent()) {
            insertAssetEvent(
                new AssetState(asset, attribute, source),
                attribute.getRuleEventExpires().orElse(configEventExpires)
            );
        }

        return false;
    }

    public GeofenceDefinition[] getAssetGeofences(String assetId) {
        return withLockReturning(getClass().getSimpleName() + "::getAssetGeofences", () -> {

            LOG.finest("Requesting geofences for asset: " + assetId);

            for (GeofenceAssetAdapter geofenceAdapter : geofenceAssetAdapters) {
                GeofenceDefinition[] geofences = geofenceAdapter.getAssetGeofences(assetId);
                if (geofences != null) {
                    LOG.finest("Retrieved geofences from geofence adapter '" + geofenceAdapter.getName() + "' for asset: " + assetId);
                    return geofences;
                }
            }

            return new GeofenceDefinition[0];
        });
    }

    protected void processTenantChange(Tenant tenant, PersistenceEvent.Cause cause) {
        withLock(getClass().getSimpleName() + "::processTenantChange", () -> {
            // Check if enabled status has changed
            boolean wasEnabled = Arrays.stream(tenants).anyMatch(t -> tenant.getRealm().equals(t.getRealm()));
            boolean isEnabled = tenant.getEnabled() && cause != PersistenceEvent.Cause.DELETE;
            tenants = identityService.getIdentityProvider().getTenants();

            if (wasEnabled == isEnabled) {
                // Nothing to do here
                return;
            }

            if (wasEnabled) {
                // Remove tenant rules engine for this tenant if it exists
                RulesEngine<TenantRuleset> tenantRulesEngine = tenantEngines.get(tenant.getRealm());
                if (tenantRulesEngine != null) {
                    tenantRulesEngine.stop();
                    tenantEngines.remove(tenant.getRealm());
                }

                // Remove any asset rules engines for assets in this realm
                assetEngines.values().stream()
                    .filter(re -> re.getId().getRealm().map(realm -> realm.equals(tenant.getRealm())).orElse(false))
                    .forEach(RulesEngine::stop);
                assetEngines.entrySet().removeIf(entry ->
                    entry.getValue().getId().getRealm().map(realm -> realm.equals(tenant.getRealm())).orElse(
                        false)
                );

            } else {
                // Create tenant rules engines for this tenant if it has any rulesets
                rulesetStorageService
                    .findTenantRulesets(tenant.getRealm(), false, true, null, true)
                    .forEach(this::deployTenantRuleset);

                // Create any asset rules engines for assets in this realm that have rulesets
                deployAssetRulesets(rulesetStorageService.findAssetRulesetsByRealm(tenant.getRealm(), false, true, null, true));
            }
        });
    }

    protected void processAssetChange(Asset asset, PersistenceEvent persistenceEvent) {
        withLock(getClass().getSimpleName() + "::processAssetChange", () -> {

            // We must load the asset from database (only when required), as the
            // persistence event might not contain a completely loaded asset
            BiFunction<Asset, AssetAttribute, AssetState> buildAssetState = (loadedAsset, attribute) ->
                new AssetState(loadedAsset, attribute.deepCopy(), Source.INTERNAL);

            switch (persistenceEvent.getCause()) {
                case CREATE: {
                    // New asset has been created so get attributes that have RULE_STATE meta
                    List<AssetAttribute> ruleStateAttributes =
                        asset.getAttributesStream().filter(AssetAttribute::isRuleState).collect(Collectors.toList());

                    // Asset used to be loaded for each attribute which is inefficient
                    Asset loadedAsset = ruleStateAttributes.isEmpty() ? null : assetStorageService.find(asset.getId(),
                        true);

                    // Build an update with a fully loaded asset
                    ruleStateAttributes.forEach(attribute -> {

                        // If the asset is now gone it was deleted immediately after being inserted, nothing more to do
                        if (loadedAsset == null)
                            return;

                        AssetState assetState = buildAssetState.apply(loadedAsset, attribute);
                        LOG.fine("Asset was persisted (" + persistenceEvent.getCause() + "), inserting fact: " + assetState);
                        updateAssetState(assetState, true, true);
                    });
                    break;
                }
                case UPDATE: {
                    int attributesIndex = Arrays.asList(persistenceEvent.getPropertyNames()).indexOf("attributes");
                    if (attributesIndex < 0) {
                        return;
                    }

                    // Fully load the asset
                    Asset loadedAsset = assetStorageService.find(asset.getId(), true);
                    // If the asset is now gone it was deleted immediately after being updated, nothing more to do
                    if (loadedAsset == null)
                        return;

                    // Attributes have possibly changed so need to compare old and new attributes
                    // to determine which facts to retract and which to insert
                    List<AssetAttribute> oldRuleStateAttributes =
                        attributesFromJson(
                            (ObjectValue) persistenceEvent.getPreviousState()[attributesIndex],
                            asset.getId()
                        ).filter(AssetAttribute::isRuleState).collect(Collectors.toList());

                    List<AssetAttribute> newRuleStateAttributes =
                        attributesFromJson(
                            (ObjectValue) persistenceEvent.getCurrentState()[attributesIndex],
                            asset.getId()
                        ).filter(AssetAttribute::isRuleState).collect(Collectors.toList());

                    // Retract facts for attributes that are obsolete
                    getAddedOrModifiedAttributes(newRuleStateAttributes,
                        oldRuleStateAttributes,
                        key -> key.equals(VALUE_TIMESTAMP_FIELD_NAME))
                        .forEach(obsoleteFactAttribute -> {
                            AssetState update = buildAssetState.apply(loadedAsset, obsoleteFactAttribute);
                            LOG.fine("Asset was persisted (" + persistenceEvent.getCause() + "), retracting: " + update);
                            retractAssetState(update);
                        });

                    // Insert facts for attributes that are new
                    getAddedOrModifiedAttributes(oldRuleStateAttributes,
                        newRuleStateAttributes,
                        key -> key.equals(VALUE_TIMESTAMP_FIELD_NAME))
                        .forEach(newFactAttribute -> {
                            AssetState assetState = buildAssetState.apply(loadedAsset, newFactAttribute);
                            LOG.fine("Asset was persisted (" + persistenceEvent.getCause() + "), updating: " + assetState);
                            updateAssetState(assetState, true, true);
                        });
                    break;
                }
                case DELETE:
                    // Retract any facts that were associated with this asset
                    asset.getAttributesStream()
                        .filter(AssetAttribute::isRuleState)
                        .forEach(attribute -> {
                            // We can't load the asset again (it was deleted), so don't use buildAssetState() and
                            // hope that the path of the event asset has been loaded before deletion, although it is
                            // "unlikely" anybody will access it during retraction...
                            AssetState assetState = new AssetState(asset, attribute, Source.INTERNAL);
                            LOG.fine("Asset was persisted (" + persistenceEvent.getCause() + "), retracting fact: " + assetState);
                            retractAssetState(assetState);
                        });
                    break;
            }
        });
    }

    protected void processRulesetChange(Ruleset ruleset, PersistenceEvent.Cause cause) {
        withLock(getClass().getSimpleName() + "::processRulesetChange", () -> {
            if (cause == PersistenceEvent.Cause.DELETE || !ruleset.isEnabled()) {
                if (ruleset instanceof GlobalRuleset) {
                    undeployGlobalRuleset((GlobalRuleset) ruleset);
                } else if (ruleset instanceof TenantRuleset) {
                    undeployTenantRuleset((TenantRuleset) ruleset);
                } else if (ruleset instanceof AssetRuleset) {
                    undeployAssetRuleset((AssetRuleset) ruleset);
                }
            } else {
                if (ruleset instanceof GlobalRuleset) {

                    RulesEngine newEngine = deployGlobalRuleset((GlobalRuleset) ruleset);
                    if (newEngine != null) {
                        // Push all existing facts into the engine, this is an initial import of state so fire delayed
                        assetStates.forEach(assetState -> newEngine.updateFact(assetState, false));
                        newEngine.fire();
                    }

                } else if (ruleset instanceof TenantRuleset) {

                    RulesEngine newEngine = deployTenantRuleset((TenantRuleset) ruleset);
                    if (newEngine != null) {
                        // Push all existing facts into the engine, this is an initial import of state so fire delayed
                        assetStates.forEach(assetState -> {
                            if (assetState.getRealm().equals(((TenantRuleset) ruleset).getRealm())) {
                                newEngine.updateFact(assetState, false);
                            }
                        });
                        newEngine.fire();
                    }

                } else if (ruleset instanceof AssetRuleset) {

                    // Must reload from the database, the ruleset might not be completely hydrated on CREATE or UPDATE
                    AssetRuleset assetRuleset = rulesetStorageService.findById(AssetRuleset.class, ruleset.getId());
                    RulesEngine newEngine = deployAssetRuleset(assetRuleset);
                    if (newEngine != null) {
                        // Push all existing facts for this asset (and it's children into the engine), this is an
                        // initial import of state so fire delayed
                        getAssetStatesInScope(((AssetRuleset) ruleset).getAssetId())
                            .forEach(assetState -> newEngine.updateFact(assetState, false));
                        newEngine.fire();
                    }
                }
            }
        });
    }

    /**
     * Deploy the ruleset into the global engine creating the engine if necessary; if the engine was created then it is
     * returned from the method.
     */
    protected RulesEngine<GlobalRuleset> deployGlobalRuleset(GlobalRuleset ruleset) {
        return withLockReturning(getClass().getSimpleName() + "::deployGlobalRuleset", () -> {
            boolean created = globalEngine == null;

            // Global rules have access to everything in the system
            if (globalEngine == null) {
                globalEngine = new RulesEngine<>(
                    timerService,
                    identityService,
                    executorService,
                    assetStorageService,
                    assetProcessingService,
                    notificationService,
                    clientEventService,
                    nodeStorageService,
                    new RulesEngineId<>(),
                    locationPredicateRulesConsumer
                );
            }

            globalEngine.addRuleset(ruleset);
            return created ? globalEngine : null;
        });
    }

    protected void undeployGlobalRuleset(GlobalRuleset ruleset) {
        withLock(getClass().getSimpleName() + "::undeployGlobalRuleset", () -> {
            if (globalEngine == null) {
                return;
            }

            if (globalEngine.removeRuleset(ruleset)) {
                globalEngine = null;
            }
        });
    }

    protected RulesEngine<TenantRuleset> deployTenantRuleset(TenantRuleset ruleset) {
        return withLockReturning(getClass().getSimpleName() + "::deployTenantRuleset", () -> {
            final boolean[] created = {false};

            // Look for existing rules engines for this tenant
            RulesEngine<TenantRuleset> tenentRulesEngine = tenantEngines
                .computeIfAbsent(ruleset.getRealm(), (realm) -> {
                    created[0] = true;
                    return new RulesEngine<>(
                        timerService,
                        identityService,
                        executorService,
                        assetStorageService,
                        assetProcessingService,
                        notificationService,
                        clientEventService,
                        nodeStorageService,
                        new RulesEngineId<>(realm),
                        locationPredicateRulesConsumer
                    );
                });

            tenentRulesEngine.addRuleset(ruleset);

            return created[0] ? tenentRulesEngine : null;
        });
    }

    protected void undeployTenantRuleset(TenantRuleset ruleset) {
        withLock(getClass().getSimpleName() + "::undeployTenantRuleset", () -> {
            RulesEngine<TenantRuleset> rulesEngine = tenantEngines.get(ruleset.getRealm());
            if (rulesEngine == null) {
                return;
            }

            if (rulesEngine.removeRuleset(ruleset)) {
                tenantEngines.remove(ruleset.getRealm());
            }
        });
    }

    protected void deployAssetRulesets(List<AssetRuleset> rulesets) {
        rulesets
            .stream()
            .collect(Collectors.groupingBy(AssetRuleset::getAssetId))
            .entrySet()
            .stream()
            .map(es ->
                new Pair<>(assetStorageService.find(es.getKey(), true), es.getValue())
            )
            .filter(assetAndRules -> assetAndRules.key != null)
            .collect(Collectors.groupingBy(assetAndRules -> assetAndRules.key.getRealm()))
            .entrySet()
            .stream()
            .filter(es -> Arrays
                .stream(tenants)
                .anyMatch(at -> es.getKey().equals(at.getRealm())))
            .forEach(es -> {
                List<Pair<Asset, List<AssetRuleset>>> tenantAssetAndRules = es.getValue();

                // RT: Not sure we need ordering here for starting engines so removing it
                // Order rulesets by asset hierarchy within this tenant
                tenantAssetAndRules.stream()
                    //.sorted(Comparator.comparingInt(item -> item.key.getPath().length))
                    .flatMap(assetAndRules -> assetAndRules.value.stream())
                    .forEach(this::deployAssetRuleset);
            });
    }

    protected RulesEngine<AssetRuleset> deployAssetRuleset(AssetRuleset ruleset) {
        return withLockReturning(getClass().getSimpleName() + "::deployAssetRuleset", () -> {
            final boolean[] created = {false};

            // Look for existing rules engine for this asset
            RulesEngine<AssetRuleset> assetRulesEngine = assetEngines
                .computeIfAbsent(ruleset.getAssetId(), (assetId) -> {
                    created[0] = true;
                    return new RulesEngine<>(
                        timerService,
                        identityService,
                        executorService,
                        assetStorageService,
                        assetProcessingService,
                        notificationService,
                        clientEventService,
                        nodeStorageService,
                        new RulesEngineId<>(ruleset.getRealm(), assetId),
                        locationPredicateRulesConsumer
                    );
                });

            assetRulesEngine.addRuleset(ruleset);
            return created[0] ? assetRulesEngine : null;
        });
    }

    protected void undeployAssetRuleset(AssetRuleset ruleset) {
        withLock(getClass().getSimpleName() + "::undeployAssetRuleset", () -> {
            RulesEngine<AssetRuleset> assetRulesEngine = assetEngines.get(ruleset.getAssetId());
            if (assetRulesEngine == null) {
                return;
            }

            if (assetRulesEngine.removeRuleset(ruleset)) {
                assetEngines.remove(ruleset.getAssetId());
            }
        });
    }

    protected void insertAssetEvent(AssetState assetState, String expires) {
        withLock(getClass().getSimpleName() + "::insertAssetEvent", () -> {
            // Get the chain of rule engines that we need to pass through
            List<RulesEngine> rulesEngines = getEnginesInScope(assetState.getRealm(), assetState.getPath());

            // Check that all engines in the scope are available
            if (rulesEngines.stream().anyMatch(RulesEngine::isError)) {
                LOG.severe("At least one rules engine is in an error state, skipping: " + assetState);
                if (LOG.isLoggable(FINEST)) {
                    for (RulesEngine rulesEngine : rulesEngines) {
                        if (rulesEngine.isError()) {
                            LOG.log(FINEST, "Rules engine error state: " + rulesEngine, rulesEngine.getError());
                        }
                    }
                }
                return;
            }

            // Pass through each engine
            for (RulesEngine rulesEngine : rulesEngines) {
                rulesEngine.insertFact(expires, assetState);
            }
        });
    }

    protected void updateAssetState(AssetState assetState, boolean skipStatusCheck, boolean fireImmediately) {
        withLock(getClass().getSimpleName() + "::updateAssetState", () -> {
            // TODO: implement rules processing error state handling

            LOG.fine("Updating asset state: " + assetState);

            // Get the chain of rule engines that we need to pass through
            List<RulesEngine> rulesEngines = getEnginesInScope(assetState.getRealm(), assetState.getPath());

            if (!skipStatusCheck) {
                // Check that all engines in the scope are available
                // TODO This is not very useful without locking the engines until we are done with the update
                for (RulesEngine rulesEngine : rulesEngines) {
                    if (rulesEngine.isError()) {
                        LOG.severe("Cannot update asset state as one or more rule engines in scope are in an error state");
                        throw rulesEngine.getError();
                    }
                }
            }

            // Remove asset state with same attribute ref as new state, add new state
            assetStates.remove(assetState);
            assetStates.add(assetState);

            // Pass through each rules engine
            for (RulesEngine rulesEngine : rulesEngines) {
                rulesEngine.updateFact(assetState, fireImmediately);
            }
        });
    }

    protected void retractAssetState(AssetState assetState) {
        // Get the chain of rule engines that we need to pass through
        List<RulesEngine> rulesEngines = getEnginesInScope(assetState.getRealm(), assetState.getPath());

        // Remove asset state with same attribute ref
        assetStates.remove(assetState);

        if (rulesEngines.size() == 0) {
            LOG.fine("Ignoring as there are no matching rules engines: " + assetState);
        }

        // Pass through each rules engine
        for (RulesEngine rulesEngine : rulesEngines) {
            rulesEngine.removeFact(assetState);
        }
    }

    protected List<AssetState> getAssetStatesInScope(String assetId) {
        return assetStates
            .stream()
            .filter(assetState -> Arrays.asList(assetState.getPath()).contains(assetId))
            .collect(Collectors.toList());
    }

    protected List<RulesEngine> getEnginesInScope(String realm, String[] assetPath) {
        List<RulesEngine> rulesEngines = new ArrayList<>();

        // Add global engine (if it exists)
        if (globalEngine != null) {
            rulesEngines.add(globalEngine);
        }

        // Add tenant engine (if it exists)
        RulesEngine tenantRulesEngine = tenantEngines.get(realm);

        if (tenantRulesEngine != null) {
            rulesEngines.add(tenantRulesEngine);
        }

        // Add asset engines, iterate through asset hierarchy using asset IDs from asset path
        for (String assetId : assetPath) {
            RulesEngine assetRulesEngine = assetEngines.get(assetId);
            if (assetRulesEngine != null) {
                rulesEngines.add(assetRulesEngine);
            }
        }

        return rulesEngines;
    }

    protected Stream<Pair<Asset, Stream<AssetAttribute>>> findRuleStateAttributes() {
        List<Asset> assets = assetStorageService.findAll(
            new AssetQuery()
                .select(AssetQuery.Select.selectAll())
                .attributeMeta(
                    new AttributeMetaPredicate(
                        MetaItemType.RULE_STATE,
                        new BooleanPredicate(true))
                ));

        return assets.stream()
            .map((Asset asset) ->
                new Pair<>(asset, asset.getAttributesStream().filter(AssetAttribute::isRuleState))
            );
    }

    /**
     * Called when an engine's rules change identifying assets with location attributes marked with {@link
     * MetaItemType#RULE_STATE} that also have {@link LocationAttributePredicate} in the rules. The job here is to
     * identify the asset's (via {@link AssetState}) that have modified {@link LocationAttributePredicate}s and to
     * notify the {@link GeofenceAssetAdapter}s.
     */
    protected void onEngineLocationRulesChanged(RulesEngine rulesEngine, List<RulesEngine.AssetStateLocationPredicates> newEngineAssetStateLocationPredicates) {
        withLock(getClass().getSimpleName() + "::onEngineLocationRulesChanged", () -> {
            int initialModifiedCount = assetsWithModifiedLocationPredicates.size();

            if (newEngineAssetStateLocationPredicates == null) {
                engineAssetLocationPredicateMap.computeIfPresent(rulesEngine,
                    (re, existingAssetStateLocationPredicates) -> {
                        // All location predicates have been removed so record each asset state as modified
                        assetsWithModifiedLocationPredicates.addAll(
                            existingAssetStateLocationPredicates.stream().map(
                                RulesEngine.AssetStateLocationPredicates::getAssetId).collect(
                                Collectors.toList()));
                        // Remove this engine from the map
                        return null;
                    });
            } else {
                engineAssetLocationPredicateMap.compute(rulesEngine,
                    (re, existingEngineAssetStateLocationPredicates) -> {
                        // Check if this not the first time this engine has been seen with location predicates so we can check
                        // for any removed asset states
                        if (existingEngineAssetStateLocationPredicates == null) {
                            // All asset states are new so record them all as modified
                            assetsWithModifiedLocationPredicates.addAll(
                                newEngineAssetStateLocationPredicates.stream().map(
                                    RulesEngine.AssetStateLocationPredicates::getAssetId).collect(
                                    Collectors.toList()));
                        } else {
                            // Find obsolete and modified asset states
                            existingEngineAssetStateLocationPredicates.forEach(
                                existingAssetStateLocationPredicates -> {
                                    // Check if there are no longer any location predicates for this asset
                                    Optional<RulesEngine.AssetStateLocationPredicates> newAssetStateLocationPredicates = newEngineAssetStateLocationPredicates.stream()
                                        .filter(assetStateLocationPredicates ->
                                            assetStateLocationPredicates.getAssetId().equals(
                                                existingAssetStateLocationPredicates.getAssetId()))
                                        .findFirst();

                                    if (newAssetStateLocationPredicates.isPresent()) {
                                        // Compare existing and new location predicate sets if there is any change then record it
                                        if (!newAssetStateLocationPredicates.get().getLocationPredicates().equals(
                                            existingAssetStateLocationPredicates.getLocationPredicates())) {
                                            assetsWithModifiedLocationPredicates.add(
                                                existingAssetStateLocationPredicates.getAssetId());
                                        }
                                    } else {
                                        // This means that there are no longer any location predicates so old ones are obsolete
                                        assetsWithModifiedLocationPredicates.add(
                                            existingAssetStateLocationPredicates.getAssetId());
                                    }
                                });

                            // Check for asset states in the new map but not in the old one
                            newEngineAssetStateLocationPredicates.forEach(
                                newAssetStateLocationPredicates -> {
                                    boolean isNewAssetState = existingEngineAssetStateLocationPredicates.stream()
                                        .noneMatch(assetStateLocationPredicates ->
                                            assetStateLocationPredicates.getAssetId().equals(
                                                newAssetStateLocationPredicates.getAssetId()));

                                    if (isNewAssetState) {
                                        // This means that all predicates for this asset are new
                                        assetsWithModifiedLocationPredicates.add(
                                            newAssetStateLocationPredicates.getAssetId());
                                    }
                                });
                        }
                        return newEngineAssetStateLocationPredicates;
                    });
            }

            if (assetsWithModifiedLocationPredicates.size() != initialModifiedCount) {
                processModifiedGeofences();
            }
        });
    }

    protected void processModifiedGeofences() {
        withLock(getClass().getSimpleName() + "::processModifiedGeofences", () -> {
            LOG.finest("Processing geofence modifications: modified asset geofence count=" + assetsWithModifiedLocationPredicates.size());

            try {
                // Find all location predicates associated with modified assets and pass through to the geofence adapters
                List<RulesEngine.AssetStateLocationPredicates> assetLocationPredicates = new ArrayList<>(
                    assetsWithModifiedLocationPredicates.size());

                assetsWithModifiedLocationPredicates.forEach(assetId -> {

                    RulesEngine.AssetStateLocationPredicates locationPredicates = new RulesEngine.AssetStateLocationPredicates(
                        assetId,
                        new HashSet<>());

                    engineAssetLocationPredicateMap.forEach((rulesEngine, engineAssetStateLocationPredicates) ->
                        engineAssetStateLocationPredicates.stream().filter(
                            assetStateLocationPredicates ->
                                assetStateLocationPredicates.getAssetId().equals(
                                    assetId))
                            .findFirst()
                            .ifPresent(
                                assetStateLocationPredicate -> {
                                    locationPredicates.getLocationPredicates().addAll(
                                        assetStateLocationPredicate.getLocationPredicates());
                                }));

                    assetLocationPredicates.add(locationPredicates);
                });

                for (GeofenceAssetAdapter geofenceAssetAdapter : geofenceAssetAdapters) {
                    LOG.finest("Passing modified geofences to adapter: " + geofenceAssetAdapter.getName());
                    geofenceAssetAdapter.processLocationPredicates(assetLocationPredicates);

                    if (assetLocationPredicates.isEmpty()) {
                        LOG.finest("All modified geofences handled");
                        break;
                    }
                }

            } catch (Exception e) {
                LOG.log(SEVERE, "Exception thrown by geofence adapter whilst processing location predicates", e);
            } finally {
                // Clear modified assets ready for next batch
                assetsWithModifiedLocationPredicates.clear();
            }
        });
    }

    protected Optional<RulesetDeployment> getRulesetDeployment(Long rulesetId) {
        if (globalEngine != null) {
            if (globalEngine.deployments.containsKey(rulesetId)) {
                return Optional.of(globalEngine.deployments.get(rulesetId));
            }
        }

        for (Map.Entry<String, RulesEngine<TenantRuleset>> realmAndEngine : tenantEngines.entrySet()) {
            if (realmAndEngine.getValue().deployments.containsKey(rulesetId)) {
                return Optional.of(realmAndEngine.getValue().deployments.get(rulesetId));
            }
        }

        for (Map.Entry<String, RulesEngine<AssetRuleset>> realmAndEngine : assetEngines.entrySet()) {
            if (realmAndEngine.getValue().deployments.containsKey(rulesetId)) {
                return Optional.of(realmAndEngine.getValue().deployments.get(rulesetId));
            }
        }

        return Optional.empty();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{}";
    }
}