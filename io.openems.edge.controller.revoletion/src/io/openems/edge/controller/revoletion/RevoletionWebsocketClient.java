package io.openems.edge.controller.revoletion;

import java.net.URI;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.utils.JsonUtils;

class RevoletionWebsocketClient extends WebSocketClient {
	public interface PowerPlanCallback {
		public void error(String msg);
		public void success(int power, double soc);
	}
	
	public record PowerPlanData(LocalDateTime startTime, double bevSoc) {};

	private final Logger log = LoggerFactory.getLogger(RevoletionWebsocketClient.class);

	private PowerPlanCallback powerPlanCompletedCallback;

	public RevoletionWebsocketClient(URI serverUri, PowerPlanCallback powerPlanCompletedCallback) {
		super(serverUri);
		this.powerPlanCompletedCallback = powerPlanCompletedCallback;
	}

	protected void sendPlanRequest(String planId, PowerPlanData planData) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d.M.yyyy HH:mm");
		var msg = JsonUtils.buildJsonObject()
				.addProperty("command", "plan")
				.add("data", JsonUtils.buildJsonObject()
						.addProperty("plan_id", planId)
						.add("plan_data", JsonUtils.buildJsonObject()
							.addProperty("start_time", planData.startTime().format(formatter))
							.addProperty("bev_soc", planData.bevSoc())
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
			var data = body.get("data").getAsJsonObject();

			if (!status.equals("ok")) {
				this.log.error("Power plan failed: " + msg);
				this.powerPlanCompletedCallback.error(msg);
				return;
			}

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
				var power = result.get("power").getAsInt();
				var soc = result.get("soc").getAsDouble();
				this.powerPlanCompletedCallback.success(power, soc);
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
