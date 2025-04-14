package programmingtheiot.gda.connection.handlers;

import java.util.logging.Logger;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.server.resources.CoapExchange;

import programmingtheiot.common.IActuatorDataListener;
import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.DataUtil;

public class GetActuatorCommandResourceHandler extends CoapResource
	implements IActuatorDataListener
{
	// Logger
	private static final Logger _Logger =
		Logger.getLogger(GetActuatorCommandResourceHandler.class.getName());

	// Últimos datos del actuador
	private ActuatorData actuatorData = new ActuatorData();

	// Constructor
	public GetActuatorCommandResourceHandler(String resourceName)
	{
		super(resourceName);
		super.setObservable(true);
		_Logger.info("Observable CoAP resource created: " + resourceName);
	}

	// Implementación del listener de actualización
	@Override
	public boolean onActuatorDataUpdate(ActuatorData data)
	{
		if (data != null && this.actuatorData != null) {
			this.actuatorData.updateData(data);
			super.changed();

			_Logger.fine("Actuator data updated for URI: " + super.getURI()
				+ " | Data value = " + this.actuatorData.getValue());

			return true;
		}
		return false;
	}

	// Solo se necesita override para GET
	@Override
	public void handleGET(CoapExchange context)
	{
		// Aceptar la solicitud
		context.accept();
		_Logger.info("GET received on: " + super.getURI());

		// Convertir ActuatorData a JSON
		String jsonData = DataUtil.getInstance().actuatorDataToJson(this.actuatorData);

		// Responder con código 2.05 CONTENT
		context.respond(ResponseCode.CONTENT, jsonData);
	}
}

