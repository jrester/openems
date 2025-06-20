package io.openems.edge.evcs.chargex.aqueduct;

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
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.MeterType;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.StringWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC16WriteRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.evcs.api.ChargeStateHandler;
import io.openems.edge.evcs.api.Evcs;
import io.openems.edge.evcs.api.EvcsPower;
import io.openems.edge.evcs.api.ManagedEvcs;
import io.openems.edge.evcs.api.PhaseRotation;
import io.openems.edge.meter.api.ElectricityMeter;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "ChargeX.Aqueduct", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class ChargexAqueductImpl extends AbstractOpenemsModbusComponent implements Evcs, ElectricityMeter, ChargexAqueduct, ModbusComponent, OpenemsComponent, ManagedEvcs {

	private final Logger log = LoggerFactory.getLogger(ChargexAqueductImpl.class);
	
	private final ChargeStateHandler chargeStateHandler = new ChargeStateHandler(this);
	
	@Reference
	private ConfigurationAdmin cm;
	
	@Reference
	private EvcsPower evcsPower;


	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	private Config config = null;

	public ChargexAqueductImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ModbusComponent.ChannelId.values(), //
				Evcs.ChannelId.values(), //
				ManagedEvcs.ChannelId.values(), //
				ChargexAqueduct.ChannelId.values()
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		if(super.activate(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId(), this.cm, "Modbus",
				config.modbus_id())) {
			return;
		}
		this.config = config;
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
			    new FC3ReadRegistersTask(0x0002, Priority.LOW,
			        m(ChargexAqueduct.ChannelId.UNIT_ID, new UnsignedDoublewordElement(0x0002))),
			    
			    new FC3ReadRegistersTask(0x0004, Priority.LOW,
			        m(ChargexAqueduct.ChannelId.MANUFACTURER, new StringWordElement(0x0004, 8))),
			    
			    new FC3ReadRegistersTask(0x000C, Priority.LOW,
			        m(ChargexAqueduct.ChannelId.DEVICE_TYPE, new StringWordElement(0x000C, 8))),
			    
			    new FC3ReadRegistersTask(0x0014, Priority.LOW,
			        m(ChargexAqueduct.ChannelId.FW_VERSION, new StringWordElement(0x0014, 8))),
			    
			    new FC3ReadRegistersTask(0x001C, Priority.LOW,
			        m(ChargexAqueduct.ChannelId.SERIAL_NR, new StringWordElement(0x001C, 8))),

			    // Overall System Status - High Priority
			    new FC3ReadRegistersTask(0x0024, Priority.HIGH,
			        m(ElectricityMeter.ChannelId.ACTIVE_POWER, new UnsignedDoublewordElement(0x0024)),
			        m(ChargexAqueduct.ChannelId.PAC_MINIMUM, new UnsignedDoublewordElement(0x0026)),
			        m(ElectricityMeter.ChannelId.CURRENT_L1, new UnsignedDoublewordElement(0x0028)),
			        m(ElectricityMeter.ChannelId.CURRENT_L2, new UnsignedDoublewordElement(0x002A)),
			        m(ElectricityMeter.ChannelId.CURRENT_L3, new UnsignedDoublewordElement(0x002C)),
			        m(ChargexAqueduct.ChannelId.CONNECTED_CARS, new UnsignedDoublewordElement(0x002E)),
			        m(ChargexAqueduct.ChannelId.ACTIVE_SESSIONS, new UnsignedDoublewordElement(0x0030)),
			        m(ChargexAqueduct.ChannelId.N_MODULES, new UnsignedDoublewordElement(0x0032)),
			        m(ChargexAqueduct.ChannelId.SYSTEM_STATUS, new UnsignedDoublewordElement(0x0034)),
			        m(ChargexAqueduct.ChannelId.MAX_CURRENT, new UnsignedDoublewordElement(0x0036))),

			    // Per Module Information - Module 0
			    new FC3ReadRegistersTask(0x0064, Priority.HIGH,
			        m(ChargexAqueduct.ChannelId.PAC_MODULE_0, new UnsignedDoublewordElement(0x0064)),
			        m(ChargexAqueduct.ChannelId.IAC_L1_MODULE_0, new UnsignedDoublewordElement(0x0066)),
			        m(ChargexAqueduct.ChannelId.IAC_L2_MODULE_0, new UnsignedDoublewordElement(0x0068)),
			        m(ChargexAqueduct.ChannelId.IAC_L3_MODULE_0, new UnsignedDoublewordElement(0x006A)),
			        m(ChargexAqueduct.ChannelId.STATES_CP_MODULE_0, new UnsignedWordElement(0x006C)),
			        m(ChargexAqueduct.ChannelId.ERRORS_CP_MODULE_0, new UnsignedWordElement(0x006E))),

			    // Per Module Information - Module 1
			    new FC3ReadRegistersTask(0x0066, Priority.LOW,
			        m(ChargexAqueduct.ChannelId.PAC_MODULE_1, new UnsignedDoublewordElement(0x0066)),
			        m(ChargexAqueduct.ChannelId.IAC_L1_MODULE_1, new UnsignedDoublewordElement(0x0068)),
			        m(ChargexAqueduct.ChannelId.IAC_L2_MODULE_1, new UnsignedDoublewordElement(0x006A)),
			        m(ChargexAqueduct.ChannelId.IAC_L3_MODULE_1, new UnsignedDoublewordElement(0x006C)),
			        m(ChargexAqueduct.ChannelId.STATES_CP_MODULE_1, new UnsignedWordElement(0x006E)),
			        m(ChargexAqueduct.ChannelId.ERRORS_CP_MODULE_1, new UnsignedWordElement(0x0070))),

			    // Per Module Information - Module 2
			    new FC3ReadRegistersTask(0x0068, Priority.LOW,
			        m(ChargexAqueduct.ChannelId.PAC_MODULE_2, new UnsignedDoublewordElement(0x0068)),
			        m(ChargexAqueduct.ChannelId.IAC_L1_MODULE_2, new UnsignedDoublewordElement(0x006A)),
			        m(ChargexAqueduct.ChannelId.IAC_L2_MODULE_2, new UnsignedDoublewordElement(0x006C)),
			        m(ChargexAqueduct.ChannelId.IAC_L3_MODULE_2, new UnsignedDoublewordElement(0x006E)),
			        m(ChargexAqueduct.ChannelId.STATES_CP_MODULE_2, new UnsignedWordElement(0x0070)),
			        m(ChargexAqueduct.ChannelId.ERRORS_CP_MODULE_2, new UnsignedWordElement(0x0072))),

			    // Per Module Information - Module 3
			    new FC3ReadRegistersTask(0x006A, Priority.LOW,
			        m(ChargexAqueduct.ChannelId.PAC_MODULE_3, new UnsignedDoublewordElement(0x006A)),
			        m(ChargexAqueduct.ChannelId.IAC_L1_MODULE_3, new UnsignedDoublewordElement(0x006C)),
			        m(ChargexAqueduct.ChannelId.IAC_L2_MODULE_3, new UnsignedDoublewordElement(0x006E)),
			        m(ChargexAqueduct.ChannelId.IAC_L3_MODULE_3, new UnsignedDoublewordElement(0x0070)),
			        m(ChargexAqueduct.ChannelId.STATES_CP_MODULE_3, new UnsignedWordElement(0x0072)),
			        m(ChargexAqueduct.ChannelId.ERRORS_CP_MODULE_3, new UnsignedWordElement(0x0074))),

			    // Readable Configuration - Low Priority
			    new FC3ReadRegistersTask(0x01F4, Priority.LOW,
			        m(ChargexAqueduct.ChannelId.PAC_TARGET_TIMEOUT, new UnsignedDoublewordElement(0x01F4)),
			        m(ChargexAqueduct.ChannelId.PAC_DEFAULT_POWER, new UnsignedDoublewordElement(0x01F6)),
			        m(ChargexAqueduct.ChannelId.PAC_TARGET_POWER, new UnsignedDoublewordElement(0x01F8)),
			        m(ChargexAqueduct.ChannelId.CHARGING_MODE, new UnsignedDoublewordElement(0x01FA))),

			    // Write Tasks for Control
			    new FC16WriteRegistersTask(0x01F4,
			        m(ChargexAqueduct.ChannelId.SET_PAC_TARGET_TIMEOUT, new UnsignedDoublewordElement(0x01F4))),

			    new FC16WriteRegistersTask(0x01F6,
			        m(ChargexAqueduct.ChannelId.SET_PAC_DEFAULT_POWER, new UnsignedDoublewordElement(0x01F6))),

			    new FC16WriteRegistersTask(0x01F8,
			        m(ChargexAqueduct.ChannelId.SET_PAC_TARGET_POWER, new UnsignedDoublewordElement(0x01F8))),

			    new FC16WriteRegistersTask(0x01FA,
			        m(ChargexAqueduct.ChannelId.SET_CHARGING_MODE, new UnsignedDoublewordElement(0x01FA)))
			);
		return modbusProtocol;
	}

	@Override
	public String debugLog() {
		return "Limit:" + this.getSetChargePowerLimit().orElse(null) + "|" + this.getStatus().getName();
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
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean pauseChargeProcess() throws Exception {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean applyDisplayText(String text) throws OpenemsException {
		return false;
	}

	@Override
	public int getMinimumTimeTillChargingLimitTaken() {
		// TODO Auto-generated method stub
		return 0;
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
}
