package io.openems.edge.controller.revoletion;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.evcs.api.ManagedEvcs;
import io.openems.edge.evcs.api.Status;

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
	private Clock clock;
	
	private RevoletionWebsocketClient revoletionClient;
	private boolean connected = false;
	
	//private LocalDateTime startTime = LocalDateTime.parse("17.7.2025 10:30", DateTimeFormatter.ofPattern("d.M.yyyy HH:mm"));
	private Instant lastPlan = null;
	private double lastSoc = 0.3;
	private boolean planning = false;


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

		this.clock = this.componentManager.getClock();
		
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
		this.config = config;
		this.log.info("Connecting to REVOL-E-TION control server");
		URI uri = URI.create("ws://" + this.config.server_host() + ":" + this.config.server_port() + "/ws");
		RevoletionWebsocketClient.PowerPlanCallback callback = new RevoletionWebsocketClient.PowerPlanCallback() {
			public void success(int power, double soc) {
				ControllerRevoletionImpl.this.lastSoc = soc;
				ControllerRevoletionImpl.this.applyPowerPlanResult(power);
			}
			public void error(String msg) {
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
	
	private void resetPlanningState() {
		this.lastPlan = null;
		this.lastSoc = 0.3;
		this.planning = false;
	}

	@Override
	public void run() throws OpenemsNamedException {
		if(!this.shouldPlan()) {
			return;
		}
		if(this.evcs.getStatus() != Status.CHARGING && this.evcs.getStatus() != Status.READY_FOR_CHARGING && this.evcs.getStatus() != Status.STARTING) {
			if(this.planning) {
				this.log.info("Not planning because status of evcs is " + this.evcs.getStatus());
				this.resetPlanningState();
			}
			return;
		}
		
		if(!this.ensureConnected()) { 
			return; 
		}
	
		var planStartTime = LocalDateTime.now();
		var planId = "plan_" + planStartTime.toEpochSecond(ZoneOffset.UTC);
		this.log.info("Execute power plan " + planId);
		var planData = new RevoletionWebsocketClient.PowerPlanData(this.lastSoc);
		this.revoletionClient.sendPlanRequest(planId, planData);
		
		this.lastPlan = Instant.now(this.clock);
		this.planning = true;
	}


	private void applyPowerPlanResult(int power) {
		this.log.info("Apply charge power limit of " + power + "W to " + this.evcs.id());
		try {
			this.evcs.applyChargePowerLimit(power);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private boolean shouldPlan() {
		if(this.lastPlan == null) {
			return true;
		}
		
		return Instant.now(this.clock).isAfter(this.lastPlan.plusSeconds(this.config.plan_interval() * 60));
	}
}
