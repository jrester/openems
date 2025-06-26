package io.openems.edge.controller.revoletion;

import java.net.URI;
import java.nio.ByteBuffer;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.utils.JsonUtils;

class RevoletionWebsocketClient extends WebSocketClient {
	public interface PowerPlanCallback {
		public void success(int power);
	}
	
	private final Logger log = LoggerFactory.getLogger(RevoletionWebsocketClient.class);
	
	private PowerPlanCallback powerPlanCompletedCallback;
	
	public RevoletionWebsocketClient(URI serverUri, PowerPlanCallback powerPlanCompletedCallback) {
		super(serverUri);
		this.powerPlanCompletedCallback = powerPlanCompletedCallback;
	}

	protected void sendPlanRequest(String planId) {
		var msg = JsonUtils.buildJsonObject()
				.addProperty("command", "plan")
				.add("data", JsonUtils.buildJsonObject()
						.addProperty("plan_id", planId)
						.addProperty("plan_data", "placeholder")
						.build()
				);
		this.send(msg.toString());
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
		this.log.info("received message: " + message);
		try {
			var body = JsonUtils.parse(message).getAsJsonObject();
			var status = body.get("status").getAsString();
			var msg = body.get("msg").getAsString();
			var data = body.get("data").getAsString();
			
			if (status != "ok") {
				this.log.error("Power plan failed: " + msg);
				return;
			}
			
			if(msg == "power_plan_started") {
				this.log.info("Power plan started");
				return;
			} else if (msg == "power_plan_failed") {
				this.log.error("Power plan failed");
				return;
			} else if (msg == "power_plan_completed") {
				this.log.error("Power plan completed");
				var result = body.get("result").getAsJsonObject();
				var power = result.get("power").getAsInt();
				this.powerPlanCompletedCallback.success(power);
			}
		} catch (OpenemsNamedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
