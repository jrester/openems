package io.openems.edge.controller.revoletion;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.component.AbstractOpenemsComponent;
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
	
	private Config config = null;
	private RevoletionWebsocketClient revoletionClient;
	
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
		log.info("Activating REVOL-E-TION controller");
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;
		if (!this.isEnabled()) {
			log.info("Aborting because controller is disabled");
			// abort if disabled
			return;
		}


		log.info("Connecting to REVOL-E-TION control server");
		URI uri = URI.create("ws://" + this.config.revoletion_ctrl_server_host() + ":" + this.config.revoletion_ctrl_server_port() + "/ws");
		RevoletionWebsocketClient.PowerPlanCallback callback = new RevoletionWebsocketClient.PowerPlanCallback() {
			public void success(int power) {
				ControllerRevoletionImpl.this.applyPowerPlanResult(power);
			}
		};
	
		this.revoletionClient = new RevoletionWebsocketClient(uri, callback);
		this.revoletionClient.connect();
		log.info("Connected to REVOL-E-TION control server");
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
		this.revoletionClient.close();
	}

	@Override
	public void run() throws OpenemsNamedException {
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
