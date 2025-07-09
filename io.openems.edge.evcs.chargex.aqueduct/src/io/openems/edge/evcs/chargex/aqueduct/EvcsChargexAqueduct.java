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
		 // Identification and Detection
	    UNIT_ID(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.NONE).accessMode(AccessMode.READ_ONLY) //
	        .text("Unit ID of the device")),

	    MANUFACTURER(Doc.of(OpenemsType.STRING) //
	        .accessMode(AccessMode.READ_ONLY) //
	        .text("Manufacturer name")),

	    DEVICE_TYPE(Doc.of(OpenemsType.STRING) //
	        .accessMode(AccessMode.READ_ONLY) //
	        .text("Device type")),

	    FW_VERSION(Doc.of(OpenemsType.STRING) //
	        .accessMode(AccessMode.READ_ONLY) //
	        .text("Firmware version")),

	    SERIAL_NR(Doc.of(OpenemsType.STRING) //
	        .accessMode(AccessMode.READ_ONLY) //
	        .text("Serial number")),

	    // Overall System Status
	    PAC_MINIMUM(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.WATT).accessMode(AccessMode.READ_ONLY) //
	        .text("Minimum power of the system")),

	    CONNECTED_CARS(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.NONE).accessMode(AccessMode.READ_ONLY) //
	        .text("Number of connected cars")),

	    ACTIVE_SESSIONS(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.NONE).accessMode(AccessMode.READ_ONLY) //
	        .text("Number of active charging sessions")),

	    N_MODULES(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.NONE).accessMode(AccessMode.READ_ONLY) //
	        .text("Number of charging modules")),

	    SYSTEM_STATUS(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.NONE).accessMode(AccessMode.READ_ONLY) //
	        .text("System status bits")),

	    MAX_CURRENT(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.MILLIAMPERE).accessMode(AccessMode.READ_ONLY) //
	        .text("Maximum current of the system")),

	    // Per Module Information - Module 0
	    PAC_MODULE_0(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.WATT).accessMode(AccessMode.READ_ONLY) //
	        .text("Active power of module 0")),

	    IAC_L1_MODULE_0(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.MILLIAMPERE).accessMode(AccessMode.READ_ONLY) //
	        .text("Current on phase 1 of module 0")),

	    IAC_L2_MODULE_0(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.MILLIAMPERE).accessMode(AccessMode.READ_ONLY) //
	        .text("Current on phase 2 of module 0")),

	    IAC_L3_MODULE_0(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.MILLIAMPERE).accessMode(AccessMode.READ_ONLY) //
	        .text("Current on phase 3 of module 0")),

	    STATES_CP_MODULE_0(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.NONE).accessMode(AccessMode.READ_ONLY) //
	        .text("CP states of module 0")),

	    ERRORS_CP_MODULE_0(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.NONE).accessMode(AccessMode.READ_ONLY) //
	        .text("CP errors of module 0")),

	    // Per Module Information - Module 1
	    PAC_MODULE_1(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.WATT).accessMode(AccessMode.READ_ONLY) //
	        .text("Active power of module 1")),

	    IAC_L1_MODULE_1(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.MILLIAMPERE).accessMode(AccessMode.READ_ONLY) //
	        .text("Current on phase 1 of module 1")),

	    IAC_L2_MODULE_1(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.MILLIAMPERE).accessMode(AccessMode.READ_ONLY) //
	        .text("Current on phase 2 of module 1")),

	    IAC_L3_MODULE_1(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.MILLIAMPERE).accessMode(AccessMode.READ_ONLY) //
	        .text("Current on phase 3 of module 1")),

	    STATES_CP_MODULE_1(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.NONE).accessMode(AccessMode.READ_ONLY) //
	        .text("CP states of module 1")),

	    ERRORS_CP_MODULE_1(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.NONE).accessMode(AccessMode.READ_ONLY) //
	        .text("CP errors of module 1")),

	    // Per Module Information - Module 2
	    PAC_MODULE_2(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.WATT).accessMode(AccessMode.READ_ONLY) //
	        .text("Active power of module 2")),

	    IAC_L1_MODULE_2(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.MILLIAMPERE).accessMode(AccessMode.READ_ONLY) //
	        .text("Current on phase 1 of module 2")),

	    IAC_L2_MODULE_2(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.MILLIAMPERE).accessMode(AccessMode.READ_ONLY) //
	        .text("Current on phase 2 of module 2")),

	    IAC_L3_MODULE_2(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.MILLIAMPERE).accessMode(AccessMode.READ_ONLY) //
	        .text("Current on phase 3 of module 2")),

	    STATES_CP_MODULE_2(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.NONE).accessMode(AccessMode.READ_ONLY) //
	        .text("CP states of module 2")),

	    ERRORS_CP_MODULE_2(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.NONE).accessMode(AccessMode.READ_ONLY) //
	        .text("CP errors of module 2")),

	    // Per Module Information - Module 3
	    PAC_MODULE_3(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.WATT).accessMode(AccessMode.READ_ONLY) //
	        .text("Active power of module 3")),

	    IAC_L1_MODULE_3(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.MILLIAMPERE).accessMode(AccessMode.READ_ONLY) //
	        .text("Current on phase 1 of module 3")),

	    IAC_L2_MODULE_3(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.MILLIAMPERE).accessMode(AccessMode.READ_ONLY) //
	        .text("Current on phase 2 of module 3")),

	    IAC_L3_MODULE_3(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.MILLIAMPERE).accessMode(AccessMode.READ_ONLY) //
	        .text("Current on phase 3 of module 3")),

	    STATES_CP_MODULE_3(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.NONE).accessMode(AccessMode.READ_ONLY) //
	        .text("CP states of module 3")),

	    ERRORS_CP_MODULE_3(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.NONE).accessMode(AccessMode.READ_ONLY) //
	        .text("CP errors of module 3")),

	    // Configuration - Read/Write
	    PAC_TARGET_TIMEOUT(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.SECONDS).accessMode(AccessMode.READ_ONLY) //
	        .text("Power target timeout")),

	    PAC_DEFAULT_POWER(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.WATT).accessMode(AccessMode.READ_ONLY) //
	        .text("Default power setting")),

	    PAC_TARGET_POWER(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.WATT).accessMode(AccessMode.READ_ONLY) //
	        .text("Target power setting")),

	    // TODO: use enum for charging mode.
	    CHARGING_MODE(Doc.of(OpenemsType.INTEGER) //
	        .accessMode(AccessMode.READ_ONLY) //
	        .text("Current charging mode")),

	    // Write-only channels for control
	    SET_PAC_TARGET_TIMEOUT(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.SECONDS).accessMode(AccessMode.WRITE_ONLY) //
	        .text("Set power target timeout")),

	    SET_PAC_DEFAULT_POWER(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.WATT).accessMode(AccessMode.WRITE_ONLY) //
	        .text("Set default power")),

	    SET_PAC_TARGET_POWER(Doc.of(OpenemsType.INTEGER) //
	        .unit(Unit.WATT).accessMode(AccessMode.WRITE_ONLY) //
	        .text("Set target power limit")),

	    SET_CHARGING_MODE(Doc.of(OpenemsType.INTEGER) //
	        .accessMode(AccessMode.WRITE_ONLY) //
	        .text("Set charging mode"));

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
