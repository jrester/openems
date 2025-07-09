package io.openems.edge.evcs.chargex.aqueduct;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.Unit;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.component.OpenemsComponent;

public interface EvcsChargexAqueduct extends OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

	    STATES_CP_MODULE_0(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.NONE).accessMode(AccessMode.READ_ONLY) //
	        .text("CP states of module 0")),

	    SET_PAC_TARGET_POWER(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.WATT).accessMode(AccessMode.WRITE_ONLY) //
	        .text("Set target power limit")),
	    ;

		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	public default IntegerWriteChannel getSetTargetPowerChannel() {
		return this.channel(ChannelId.SET_PAC_TARGET_POWER);
	}

	public default void setTargetPower(int power) throws OpenemsNamedException {
		this.getSetTargetPowerChannel().setNextWriteValue(power);
	}
}
