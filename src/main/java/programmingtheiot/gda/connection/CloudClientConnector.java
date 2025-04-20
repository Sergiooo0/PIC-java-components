/**
 * This class is part of the Programming the Internet of Things project.
 * 
 * It is provided as a simple shell to guide the student and assist with
 * implementation for the Programming the Internet of Things exercises,
 * and designed to be modified by the student as needed.
 */ 

package programmingtheiot.gda.connection;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SensorData;
import programmingtheiot.data.SystemPerformanceData;

/**
 * Shell representation of class for student implementation.
 *
 */
public class CloudClientConnector implements ICloudClient, IConnectionListener
{
	// static
	
	private static final Logger _Logger =
		Logger.getLogger(CloudClientConnector.class.getName());
	
	// private var's
	private String topicPrefix = "";
	private MqttClientConnector mqttClient = null;
	private IDataMessageListener dataMsgListener = null;

	private int qosLevel = ConfigUtil.getInstance().getInteger(
		ConfigConst.CLOUD_GATEWAY_SERVICE, 
		ConfigConst.DEFAULT_QOS_KEY, 
		ConfigConst.DEFAULT_QOS);
	
	// constructors
	
	/**
	 * Default.
	 * 
	 */
	public CloudClientConnector() {
		super();
		ConfigUtil configUtil = ConfigUtil.getInstance();
	
		this.topicPrefix = configUtil.getProperty(
			ConfigConst.CLOUD_GATEWAY_SERVICE,
			ConfigConst.BASE_TOPIC_KEY
		);
	
		// Depending on the cloud service, the topic names may or may not begin with a "/", 
		if (topicPrefix == null) {
			topicPrefix = "/";
		} else {
			if (!topicPrefix.endsWith("/")) {
				topicPrefix += "/";
			}
		}
	}
	
	
	// public methods
	
	@Override
	public boolean connectClient() {
		if (this.mqttClient == null) {
			// TODO: either line should work with recent updates to `MqttClientConnector`
			// this.mqttClient = new MqttClientConnector(true);
			this.mqttClient = new MqttClientConnector(ConfigConst.CLOUD_GATEWAY_SERVICE);
			this.mqttClient.setConnectionListener(this);
		}
		return this.mqttClient.connectClient();
	}

	@Override
	public boolean disconnectClient()
	{	
		if (this.mqttClient == null) {
			_Logger.warning("No MQTT client to disconnect.");
			return false;
		}
		if (!this.mqttClient.isConnected()) {
			_Logger.warning("No MQTT client connected.");
			return true;
		}
		if (this.mqttClient != null && this.mqttClient.isConnected()) {
			boolean result = this.mqttClient.disconnectClient();
			if (result) {
				_Logger.info("Disconnected from cloud service.");
			} else {
				_Logger.warning("Failed to disconnect from cloud service.");
			}
			return result;
		}

		return false;
	}

	@Override
	public boolean setDataMessageListener(IDataMessageListener listener)
	{
		if (listener != null) {
			this.dataMsgListener = listener;
			//this.mqttClient.setDataMessageListener(listener);
			return true;
		}
		return false;
	}

	@Override
	public boolean sendEdgeDataToCloud(ResourceNameEnum resource, SensorData data)
	{
		if (resource != null && data != null) {
			_Logger.info("Sending sensor data to cloud: " + data.toString());
			String payload = DataUtil.getInstance().sensorDataToJson(data);

			return publishMessageToCloud(resource, data.getName(), payload);
		}

		return false;
	}

	@Override
	public boolean sendEdgeDataToCloud(ResourceNameEnum resource, SystemPerformanceData data)
	{
		if (resource != null && data != null) {
			// send the reading as a SensorData representation
			SensorData cpuData = new SensorData();
			cpuData.updateData(data);
			cpuData.setName(ConfigConst.CPU_UTIL_NAME);
			cpuData.setValue(data.getCpuUtilization());

			boolean cpuDataSuccess = sendEdgeDataToCloud(resource, cpuData);

			if (! cpuDataSuccess) {
				_Logger.warning("Failed to send CPU utilization data to cloud service.");
			}

			// send the reading as a SensorData representation
			SensorData memData = new SensorData();
			memData.updateData(data);
			memData.setName(ConfigConst.MEM_UTIL_NAME);
			memData.setValue(data.getMemoryUtilization());

			boolean memDataSuccess = sendEdgeDataToCloud(resource, memData);

			if (! memDataSuccess) {
				_Logger.warning("Failed to send memory utilization data to cloud service.");
			}

			return (cpuDataSuccess == memDataSuccess);
		}

		return false;
	}

	@Override
	public boolean subscribeToCloudEvents(ResourceNameEnum resource)
	{
		boolean success = false;

		String topicName = null;

		if (this.mqttClient != null && this.mqttClient.isConnected()) {
			topicName = createTopicName(resource);

			// NOTE: This is a generic subscribe call - if you use this approach,
			// you will need to update this.mqttClient.messageReceived() to
			//   (1) identify the message source (e.g., CDA or Cloud),
			//   (2) determine the message type (e.g., actuator command), and
			//   (3) convert the payload into a data container (e.g., ActuatorData)
			//
			// Once you determine the message source and type, and convert the
			// payload to its appropriate data container, you can then determine
			// where to route the message (e.g., send to the IDataMessageListener
			// instance (which will be DeviceDataManager).
			this.mqttClient.subscribeToTopic(topicName, this.qosLevel);

			success = true;
		} else {
			_Logger.warning("Subscription methods only available for MQTT. No MQTT connection to broker. Ignoring. Topic: " + topicName);
		}

		return success;
	}

	@Override
	public boolean unsubscribeFromCloudEvents(ResourceNameEnum resource)
	{
		boolean success = false;
	
		String topicName = null;
	
		if (this.mqttClient != null && this.mqttClient.isConnected()) {
			topicName = createTopicName(resource);
	
			this.mqttClient.unsubscribeFromTopic(topicName);
	
			success = true;
		} else {
			_Logger.warning("Unsubscribe method only available for MQTT. No MQTT connection to broker. Ignoring. Topic: " + topicName);
		}
	
		return success;
	}

	// public methods - IConnectionListener
	@Override
	public void onConnect()
	{
		_Logger.info("Handling CSP subscriptions and device topic provisioninig...");

		LedEnablementMessageListener ledListener = new LedEnablementMessageListener(this.dataMsgListener);

		// topic may not exist yet, so create a 'response' actuation event with invalid value -
		// this will create the relevant topic if it doesn't yet exist, which ensures
		// the message listener (if coded correctly) will log a message but ignore the
		// actuation command and NOT pass it onto the IDataMessageListener instance
		ActuatorData ad = new ActuatorData();
		ad.setAsResponse();
		ad.setName(ConfigConst.LED_ACTUATOR_NAME);
		ad.setValue((float) -1.0); // NOTE: this just needs to be an invalid actuation value

		String ledTopic = createTopicName(ledListener.getResource().getDeviceName(), ad.getName());
		String adJson = DataUtil.getInstance().actuatorDataToJson(ad);

		this.publishMessageToCloud(ledTopic, adJson);

		this.mqttClient.subscribeToTopic(ledTopic, this.qosLevel, ledListener);
	}

	@Override
	public void onDisconnect()
	{
		_Logger.info("MQTT client disconnected. Nothing else to do.");
	}
	
	
	// private methods
	private String createTopicName(ResourceNameEnum resource)
	{
		return createTopicName(resource.getDeviceName(), resource.getResourceType());
	}

	private String createTopicName(ResourceNameEnum resource, String itemName)
	{
		return (createTopicName(resource) + "-" + itemName).toLowerCase();
	}
	
	private String createTopicName(String deviceName, String resourceTypeName)
	{
		StringBuilder buf = new StringBuilder();
	
		if (deviceName != null && deviceName.trim().length() > 0) {
			buf.append(topicPrefix).append(deviceName);
		}
	
		if (resourceTypeName != null && resourceTypeName.trim().length() > 0) {
			buf.append('/').append(resourceTypeName);
		}
	
		return buf.toString().toLowerCase();
	}
	
	private boolean publishMessageToCloud(ResourceNameEnum resource, String itemName, String payload) {
		String topicName = createTopicName(resource) + "-" + itemName;
		return publishMessageToCloud(topicName, payload);
	}
	
	private boolean publishMessageToCloud(String topicName, String payload) {
		try {
			_Logger.info("Publishing payload value(s) to CSP: " + topicName);

			if (this.mqttClient == null || !this.mqttClient.isConnected()) {
				_Logger.warning("No MQTT client connected.");
			}
	
			this.mqttClient.publishMessage(topicName, payload.getBytes(), this.qosLevel);
			_Logger.info("Published payload value(s) to CSP: " + topicName);
	
			// NOTE: Depending on the cloud service, it may be necessary to 'throttle'
			// the published messages by limiting to, for example, no more than one
			// per second. While there are a variety of ways to accomplish this,
			// briefly described below are two techniques that may be worth considering
			// if this is a limitation you need to handle in your code:
			//
			// 1) Add an artificial delay after the call to this.mqttClient.publishMessage().
			//    This can be implemented by sleeping for up to a second after the call.
			//    However, it can also adversely affect the program flow, as this sleep
			//    will block DeviceDataManager, which invoked one of the sendEdgeDataToCloud()
			//    methods that led to this call, and may negatively impact your application.
			//
			// 2) Implement a Queue which can store both the payload and target topic, and
			//    add a scheduler to pop the oldest message off the Queue (when not empty)
			//    at a regular interval (for example, once per second), and then invoke the
			//    this.mqttClient.publishMessage() method.
			//
			// Both approaches require thoughtful design considerations of course, and your
			// requirements may demand an alternative approach (or none at all if throttling
			// isn't a concern). Design and implementation details are left up to you.
	
			return true;
		} catch (Exception e) {
			_Logger.warning("Failed to publish message to CSP: " + topicName + " - " + e);
			_Logger.log(Level.FINE, "Exception: ", e);
		}
	
		return false;
	}
	
}

class LedEnablementMessageListener implements IMqttMessageListener
{
	private static final Logger _Logger =
		Logger.getLogger(LedEnablementMessageListener.class.getName());
	
	private IDataMessageListener dataMsgListener = null;

	private ResourceNameEnum resource = ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE;

	private int    typeID   = ConfigConst.LED_ACTUATOR_TYPE;
	private String itemName = ConfigConst.LED_ACTUATOR_NAME;

	LedEnablementMessageListener(IDataMessageListener dataMsgListener)
	{
		this.dataMsgListener = dataMsgListener;
	}

	public ResourceNameEnum getResource()
	{
		return this.resource;
	}

	@Override
	public void messageArrived(String topic, MqttMessage message) throws Exception
	{
		try {
			String jsonData = new String(message.getPayload());

			ActuatorData actuatorData =
				DataUtil.getInstance().jsonToActuatorData(jsonData);

			actuatorData.setLocationID(ConfigConst.CONSTRAINED_DEVICE);
			actuatorData.setTypeID(this.typeID);
			actuatorData.setName(this.itemName);

			int val = (int) actuatorData.getValue();

			switch (val) {
				case ConfigConst.ON_COMMAND:
					_Logger.info("Received LED enablement message [ON].");
					actuatorData.setStateData("LED switching ON");
					break;

				case ConfigConst.OFF_COMMAND:
					_Logger.info("Received LED enablement message [OFF].");
					actuatorData.setStateData("LED switching OFF");
					break;

				default:
					return;
			}
			// Option 2: using ActuatorData
			
			if (this.dataMsgListener != null) {
				this.dataMsgListener.handleActuatorCommandRequest(
					ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE, actuatorData);
			}
		} catch (Exception e) {
			_Logger.warning("Failed to convert message payload to ActuatorData.");
		}
	}

}
