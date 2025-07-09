package io.openems.edge.controller.revoletion;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "EVSE Controller with REVOL-E-TION", //
		description = "Controller with REVOL-E-TION")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "ctrlRevoletion0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "REVOL-E-TION CTRL Host", description = "Host where the REVOL-E-TION control server can be reached.")
	String server_host();

	@AttributeDefinition(name = "REVOL-E-TION CTRL Port", description = "Port where the REVOL-E-TION control server can be reached")
	int server_port() default 8080;

	@AttributeDefinition(name = "Evcs-IDs", description = "ID of Evcs device", required = true)
	String[] evcs_ids() default { "evcs0"};

	@AttributeDefinition(name = "Enabled charging", description = "Activates or deactivates the Charging.")
	boolean enabledCharging() default true;

	String webconsole_configurationFactory_nameHint() default "Controller REVOL-E-TION [{id}]";

}