package io.openems.edge.controller.revoletion;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.utils.JsonUtils;

class RevoletionWebsocketClient extends WebSocketClient {
	public interface PowerPlanCallback {
		public void error(String msg);
		public void success(PowerPlanResult result);
	}
	
	public record PowerPlanData(Map<String, Double> socs, int planningIntervalMinutes) {};
	public record PowerPlanResult(Map<String, Integer> powers, Map<String, Double> socs) {};

	private final Logger log = LoggerFactory.getLogger(RevoletionWebsocketClient.class);

	private PowerPlanCallback powerPlanCompletedCallback;

	public RevoletionWebsocketClient(URI serverUri, PowerPlanCallback powerPlanCompletedCallback) {
		super(serverUri);
		this.powerPlanCompletedCallback = powerPlanCompletedCallback;
	}

	protected void sendPlanRequest(String planId, PowerPlanData planData) {
		var socs = JsonUtils.buildJsonObject();
		for(Entry<String, Double> entry : planData.socs().entrySet()) {
			socs.addProperty(entry.getKey(), entry.getValue());
		}
		var msg = JsonUtils.buildJsonObject()
				.addProperty("command", "plan")
				.add("data", JsonUtils.buildJsonObject()
						.addProperty("plan_id", planId)
						.add("plan_data", JsonUtils.buildJsonObject()
							.add("socs", socs.build())
							.addProperty("timestep_minutes", planData.planningIntervalMinutes())
							.build()
						)
						.build()
				);
		log.debug("Send power plan request: "+ msg.build().toString());
		this.send(msg.build().toString());
	}


	@Override
	public void onOpen(ServerHandshake handshakedata) {
		this.log.info("connected to REVOL-E-TION ctrl");
	}

	@Override
	public void onClose(int code, String reason, boolean remote) {
		this.log.info("closed with exit code " + code + " additional info: " + reason);
	}

	@Override
	public void onMessage(String message) {
		this.log.debug("received message: " + message);
		try {
			var body = JsonUtils.parse(message).getAsJsonObject();
			var status = body.get("status").getAsString();
			var msg = body.get("msg").getAsString();
			if (!status.equals("ok")) {
				this.log.error("Power plan failed: " + msg);
				this.powerPlanCompletedCallback.error(msg);
				return;
			}
			
			var data = body.get("data").getAsJsonObject();



			if(msg.equals("power_plan_started")) {
				var planId = data.get("plan_id").getAsString();
				this.log.info("Power plan " + planId + " started");
				return;
			} else if (msg.equals("power_plan_failed")) {
				var planId = data.get("plan_id").getAsString();
				this.log.error("Power plan " + planId + " failed");
				this.powerPlanCompletedCallback.error(msg);
				return;
			} else if (msg.equals("power_plan_completed")) {
				var planId = data.get("plan_id").getAsString();
				this.log.info("Power plan " + planId + " completed");
				var result = data.get("result").getAsJsonObject();
				
				var powers = result.get("powers").getAsJsonObject();
				var powersMap = new HashMap<String, Integer>();
				for(Entry<String, JsonElement> entry : powers.entrySet()) {
					powersMap.put(entry.getKey(), entry.getValue().getAsInt());
				}
				
				var socs = result.get("socs").getAsJsonObject();
				var socsMap = new HashMap<String, Double>();
				for(Entry<String, JsonElement> entry : socs.entrySet()) {
					socsMap.put(entry.getKey(), entry.getValue().getAsDouble());
				}
				
				var powerPlanResult = new PowerPlanResult(powersMap, socsMap);
				this.powerPlanCompletedCallback.success(powerPlanResult);
			}
		} catch (OpenemsNamedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			this.powerPlanCompletedCallback.error("");
			return;
		}


	}

	@Override
	public void onMessage(ByteBuffer message) {
		this.log.info("received ByteBuffer");
	}

	@Override
	public void onError(Exception ex) {
		this.log.error("an error occurred:" + ex);
	}
}
