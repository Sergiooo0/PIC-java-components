/**
 * This class is part of the Programming the Internet of Things project.
 * 
 * It is provided as a simple shell to guide the student and assist with
 * implementation for the Programming the Internet of Things exercises,
 * and designed to be modified by the student as needed.
 */ 

package programmingtheiot.gda.app;

import java.util.logging.Level;
import java.util.logging.Logger;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IActuatorDataListener;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SensorData;
import programmingtheiot.data.SystemPerformanceData;
import programmingtheiot.data.SystemStateData;
import programmingtheiot.gda.connection.CoapServerGateway;
import programmingtheiot.gda.connection.IPersistenceClient;
import programmingtheiot.gda.connection.IPubSubClient;
import programmingtheiot.gda.connection.IRequestResponseClient;
import programmingtheiot.gda.connection.MqttClientConnector;
import programmingtheiot.gda.connection.RedisPersistenceAdapter;
import programmingtheiot.gda.system.SystemPerformanceManager;
import redis.clients.jedis.JedisPubSub;

/**
 * Shell representation of class for student implementation.
 *
 */
public class DeviceDataManager extends JedisPubSub implements IDataMessageListener
{
	// static
	
	private static final Logger _Logger =
		Logger.getLogger(DeviceDataManager.class.getName());
	
	// private var's

	private DataUtil dataUtil = DataUtil.getInstance();
	
	private boolean enableMqttClient = true;
	private boolean enableCoapServer = false;
	private boolean enableCloudClient = false;
	private boolean enableSmtpClient = false;
	private boolean enablePersistenceClient = false;
	private boolean enableSystemPerf = false;
	
	private IActuatorDataListener actuatorDataListener = null;
	private IPubSubClient mqttClient = null;
	private IPubSubClient cloudClient = null;
	private IPersistenceClient persistenceClient = null;
	private IRequestResponseClient smtpClient = null;
	private CoapServerGateway coapServer = null;
	private RedisPersistenceAdapter redisClient = null;
	private SystemPerformanceManager sysPerfMgr = null;
	
	// constructors
	
	public DeviceDataManager()
	{
		super();

		ConfigUtil configUtil = ConfigUtil.getInstance();

		this.enableMqttClient =
			configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_MQTT_CLIENT_KEY);

		this.enableCoapServer =
			configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_COAP_SERVER_KEY);

		this.enableCloudClient =
			configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_CLOUD_CLIENT_KEY);

		this.enablePersistenceClient =
			configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_PERSISTENCE_CLIENT_KEY);

		initConnections();
	}
	
	public DeviceDataManager(
		boolean enableMqttClient,
		boolean enableCoapClient,
		boolean enableCloudClient,
		boolean enableSmtpClient,
		boolean enablePersistenceClient)
	{
		super();
		
		initConnections();
	}
	
	
	// public methods
	
	@Override
	public boolean handleActuatorCommandResponse(ResourceNameEnum resourceName, ActuatorData data)
	{
		_Logger.info("Handling actuator command response for resource: " + resourceName.toString());
		if (data != null) {
			_Logger.info("Handling actuator command response");
			if (data.hasError()) {
				_Logger.log(Level.WARNING, "Received actuator with error of status code: {0}", data.getStatusCode());
			}
			return true;
		} 
		return false;	
	}

	@Override
	public boolean handleActuatorCommandRequest(ResourceNameEnum resourceName, ActuatorData data)
	{
		if(data != null) {
			_Logger.info("Handling actuator command request for resource: " + data.getName());
		
			if (data.hasError()) {
				_Logger.log(Level.WARNING, "Received actuator with error of status code: {0}", data.getStatusCode());
			} else {
				if (this.redisClient != null) {
					this.redisClient.storeData(data.getName(), 0, data);
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean handleIncomingMessage(ResourceNameEnum resourceName, String msg)
	{
		_Logger.info("Handling incoming message for resource: " + resourceName.toString());
		if (msg != null) {
			_Logger.info("Handling incoming message: " + msg);
			return true;
		}
		return false;
	}

	@Override
	public boolean handleSensorMessage(ResourceNameEnum resourceName, SensorData data)
	{
		_Logger.info("Handling sensor message for resource: " + resourceName.toString());
		if (data != null) {
			_Logger.info("Handling sensor message");
			if (data.hasError()) {
				_Logger.log(Level.WARNING, "Received sensor with error of status code: {0}", data.getStatusCode());
			} else {
				if (this.redisClient != null) {
					this.redisClient.storeData(data.getName(), 0, data);
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean handleSystemPerformanceMessage(ResourceNameEnum resourceName, SystemPerformanceData data)
	{
		_Logger.info("Handling system performance message for resource: " + resourceName.toString());
		if (data != null) {
			_Logger.info("Handling system performance message");
			if (data.hasError()) {
				_Logger.log(Level.WARNING, "Received system performance with error of status code: {0}", data.getStatusCode());
			}
			// transform the data into a JSON string with DataUtil
			String json = dataUtil.systemPerformanceDataToJson(data);
			_Logger.info("Handled system performance message");
			return true;
		}
		return false;
	}
	
	public void setActuatorDataListener(String name, IActuatorDataListener listener)
	{
	}
	
	public void startManager()
	{
		_Logger.info("Starting device data manager.");
		if (this.sysPerfMgr != null) {
			_Logger.info("Starting system performance manager.");
			this.sysPerfMgr.startManager();
		}
		if (this.redisClient != null) {
			_Logger.info("Starting Redis client.");
			this.redisClient.connectClient();
			this.redisClient.subscribeToChannel(this, ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE);
		}
		if (this.mqttClient != null) {
			if (this.mqttClient.connectClient()){
				_Logger.info("Starting MQTT client.");
				int qos = ConfigUtil.getInstance().getInteger(
					ConfigConst.GATEWAY_DEVICE, ConfigConst.DEFAULT_QOS_KEY);
				this.mqttClient.subscribeToTopic(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE, qos);
				this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE, qos);
				this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, qos);
				this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE, qos);
			} else {
				_Logger.warning("Unable to connect to MQTT broker. MQTT client will not be started.");
				// TODO: handle this case
				throw new RuntimeException("Unable to connect to MQTT broker. MQTT client will not be started.");

			}
		} 
	}
	
	public void stopManager()
	{
		_Logger.info("Stopping device data manager.");
		if (this.sysPerfMgr != null) {
			_Logger.log(Level.INFO, "Stopping system performance manager.");
			this.sysPerfMgr.stopManager();
		}
		if (this.redisClient != null) {
			_Logger.info("Stopping Redis client.");
			this.redisClient.unsubscribeFromChannel(this);
			this.redisClient.disconnectClient();
		}
		if (this.mqttClient != null) {
			_Logger.info("Stopping MQTT client.");
			this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE);
			this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE);
			this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE);
			this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE);
			if (this.mqttClient.disconnectClient()) {
				_Logger.info("Successfully disconnected MQTT client from broker.");
			} else {
				_Logger.severe("Failed to disconnect MQTT client from broker.");
				// TODO: handle this case
				throw new RuntimeException("Failed to disconnect MQTT client from broker.");
			}

		}
	}

	// JedisPubSub methods

	public void onMessage(String channel, String message) {
		_Logger.info("Mensaje recibido en canal [" + channel + "]: " + message);
	}

	public void onSubscribe(String channel, int subscribedChannels) {
		_Logger.info("Subscribed to channel: " + channel);
	}

	public void onUnsubscribe(String channel, int subscribedChannels) {
		_Logger.info("Unsubscribed from channel: " + channel);
	}

	public void onPSubscribe(String pattern, int subscribedChannels) {
	}

	public void onPUnsubscribe(String pattern, int subscribedChannels) {
	}

	public void onPMessage(String pattern, String channel, String message) {
	}
	
	// private methods
	
	/**
	 * Initializes the enabled connections. This will NOT start them, but only create the
	 * instances that will be used in the {@link #startManager() and #stopManager()) methods.
	 * 
	 */
	private void initConnections()
	{
		ConfigUtil configUtil = ConfigUtil.getInstance();

		this.enableSystemPerf = configUtil.getBoolean(
			ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_SYSTEM_PERF_KEY);

		if (this.enableMqttClient) {
			this.mqttClient = new MqttClientConnector();
			this.mqttClient.setDataMessageListener(this);
		}
		
		if (this.enableSystemPerf) {

			this.sysPerfMgr = new SystemPerformanceManager();
			this.sysPerfMgr.setDataMessageListener(this);
		}

		if (this.enablePersistenceClient) {
			this.redisClient = new RedisPersistenceAdapter();
		}
	}

	private void handleIncomingDataAnalysis(ResourceNameEnum resourceName, ActuatorData data)
	{
		_Logger.info("Handling incoming data analysis (actuator) for resource: " + resourceName.toString());
	}

	private void handleIncomingDataAnalysis(ResourceNameEnum resourceName, SystemStateData data)
	{
		_Logger.info("Handling incoming data analysis  (system state) for resource: " + resourceName.toString());
	}

	private boolean handleUpstreamTransmission(ResourceNameEnum resourceName, String jsonData, int qos)
	{
		_Logger.info("Handling upstream transmission for resource: " + resourceName.toString());
		return false;
	}

}
