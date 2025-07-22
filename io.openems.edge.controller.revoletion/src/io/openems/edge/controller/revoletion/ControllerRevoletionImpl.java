package io.openems.edge.controller.revoletion;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	private Clock clock;
	
	private Map<String, String> evcsId2BevId;
	private RevoletionWebsocketClient revoletionClient;
	private boolean connected = false;
	
	private Instant lastPlan = null;
	private Map<String, Double> lastSocs;
	private boolean planning = false;


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
			public void success(RevoletionWebsocketClient.PowerPlanResult result) {
				ControllerRevoletionImpl.this.lastSocs = result.socs();
				ControllerRevoletionImpl.this.applyPowerPlanResult(result.powers());
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
		this.resetPlanningState();
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
		this.lastSocs = new HashMap<>();
		this.evcsId2BevId = new HashMap<>();
		for(int i = 0; i<this.config.evcs_ids().length; i++) {
			var bevId = "bev" + i;
			var evcsId = this.config.evcs_ids()[i];
			this.lastSocs.put(bevId, 0.3);
			this.evcsId2BevId.put(evcsId, bevId);
		}
		this.planning = false;
	}

	@Override
	public void run() throws OpenemsNamedException {
		if(!this.shouldPlan()) {
			return;
		}
		// Wallbox does not correctly set status.
		/*
		if(this.evcs.getStatus() != Status.CHARGING && this.evcs.getStatus() != Status.READY_FOR_CHARGING && this.evcs.getStatus() != Status.STARTING) {
			if(this.planning) {
				this.log.info("Not planning because status of evcs is " + this.evcs.getStatus());
				this.resetPlanningState();
			}
			return;
		}*/
		
		if(!this.ensureConnected()) { 
			return; 
		}
	
		var planStartTime = LocalDateTime.now();
		var planId = "plan_" + planStartTime.toEpochSecond(ZoneOffset.UTC);
		this.log.info("Execute power plan " + planId);
		var planData = new RevoletionWebsocketClient.PowerPlanData(this.lastSocs, this.config.plan_interval());
		this.revoletionClient.sendPlanRequest(planId, planData);
		
		this.lastPlan = Instant.now(this.clock);
		this.planning = true;
	}


	private void applyPowerPlanResult(Map<String, Integer> powers) {
		for(String evcsId : this.config.evcs_ids()) {
			ManagedEvcs evcs;
			try {
				evcs = this.componentManager.getComponent(evcsId);
			} catch (OpenemsNamedException e) {
				e.printStackTrace();
				continue;
			}
			var bevId = this.evcsId2BevId.get(evcsId);
			var power = powers.get(bevId);
			this.log.info("Apply charge power limit of " + power + "W to " + evcsId);
			try {
				evcs.applyChargePowerLimit(power.intValue());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private boolean shouldPlan() {
		int intervalMinutes = this.config.plan_interval();
		
		Instant now = Instant.now(this.clock);
		ZonedDateTime nowZoned = now.atZone(this.clock.getZone());
		int currentMinute = nowZoned.getMinute();
		boolean isAtIntervalBoundary = currentMinute % intervalMinutes == 0;
		
		if(!isAtIntervalBoundary) {
			return false;
		}	
		
		if(this.lastPlan == null) {
			return true;
		}
		
		ZonedDateTime lastPlanZoned = this.lastPlan.atZone(this.clock.getZone());
		
	    int currentIntervalSlot = nowZoned.getMinute() / intervalMinutes;
	    int lastPlanIntervalSlot = lastPlanZoned.getMinute() / intervalMinutes;
	    
	    return currentIntervalSlot != lastPlanIntervalSlot || 
	            nowZoned.getHour() != lastPlanZoned.getHour() ||
	            !nowZoned.toLocalDate().equals(lastPlanZoned.toLocalDate());
	}
}
