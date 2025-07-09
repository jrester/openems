package io.openems.edge.controller.revoletion;

import java.net.URI;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.evcs.api.ManagedEvcs;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Controller.io.openems.edge.controller.revoletion", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class ControllerRevoletionImpl extends AbstractOpenemsComponent implements ControllerRevoletion, Controller, OpenemsComponent {
	private Logger log = LoggerFactory.getLogger(ControllerRevoletionImpl.class);
	
	@Reference
	private ConfigurationAdmin cm;
	
	@Reference
	private ComponentManager componentManager;
	

	private Config config;
	private RevoletionWebsocketClient revoletionClient;

	private boolean connected = false;
	private boolean shouldPlan = true;


	@Reference(policyOption = ReferencePolicyOption.GREEDY)
	private ManagedEvcs evcs;

	public ControllerRevoletionImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				ControllerRevoletion.ChannelId.values() //
		);
	}


	@Activate
	private void activate(ComponentContext context, Config config) {
		this.log.info("Activating REVOL-E-TION controller");
		super.activate(context, config.id(), config.alias(), config.enabled());
		
		if (!this.isEnabled()) {
			this.log.info("Aborting because controller is disabled");
			return;
		}

		this.applyConfig(config);
	}
	
	@Modified
	private void modified(ComponentContext context, Config config) throws OpenemsNamedException {
		this.log.info("Configuration for REVOL-E-TION controller was changed; restarting client");
		try {
			this.revoletionClient.closeBlocking();
		} catch (InterruptedException e) {
			this.log.warn("Failed to close existing REVOL-E-TION connection: " + e);
		}
		this.applyConfig(config);
		super.modified(context, config.id(), config.alias(), config.enabled());
	}
	
	private void applyConfig(Config config) {
		this.log.info("Applying config: " + config.toString());
		this.config = config;
		this.log.info("Connecting to REVOL-E-TION control server");
		URI uri = URI.create("ws://" + this.config.server_host() + ":" + this.config.server_port() + "/ws");
		RevoletionWebsocketClient.PowerPlanCallback callback = new RevoletionWebsocketClient.PowerPlanCallback() {
			public void success(int power) {
				ControllerRevoletionImpl.this.applyPowerPlanResult(power);
			}
			public void error(String msg) {
				ControllerRevoletionImpl.this.shouldPlan = true;
			}
		};

		this.revoletionClient = new RevoletionWebsocketClient(uri, callback);

		try {
			this.connected = this.revoletionClient.connectBlocking();
		} catch (InterruptedException e) {
			this.log.warn("Failed to connect to REVOL-E-TION control server at " + this.revoletionClient.getURI().toString() + ". Will retry.");
			return;
		}
		
		this.log.info("Connected to REVOL-E-TION control server at " + this.revoletionClient.getURI().toString());
	}

	private boolean ensureConnected() {
		if(this.connected) { return true; }

		try {
			this.connected = this.revoletionClient.reconnectBlocking();
		} catch (InterruptedException e) {
			this.log.warn("Failed to connect to REVOL-E-TION control server at " + this.revoletionClient.getURI().toString() + ". Will retry.");
			return false;
		}

		if(this.connected) {
			this.log.info("Connected to REVOL-E-TION control server");
			return true;
		} else {
			this.log.warn("Failed to connect to REVOL-E-TION control server at " + this.revoletionClient.getURI().toString() + ". Will retry.");
			return false;
		}
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
		try {
			this.revoletionClient.closeBlocking();
		} catch(InterruptedException e) { }
	}

	@Override
	public void run() throws OpenemsNamedException {
		if(!this.ensureConnected()) { return; }

		if(shouldPlan) {
			var planId = "plan_" + Instant.now().toEpochMilli();
			this.log.info("Execute power plan " + planId);
			this.revoletionClient.sendPlanRequest(planId);
			this.shouldPlan = false;
		}
	}


	private void applyPowerPlanResult(int power) {
		this.shouldPlan = true;
		this.log.info("Apply power plan result " + power);
		try {
			this.evcs.applyChargePowerLimit(power);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
