/**
 * This class is part of the Programming the Internet of Things project.
 * 
 * It is provided as a simple shell to guide the student and assist with
 * implementation for the Programming the Internet of Things exercises,
 * and designed to be modified by the student as needed.
 */ 

 package programmingtheiot.gda.connection;

 import java.util.List;
 import java.util.Queue;
 import java.util.concurrent.ArrayBlockingQueue;
 import java.util.logging.Level;
 import java.util.logging.Logger;
 
 import org.eclipse.californium.core.CoapResource;
 import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.config.CoapConfig;
import org.eclipse.californium.core.network.Endpoint;
 import org.eclipse.californium.core.network.interceptors.MessageTracer;
 import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.elements.config.UdpConfig;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.IActuatorDataListener;
import programmingtheiot.common.IDataMessageListener;
 import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.gda.connection.handlers.GetActuatorCommandResourceHandler;
import programmingtheiot.gda.connection.handlers.UpdateSystemPerformanceResourceHandler;
import programmingtheiot.gda.connection.handlers.UpdateTelemetryResourceHandler;

/**
 * Shell representation of class for student implementation.
 * 
 */
public class CoapServerGateway
{
	// static
	static {
		CoapConfig.register();
		UdpConfig.register();
	}
	
	private static final Logger _Logger =
		Logger.getLogger(CoapServerGateway.class.getName());
	
	// params
	
	private CoapServer coapServer = null;
	
	private IDataMessageListener dataMsgListener = null;
	
	
	// constructors
	
	/**
	 * Constructor.
	 * 
	 * @param dataMsgListener
	 */
	public CoapServerGateway(IDataMessageListener dataMsgListener)
	{
		super();
		
		/*
		 * Basic constructor implementation provided. Change as needed.
		 */
		
		this.dataMsgListener = dataMsgListener;
		
		initServer(
		ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE,
		ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE,
		ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE
	);
	}

		
	// public methods
	
	public void addResource(ResourceNameEnum resource)
	{
	}
	
	public boolean hasResource(String name)
	{
		return false;
	}
	
	public void setDataMessageListener(IDataMessageListener listener)
	{
		if (listener != null) {
			this.dataMsgListener = listener;
		}
	}
	
	public boolean startServer()
	{
		try {
			if (this.coapServer != null) {
				this.coapServer.start();

				// for message logging
				for (Endpoint ep : this.coapServer.getEndpoints()) {
					ep.addInterceptor(new MessageTracer());
				}

				return true;
			} else {
				_Logger.warning("CoAP server START failed. Not yet initialized.");
			}
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to start CoAP server.", e);
		}

		return false;
	}

	public boolean stopServer()
	{
		try {
			if (this.coapServer != null) {
				this.coapServer.stop();

				return true;
			} else {
				_Logger.warning("CoAP server STOP failed. Not yet initialized.");
			}
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to stop CoAP server.", e);
		}

		return false;
	}
		
	
	// private methods
	
	private Resource createResourceChain(ResourceNameEnum resourceEnum)
	{
		if (resourceEnum == null) return null;

		String fullPath = resourceEnum.getResourceName();
		String[] pathSegments = fullPath.split("/");

		if (pathSegments.length == 0) return null;

		CoapResource root = new CoapResource(pathSegments[0]);
		CoapResource current = root;

		for (int i = 1; i < pathSegments.length - 1; i++) {
			CoapResource child = new CoapResource(pathSegments[i]);
			current.add(child);
			current = child;
		}

		// final segment is the actual leaf resource
		String leafName = pathSegments[pathSegments.length - 1];

		CoapResource handler = null;

		switch (resourceEnum) {
			case CDA_SYSTEM_PERF_MSG_RESOURCE:
				handler = new UpdateSystemPerformanceResourceHandler(leafName);
				((UpdateSystemPerformanceResourceHandler) handler).setDataMessageListener(this.dataMsgListener);
				break;

			case CDA_SENSOR_MSG_RESOURCE:
				handler = new UpdateTelemetryResourceHandler(leafName);
				((UpdateTelemetryResourceHandler) handler).setDataMessageListener(this.dataMsgListener);
				break;

			case CDA_ACTUATOR_CMD_RESOURCE:
				handler = new GetActuatorCommandResourceHandler(leafName);
				// aquí puedes vincular al DeviceDataManager si fuera necesario
				break;

			default:
				_Logger.warning("Unknown resource enum: " + resourceEnum.name());
				break;
		}

		if (handler != null) {
			current.add(handler);
			return root;
		} else {
			return null;
		}
	}


	
	private void initServer(ResourceNameEnum... resources)
	{
		try {
			this.coapServer = new CoapServer(ConfigConst.DEFAULT_COAP_PORT);

			// Recursos predeterminados si no se pasan como argumentos
			if (resources == null || resources.length == 0) {
				resources = new ResourceNameEnum[] {
					ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE,
					ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE,
					ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE
				};
			}

			for (ResourceNameEnum resource : resources) {
				Resource coapResource = createResourceChain(resource);
				if (coapResource != null) {
					this.coapServer.add(coapResource);
					_Logger.info("Added CoAP resource: " + resource.getResourceName());
				} else {
					_Logger.warning("Failed to create CoAP resource: " + resource.getResourceName());
				}
			}
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Exception during CoAP server initialization.", e);
		}
	}


}
