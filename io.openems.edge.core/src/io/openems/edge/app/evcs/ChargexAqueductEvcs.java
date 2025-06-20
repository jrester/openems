package io.openems.edge.app.evcs;

import static io.openems.edge.app.common.props.CommonProps.alias;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.google.gson.JsonElement;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.function.ThrowingTriFunction;
import io.openems.common.oem.OpenemsEdgeOem;
import io.openems.common.session.Language;
import io.openems.common.types.EdgeConfig;
import io.openems.common.utils.JsonUtils;
import io.openems.edge.app.common.props.CommunicationProps;
import io.openems.edge.app.evcs.ChargexAqueductEvcs.Property;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.host.Host;
import io.openems.edge.common.meta.Meta;
import io.openems.edge.core.appmanager.AbstractOpenemsApp;
import io.openems.edge.core.appmanager.AbstractOpenemsAppWithProps;
import io.openems.edge.core.appmanager.AppConfiguration;
import io.openems.edge.core.appmanager.AppDef;
import io.openems.edge.core.appmanager.AppDescriptor;
import io.openems.edge.core.appmanager.ComponentUtil;
import io.openems.edge.core.appmanager.ConfigurationTarget;
import io.openems.edge.core.appmanager.HostSupplier;
import io.openems.edge.core.appmanager.MetaSupplier;
import io.openems.edge.core.appmanager.Nameable;
import io.openems.edge.core.appmanager.OpenemsApp;
import io.openems.edge.core.appmanager.OpenemsAppCardinality;
import io.openems.edge.core.appmanager.OpenemsAppCategory;
import io.openems.edge.core.appmanager.TranslationUtil;
import io.openems.edge.core.appmanager.Type;
import io.openems.edge.core.appmanager.Type.Parameter;
import io.openems.edge.core.appmanager.Type.Parameter.BundleParameter;
import io.openems.edge.core.appmanager.dependency.Tasks;
import io.openems.edge.core.appmanager.dependency.aggregatetask.SchedulerByCentralOrderConfiguration.SchedulerComponent;

@Component(name = "App.Evcs.Chargex.Aqueduct")
public class ChargexAqueductEvcs extends AbstractOpenemsAppWithProps<ChargexAqueductEvcs, Property, Parameter.BundleParameter> implements OpenemsApp, HostSupplier, MetaSupplier {
	public enum Property implements Type<Property, ChargexAqueductEvcs, Parameter.BundleParameter>, Nameable {
		// Component-IDs
		EVCS_ID(AppDef.componentId("evcs0")), //
		CTRL_EVCS_ID(AppDef.componentId("ctrlEvcs0")), //
		// Properties
		ALIAS(alias()), //

		IP(AppDef.copyOfGeneric(CommunicationProps.excludingIp()) //
				.setDefaultValue("192.168.25.11") //
				.setRequired(true)), //
		PORT(AppDef.copyOfGeneric(CommunicationProps.port()) //
			.setDefaultValue(1502) //
			.setRequired(true)), //
		MAX_HARDWARE_POWER_ACCEPT_PROPERTY(AppDef.of() //
				.setAllowedToSave(false)), //
		MAX_HARDWARE_POWER(AppDef.copyOfGeneric(//
				EvcsProps.clusterMaxHardwarePowerSingleCp(MAX_HARDWARE_POWER_ACCEPT_PROPERTY, EVCS_ID))), //
		MODBUS_ID(AppDef.componentId("modbus0")), //
		;

		private final AppDef<? super ChargexAqueductEvcs, ? super Property, ? super BundleParameter> def;

		private Property(AppDef<? super ChargexAqueductEvcs, ? super Property, ? super BundleParameter> def) {
			this.def = def;
		}

		@Override
		public Type<Property, ChargexAqueductEvcs, BundleParameter> self() {
			return this;
		}

		@Override
		public AppDef<? super ChargexAqueductEvcs, ? super Property, ? super BundleParameter> def() {
			return this.def;
		}

		@Override
		public Function<GetParameterValues<ChargexAqueductEvcs>, BundleParameter> getParamter() {
			return Parameter.functionOf(AbstractOpenemsApp::getTranslationBundle);
		}
	}
	
	private final Host host;
	private final Meta meta;
	
	@Activate
	public ChargexAqueductEvcs(//
			@Reference ComponentManager componentManager, //
			ComponentContext componentContext, //
			@Reference ConfigurationAdmin cm, //
			@Reference ComponentUtil componentUtil, //
			@Reference Host host, //
			@Reference Meta meta //
	) {
		super(componentManager, componentContext, cm, componentUtil);
		this.host = host;
		this.meta = meta;
	}
	
	@Override
	public OpenemsAppCardinality getCardinality() {
		return OpenemsAppCardinality.MULTIPLE;
	}

	@Override
	public OpenemsAppCategory[] getCategories() {
		return new OpenemsAppCategory[] { OpenemsAppCategory.EVCS };
	}

	@Override
	protected ChargexAqueductEvcs getApp() {
		return this;
	}

	@Override
	protected Property[] propertyValues() {
		return Property.values();
	}
	
	@Override
	public Host getHost() {
		return this.host;
	}

	@Override
	public Meta getMeta() {
		return this.meta;
	}

	@Override
	public AppDescriptor getAppDescriptor(OpenemsEdgeOem oem) {
		return AppDescriptor.create() //
				.build();
	}

	@Override
	protected ThrowingTriFunction<ConfigurationTarget, Map<Property, JsonElement>, Language, AppConfiguration, OpenemsNamedException> appPropertyConfigurationFactory() {
		return (t, p, l) -> {
			final var bundle = AbstractOpenemsApp.getTranslationBundle(l);

			final var controllerAlias = TranslationUtil.getTranslation(AbstractOpenemsApp.getTranslationBundle(l),
					"App.Evcs.controller.alias");
			
			final var ip = this.getString(p, l, Property.IP);
			final var alias = this.getString(p, l, Property.ALIAS);
			
			final var evcsId = this.getId(t, p, Property.EVCS_ID);
			final var ctrlEvcsId = this.getId(t, p, Property.CTRL_EVCS_ID);
			final var modbusId = this.getId(t, p, Property.MODBUS_ID);
			
			final var components = new ArrayList<EdgeConfig.Component>();
			components.add(new EdgeConfig.Component(evcsId, alias, "Evcs.Chargex.Aqueduct", JsonUtils.buildJsonObject()
					.addPropertyIfNotNull("modbus.id", modbusId) //
					.build()));
			
			components.add(new EdgeConfig.Component(ctrlEvcsId, controllerAlias, "Controller.Evcs", 
					JsonUtils.buildJsonObject() //
						.addProperty("evcs.id", evcsId) //
						.build()));
			
			components.add(new EdgeConfig.Component(modbusId, 
					TranslationUtil.getTranslation(bundle, "App.Evcs.Chargex.Aqueduct.modbus.aliias"), 
					"Bridge.Modbus.Tcp", JsonUtils.buildJsonObject() //
					.addProperty("ip", ip)
					.build()));
			
			return AppConfiguration.create() //
					.addTask(Tasks.component(components)) //
					.addTask(Tasks.schedulerByCentralOrder(new SchedulerComponent(ctrlEvcsId, "Controller.Evcs", this.getAppId()))) //
					.build();
		};
	}
}
