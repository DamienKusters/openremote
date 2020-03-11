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
package org.openremote.app.client.rules.tenant;

import org.openremote.app.client.Environment;
import org.openremote.app.client.TenantMapper;
import org.openremote.app.client.assets.browser.AssetBrowser;
import org.openremote.app.client.mvp.AcceptsView;
import org.openremote.app.client.mvp.AppActivity;
import org.openremote.app.client.rest.EntityReader;
import org.openremote.app.client.rest.EntityWriter;
import org.openremote.app.client.rules.AbstractRulesEditorActivity;
import org.openremote.app.client.rules.RulesEditor;
import org.openremote.model.event.bus.EventBus;
import org.openremote.model.event.bus.EventRegistration;
import org.openremote.model.http.RequestParams;
import org.openremote.model.interop.Consumer;
import org.openremote.model.rules.RulesResource;
import org.openremote.model.rules.TenantRuleset;
import org.openremote.model.security.TenantResource;

import javax.inject.Inject;
import java.util.Collection;

public class TenantRulesEditorActivity
    extends AbstractRulesEditorActivity<TenantRuleset, TenantRulesEditorPlace> {

    final TenantMapper tenantMapper;
    final TenantResource tenantResource;
    final TenantRulesetMapper tenantRulesetMapper;

    String realm;

    @Inject
    public TenantRulesEditorActivity(Environment environment,
                                     AssetBrowser.Presenter assetBrowserPresenter,
                                     RulesEditor view,
                                     RulesResource rulesetResource,
                                     TenantMapper tenantMapper,
                                     TenantResource tenantResource,
                                     TenantRulesetMapper tenantRulesetMapper) {
        super(environment, assetBrowserPresenter, view, rulesetResource);
        this.tenantMapper = tenantMapper;
        this.tenantResource = tenantResource;
        this.tenantRulesetMapper = tenantRulesetMapper;
    }

    @Override
    protected AppActivity<TenantRulesEditorPlace> init(TenantRulesEditorPlace place) {
        realm = place.getRealm();
        return super.init(place);
    }

    @Override
    public void start(AcceptsView container, EventBus eventBus, Collection<EventRegistration> registrations) {
        super.start(container, eventBus, registrations);

        environment.getApp().getRequests().sendAndReturn(
            tenantMapper,
            params -> tenantResource.get(params, realm),
            200,
            tenant -> {
                view.setHeadline(tenant.getDisplayName(), environment.getMessages().editTenantRuleset());
            }
        );
    }

    @Override
    protected TenantRuleset newRuleset() {
        return new TenantRuleset(realm, null, null, null);
    }

    @Override
    protected EntityReader<TenantRuleset> getEntityReader() {
        return tenantRulesetMapper;
    }

    @Override
    protected Consumer<RequestParams<Void, TenantRuleset>> loadRequestConsumer() {
        return params -> rulesetResource.getTenantRuleset(params, rulesetId);
    }

    @Override
    protected EntityWriter<TenantRuleset> getEntityWriter() {
        return tenantRulesetMapper;
    }

    @Override
    protected Consumer<RequestParams<TenantRuleset, Void>> createRequestConsumer() {
        return params -> rulesetResource.createTenantRuleset(params, ruleset);
    }

    @Override
    protected void afterCreate() {
        environment.getPlaceController().goTo(new TenantRulesListPlace(realm));
    }

    @Override
    protected Consumer<RequestParams<TenantRuleset, Void>> updateRequestConsumer() {
        return params -> rulesetResource.updateTenantRuleset(params, rulesetId, ruleset);
    }

    @Override
    protected void afterUpdate() {
        environment.getPlaceController().goTo(new TenantRulesEditorPlace(realm, rulesetId));
    }

    @Override
    protected Consumer<RequestParams<Void, Void>> deleteRequestConsumer() {
        return params -> rulesetResource.deleteTenantRuleset(params, rulesetId);
    }

    @Override
    protected void afterDelete() {
        environment.getPlaceController().goTo(new TenantRulesListPlace(realm));
    }

    @Override
    public void cancel() {
        environment.getPlaceController().goTo(new TenantRulesListPlace(realm));
    }
}
