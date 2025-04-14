package programmingtheiot.gda.connection.handlers;

import java.util.logging.Logger;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.server.resources.CoapExchange;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SystemPerformanceData;

public class UpdateSystemPerformanceResourceHandler extends GenericCoapResourceHandler
{
	private static final Logger _Logger =
		Logger.getLogger(UpdateSystemPerformanceResourceHandler.class.getName());

	private IDataMessageListener dataMsgListener = null;

	public UpdateSystemPerformanceResourceHandler(String resourceName)
	{
		super(resourceName);
	}

	public void setDataMessageListener(IDataMessageListener listener)
	{
		if (listener != null) {
			this.dataMsgListener = listener;
		}
	}

	@Override
	public void handlePUT(CoapExchange context)
	{
		ResponseCode code = ResponseCode.NOT_ACCEPTABLE;
		context.accept();

		if (this.dataMsgListener != null) {
			try {
				String jsonData = new String(context.getRequestPayload());

				SystemPerformanceData sysPerfData =
					DataUtil.getInstance().jsonToSystemPerformanceData(jsonData);

				this.dataMsgListener.handleSystemPerformanceMessage(
					ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE, sysPerfData);

				code = ResponseCode.CHANGED;
			} catch (Exception e) {
				_Logger.warning("Failed to handle PUT request: " + e.getMessage());
				code = ResponseCode.BAD_REQUEST;
			}
		} else {
			_Logger.info("No callback listener set.");
			code = ResponseCode.CONTINUE;
		}

		context.respond(code, "PUT handled for: " + super.getName());
	}

	@Override
	public void handleGET(CoapExchange context)
	{
		context.accept();
		_Logger.info("GET request received on: " + super.getName());
		context.respond(ResponseCode.CONTENT, "GET not implemented.");
	}

	@Override
	public void handlePOST(CoapExchange context)
	{
		context.accept();
		_Logger.info("POST request received on: " + super.getName());
		context.respond(ResponseCode.METHOD_NOT_ALLOWED, "POST not supported.");
	}

	@Override
	public void handleDELETE(CoapExchange context)
	{
		context.accept();
		_Logger.info("DELETE request received on: " + super.getName());
		context.respond(ResponseCode.DELETED);
	}
}
