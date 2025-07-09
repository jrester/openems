package io.openems.edge.evcs.chargex.aqueduct;

import static io.openems.edge.evcs.api.ChargingType.AC;
import static io.openems.edge.evcs.api.Phases.THREE_PHASE;

import java.time.Clock;
import java.time.Instant;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.MeterType;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.UnsignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC16WriteRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.evcs.api.ChargeStateHandler;
import io.openems.edge.evcs.api.DeprecatedEvcs;
import io.openems.edge.evcs.api.Evcs;
import io.openems.edge.evcs.api.EvcsPower;
import io.openems.edge.evcs.api.ManagedEvcs;
import io.openems.edge.evcs.api.PhaseRotation;
import io.openems.edge.evcs.api.Status;
import io.openems.edge.evcs.api.WriteHandler;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "ChargeX.Aqueduct", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class EvcsChargexAqueductImpl extends AbstractOpenemsModbusComponent implements Evcs, ElectricityMeter, EvcsChargexAqueduct, ModbusComponent, OpenemsComponent, ManagedEvcs, TimedataProvider, DeprecatedEvcs, EventHandler {

	private final Logger log = LoggerFactory.getLogger(EvcsChargexAqueductImpl.class);

	private final ChargeStateHandler chargeStateHandler = new ChargeStateHandler(this);

	private Clock clock;

	@Reference
	private ComponentManager componentManager;

	@Reference
	private ConfigurationAdmin cm;

	@Reference
	private EvcsPower evcsPower;

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL)
	private volatile Timedata timedata = null;

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	private Instant lastWrite;
	private Config config = null;
	/**
	 * Processes the controller's writes to this evcs component.
	 */
	private final WriteHandler writeHandler = new WriteHandler(this);

	public EvcsChargexAqueductImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ModbusComponent.ChannelId.values(), //
				ElectricityMeter.ChannelId.values(), //
				Evcs.ChannelId.values(), //
				ManagedEvcs.ChannelId.values(), //
				EvcsChargexAqueduct.ChannelId.values(), //
				DeprecatedEvcs.ChannelId.values() //
		);
		DeprecatedEvcs.copyToDeprecatedEvcsChannels(this);

	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		if(super.activate(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId(), this.cm, "Modbus",
				config.modbus_id())) {
			return;
		}
		this.config = config;

		this._setPowerPrecision(1);
		this._setChargingType(AC);
		this._setPhases(THREE_PHASE);

		this.clock = this.componentManager.getClock();
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		final var modbusProtocol = new ModbusProtocol(this,
			    // Identification and Detection
//			    new FC3ReadRegistersTask(0x0002, Priority.LOW,
//			        m(ChargexAqueduct.ChannelId.UNIT_ID, new UnsignedDoublewordElement(0x0002))),
//
//			    new FC3ReadRegistersTask(0x0004, Priority.LOW,
//			        m(ChargexAqueduct.ChannelId.MANUFACTURER, new StringWordElement(0x0004, 8))),
//
//			    new FC3ReadRegistersTask(0x000C, Priority.LOW,
//			        m(ChargexAqueduct.ChannelId.DEVICE_TYPE, new StringWordElement(0x000C, 8))),
//
//			    new FC3ReadRegistersTask(0x0014, Priority.LOW,
//			        m(ChargexAqueduct.ChannelId.FW_VERSION, new StringWordElement(0x0014, 8))),
//
//			    new FC3ReadRegistersTask(0x001C, Priority.LOW,
//			        m(ChargexAqueduct.ChannelId.SERIAL_NR, new StringWordElement(0x001C, 8))),

			    new FC3ReadRegistersTask(0x0024, Priority.HIGH,
			        m(ElectricityMeter.ChannelId.ACTIVE_POWER, new UnsignedDoublewordElement(0x0024))),
//			        m(ChargexAqueduct.ChannelId.PAC_MINIMUM, new UnsignedDoublewordElement(0x0026)),
//			        m(ElectricityMeter.ChannelId.CURRENT_L1, new UnsignedDoublewordElement(0x0028)),
//			        m(ElectricityMeter.ChannelId.CURRENT_L2, new UnsignedDoublewordElement(0x002A)),
//			        m(ElectricityMeter.ChannelId.CURRENT_L3, new UnsignedDoublewordElement(0x002C)),
//			        m(ChargexAqueduct.ChannelId.CONNECTED_CARS, new UnsignedDoublewordElement(0x002E)),
//			        m(ChargexAqueduct.ChannelId.ACTIVE_SESSIONS, new UnsignedDoublewordElement(0x0030)),
//			        m(ChargexAqueduct.ChannelId.N_MODULES, new UnsignedDoublewordElement(0x0032)),
//			        m(ChargexAqueduct.ChannelId.SYSTEM_STATUS, new UnsignedDoublewordElement(0x0034)),
//			        m(ChargexAqueduct.ChannelId.MAX_CURRENT, new UnsignedDoublewordElement(0x0036))),

			    // Per Module Information - Module 0
			    new FC3ReadRegistersTask(0x0066, Priority.HIGH,
			        //m(ElectricityMeter.ChannelId.ACTIVE_POWER, new UnsignedDoublewordElement(0x0064)), // active power of module
			        m(ElectricityMeter.ChannelId.CURRENT_L1, new UnsignedDoublewordElement(0x0066)),
			        m(ElectricityMeter.ChannelId.CURRENT_L2, new UnsignedDoublewordElement(0x0068)),
			        m(ElectricityMeter.ChannelId.CURRENT_L3, new UnsignedDoublewordElement(0x006A)),
			        m(EvcsChargexAqueduct.ChannelId.STATES_CP_MODULE_0, new UnsignedWordElement(0x006C))),
//			        m(ChargexAqueduct.ChannelId.ERRORS_CP_MODULE_0, new UnsignedWordElement(0x006E))),


			    // Readable Configuration - Low Priority
			    new FC3ReadRegistersTask(0x01F4, Priority.LOW,
			        m(EvcsChargexAqueduct.ChannelId.PAC_TARGET_TIMEOUT, new UnsignedDoublewordElement(0x01F4)),
			        m(EvcsChargexAqueduct.ChannelId.PAC_DEFAULT_POWER, new UnsignedDoublewordElement(0x01F6)),
			        m(EvcsChargexAqueduct.ChannelId.PAC_TARGET_POWER, new UnsignedDoublewordElement(0x01F8)),
			        m(EvcsChargexAqueduct.ChannelId.CHARGING_MODE, new UnsignedDoublewordElement(0x01FA))),

			    // Write Tasks for Control
			    new FC16WriteRegistersTask(0x01F4,
			        m(EvcsChargexAqueduct.ChannelId.SET_PAC_TARGET_TIMEOUT, new UnsignedDoublewordElement(0x01F4))),

			    new FC16WriteRegistersTask(0x01F6,
			        m(EvcsChargexAqueduct.ChannelId.SET_PAC_DEFAULT_POWER, new UnsignedDoublewordElement(0x01F6))),

			    new FC16WriteRegistersTask(0x01F8,
			        m(EvcsChargexAqueduct.ChannelId.SET_PAC_TARGET_POWER, new UnsignedDoublewordElement(0x01F8))),

			    new FC16WriteRegistersTask(0x01FA,
			        m(EvcsChargexAqueduct.ChannelId.SET_CHARGING_MODE, new UnsignedDoublewordElement(0x01FA)))
			);

		this.addStatusListener();
		return modbusProtocol;
	}

	private void addStatusListener() {
		this.channel(EvcsChargexAqueduct.ChannelId.STATES_CP_MODULE_0).onSetNextValue(s -> {
	        int stateValue = (int)s.get();

	        // Extract bit flags from the state value
	        boolean charging = (stateValue & (1 << 0)) != 0;
	        boolean singlePhase = (stateValue & (1 << 1)) == 0;
	        boolean authorized = (stateValue & (1 << 2)) != 0;
	        boolean evPresent = (stateValue & (1 << 3)) != 0;
	        boolean batteryFull = (stateValue & (1 << 5)) != 0;

	        var status = Status.UNDEFINED;
	        if(charging) {
			status = Status.CHARGING;
	        } else if(!evPresent) {
			status = Status.NOT_READY_FOR_CHARGING;
	        } else if(evPresent && !authorized) {
			status = Status.NOT_READY_FOR_CHARGING;
	        } else if(evPresent && authorized && !charging) {
			status = Status.READY_FOR_CHARGING;
	        } else if(batteryFull) {
			status = Status.ENERGY_LIMIT_REACHED;
	        }

	        this._setStatus(status);

	        if(singlePhase) {
			this._setPhases(1);
	        } else {
			this._setPhases(THREE_PHASE);
	        }
		});
	}


	@Override
	public String debugLog() {
		var builder = new StringBuilder()
				.append("Active:" + this.getActivePower().orElse(null))
				.append("|P1:" + this.getCurrentL1().orElse(null))
				.append("|P2:" + this.getCurrentL2().orElse(null))
				.append("|P3:" + this.getCurrentL3().orElse(null))
				.append("|Status:" + this.getStatus());
		return  builder.toString();
	}

	@Override
	public PhaseRotation getPhaseRotation() {
		return PhaseRotation.L1_L2_L3;
	}

	@Override
	public MeterType getMeterType() {
		return MeterType.MANAGED_CONSUMPTION_METERED;
	}

	@Override
	public EvcsPower getEvcsPower() {
		return this.evcsPower;
	}

	@Override
	public int getConfiguredMinimumHardwarePower() {
		return this.config.minHwPower();
	}

	@Override
	public int getConfiguredMaximumHardwarePower() {
		return this.config.maxHwPower();
	}

	@Override
	public boolean getConfiguredDebugMode() {
		return this.config.debugMode();
	}

	@Override
	public boolean applyChargePowerLimit(int power) throws Exception {
		// ensure that it is only written once every 5 seconds at most
		if (this.checkWriteIntervall()) {
			return false;
		}
		this.log.info("apply charge power limit " + power);
		this.setTargetPower(power);
		this.lastWrite = Instant.now(this.clock);
		return true;
	}

	@Override
	public boolean pauseChargeProcess() throws Exception {
		return this.applyChargePowerLimit(0);
	}

	@Override
	public boolean applyDisplayText(String text) throws OpenemsException {
		return false;
	}

	@Override
	public int getMinimumTimeTillChargingLimitTaken() {
		return 30;
	}

	private boolean checkWriteIntervall() {
		if (this.lastWrite == null) {
			return false;
		}
		return Instant.now(this.clock).isBefore(this.lastWrite.plusSeconds(5));
	}

	@Override
	public ChargeStateHandler getChargeStateHandler() {
		return this.chargeStateHandler;
	}

	@Override
	public void logDebug(String message) {
		if (this.config.debugMode()) {
			this.logInfo(this.log, message);
		}
	}

	@Override
	public Timedata getTimedata() {
		return this.timedata;
	}

	@Override
	public boolean isReadOnly() {
		return false;
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled()) {
			return;
		}
		switch (event.getTopic()) {
		case EdgeEventConstants.TOPIC_CYCLE_EXECUTE_WRITE -> {
			if (!this.isReadOnly()) {
				this.writeHandler.run();
			}
		}
		}
	}
}
