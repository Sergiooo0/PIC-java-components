package programmingtheiot.gda.connection;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.interceptors.MessageTracer;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.elements.config.UdpConfig;
import org.eclipse.californium.core.config.CoapConfig;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.IActuatorDataListener;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.gda.connection.handlers.GetActuatorCommandResourceHandler;
import programmingtheiot.gda.connection.handlers.UpdateSystemPerformanceResourceHandler;
import programmingtheiot.gda.connection.handlers.UpdateTelemetryResourceHandler;

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
	private boolean isServerInitialized = false;

	// constructors
	public CoapServerGateway(IDataMessageListener dataMsgListener)
	{
		super();
		this.dataMsgListener = dataMsgListener;
		initServer();
	}
	
	// public methods
	public void addResource(ResourceNameEnum resourceType, String endName, Resource resource)
	{
		if (resourceType != null && resource != null) {
			createAndAddResourceChain(resourceType, resource);
		}
	}
	
	public boolean hasResource(String name)
	{
		return this.coapServer != null && this.coapServer.getRoot().getChild(name) != null;
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
			if (!this.isServerInitialized) {
				initServer();
			}

			if (this.coapServer != null) {
				this.coapServer.start();

				// for message logging
				for (Endpoint ep : this.coapServer.getEndpoints()) {
					ep.addInterceptor(new MessageTracer());
				}

				_Logger.info("CoAP server started.");
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
				_Logger.info("CoAP server stopped.");
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
	private void initServer()
	{
		this.coapServer = new CoapServer(ConfigConst.DEFAULT_COAP_PORT);
		initDefaultResources();
		this.isServerInitialized = true;
	}

	private void initDefaultResources()
	{
		// System Performance
		UpdateSystemPerformanceResourceHandler sysPerfHandler =
			new UpdateSystemPerformanceResourceHandler(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE.getResourceType());
		sysPerfHandler.setDataMessageListener(this.dataMsgListener);
		addResource(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE, null, sysPerfHandler);

		// Telemetry
		UpdateTelemetryResourceHandler telemetryHandler =
			new UpdateTelemetryResourceHandler(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE.getResourceType());
		telemetryHandler.setDataMessageListener(this.dataMsgListener);
		addResource(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, null, telemetryHandler);

		// Actuator Command
		GetActuatorCommandResourceHandler actuatorHandler =
			new GetActuatorCommandResourceHandler(ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE.getResourceType());
		addResource(ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE, null, actuatorHandler);

		if (this.dataMsgListener != null) {
			this.dataMsgListener.setActuatorDataListener(null, actuatorHandler);
		}
	}

	private void createAndAddResourceChain(ResourceNameEnum resourceType, Resource resource)
	{
		_Logger.info("Adding server resource handler chain: " + resourceType.getResourceName());

		List<String> resourceNames = resourceType.getResourceNameChain();
		Queue<String> queue = new ArrayBlockingQueue<>(resourceNames.size());
		queue.addAll(resourceNames);

		Resource parentResource = this.coapServer.getRoot();
		if (parentResource == null) {
			parentResource = new CoapResource(queue.poll());
			this.coapServer.add(parentResource);
		}

		while (!queue.isEmpty()) {
			String resourceName = queue.poll();
			Resource nextResource = parentResource.getChild(resourceName);

			if (nextResource == null) {
				if (queue.isEmpty()) {
					nextResource = resource;
					nextResource.setName(resourceName);
				} else {
					nextResource = new CoapResource(resourceName);
				}
				parentResource.add(nextResource);
			}

			parentResource = nextResource;
		}
	}
}
