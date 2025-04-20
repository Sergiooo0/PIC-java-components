/**
 * This class is part of the Programming the Internet of Things project.
 * 
 * It is provided as a simple shell to guide the student and assist with
 * implementation for the Programming the Internet of Things exercises,
 * and designed to be modified by the student as needed.
 */ 

package programmingtheiot.gda.app;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IActuatorDataListener;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.BaseIotData;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SensorData;
import programmingtheiot.data.SystemPerformanceData;
import programmingtheiot.data.SystemStateData;
import programmingtheiot.gda.connection.CoapServerGateway;
import programmingtheiot.gda.connection.ICloudClient;
import programmingtheiot.gda.connection.IPersistenceClient;
import programmingtheiot.gda.connection.IPubSubClient;
import programmingtheiot.gda.connection.IRequestResponseClient;
import programmingtheiot.gda.connection.MqttClientConnector;
import programmingtheiot.gda.connection.CloudClientConnector;
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
	private ICloudClient cloudClient = null;
	private IPersistenceClient persistenceClient = null;
	private IRequestResponseClient smtpClient = null;
	private CoapServerGateway coapServer = null;
	private RedisPersistenceAdapter redisClient = null;
	private SystemPerformanceManager sysPerfMgr = null;

	private ActuatorData latestHumidifierActuatorData =null;
	private ActuatorData latestHumidifierActuatorResponse =null;
	private SensorData latestHumiditySensorData =null;
	private OffsetDateTime latestHumiditySensorTimeStamp =null;

	private boolean handleHumidityChangeOnDevice =false;// optional
	private int lastKnownHumidifierCommand   =ConfigConst.OFF_COMMAND;


	private long humidityMaxTimePastThreshold =300;// seconds
	private float nominalHumiditySetting   =40.0f;
	private float triggerHumidifierFloor   =30.0f;
	private float triggerHumidifierCeiling =50.0f;


	
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

		this.handleHumidityChangeOnDevice = 
			configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.HANDLE_HUMIDITY);
		this.humidityMaxTimePastThreshold = 
			configUtil.getInteger(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.HUMIDITY_MAX_TIME_PAST_THRESHOLD, 300);// seconds
		this.nominalHumiditySetting   =
			configUtil.getFloat(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.NOMINAL_HUMIDITY_SETTING, 40.0f);
		this.triggerHumidifierFloor   =
			configUtil.getFloat(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.TRIGGER_HUMIDIFIER_FLOOR, 30.0f);
		this.triggerHumidifierCeiling =
			configUtil.getFloat(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.TRIGGER_HUMIDIFIER_CEILING, 50.0f);

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
			_Logger.log(
			Level.FINE,
			"Actuator request received: {0}. Message: {1}",
			new Object[] {resourceName.getResourceName(), Integer.valueOf((data.getCommand()))});
		
			if (data.hasError()) {
				_Logger.log(Level.WARNING, "Received actuator with error of status code: {0}", data.getStatusCode());
			} else {
				int qos = ConfigUtil.getInstance().getInteger(
					ConfigConst.MQTT_GATEWAY_SERVICE, ConfigConst.DEFAULT_QOS_KEY, 0);
				if (this.redisClient != null) {
					this.redisClient.storeData(data.getName(), qos, data);
				}
				this.sendActuatorCommandtoCda(resourceName, data);
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
			if (data.hasError()) {
				_Logger.log(Level.WARNING, "Received sensor with error of status code: {0}", data.getStatusCode());
			} else {
				//String jsonData = dataUtil.sensorDataToJson(data);
				//_Logger.fine("JSON [SensorData] -> " +jsonData);
				int qos = ConfigUtil.getInstance().getInteger(
					ConfigConst.MQTT_GATEWAY_SERVICE, ConfigConst.DEFAULT_QOS_KEY, 0);
				if (this.redisClient != null) {
					this.redisClient.storeData(data.getName(), qos, data);
				}
				this.handleIncomingDataAnalysis(resourceName,data);

				this.handleUpstreamTransmission(resourceName,data,qos);
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
			int qos = ConfigUtil.getInstance().getInteger(
				ConfigConst.MQTT_GATEWAY_SERVICE, ConfigConst.DEFAULT_QOS_KEY, 0);
			if (this.redisClient != null) {
				this.redisClient.storeData(data.getName(), qos, data);
			}
			this.handleUpstreamTransmission(resourceName, data,qos);
			_Logger.info("Handled system performance message");
			return true;
		}
		return false;
	}
	
	public void setActuatorDataListener(String name, IActuatorDataListener listener)
	{
		if (listener != null) {
			this.actuatorDataListener = listener;
		} else {
			_Logger.warning("Actuator data listener is null.");
		}
	}
	
	public void startManager()
	{
		_Logger.info("Starting device data manager.");
		if (this.enableCloudClient && this.cloudClient != null) {
			if (this.cloudClient.connectClient()) {
				_Logger.info("Starting cloud client.");
			} else {
				_Logger.warning("Unable to connect to cloud service. Cloud client will not be started.");
			}
		}
		if (this.mqttClient != null) {
			if (this.mqttClient.connectClient()){
				_Logger.info("Starting Async MQTT client.");
			} else {
				_Logger.warning("Unable to connect to MQTT broker. MQTT client will not be started.");
				// TODO: handle this case
				throw new RuntimeException("Unable to connect to MQTT broker. MQTT client will not be started.");
			}
		}
		// give some time for the MQTT client to connect
		try {
			Thread.sleep(2000L);
		} catch (Exception e) {
			// ignore
		}
		if (this.enableCoapServer && this.coapServer != null) {
			if (this.coapServer.startServer()) {
				_Logger.info("CoAP server started.");
			} else {
				_Logger.severe("Failed to start CoAP server. Check log file for details.");
			}
		}
		
		if (this.redisClient != null) {
			_Logger.info("Starting Redis client.");
			this.redisClient.connectClient();
			//Teniendo MQTT y Redis, no es necesario subscribirse a los canales de Redis
			// redis sólo para almacenamiento persistente.
			//this.redisClient.subscribeToChannel(this, ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE);
		}
		if (this.sysPerfMgr != null) {
			_Logger.info("Starting system performance manager.");
			this.sysPerfMgr.startManager();
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
			//this.redisClient.unsubscribeFromChannel(this);
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
		if (this.enableCoapServer && this.coapServer != null) {
			if (this.coapServer.stopServer()) {
				_Logger.info("CoAP server stopped.");
			} else {
				_Logger.severe("Failed to stop CoAP server. Check log file for details.");
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

		if (this.enableCoapServer) {
			this.coapServer = new CoapServerGateway(this);
		}

		if (this.enableCloudClient) {
			this.cloudClient = new CloudClientConnector();
			this.cloudClient.setDataMessageListener(this);
		}
	}

	private void handleIncomingDataAnalysis(ResourceNameEnum resourceName, ActuatorData data)
	{
		_Logger.info("Handling incoming data analysis (actuator) for resource: " + resourceName.toString());

		if (data.isResponseFlagEnabled()) {
			_Logger.info("Handling incoming data analysis (actuator) for resource: " + resourceName.toString());
			if (this.actuatorDataListener != null) {
				this.actuatorDataListener.onActuatorDataUpdate(data);
			}
		} else {
			_Logger.warning("Actuator data is not a response. Ignoring.");
		}
	}

	private void handleIncomingDataAnalysis(ResourceNameEnum resource, SensorData data)
	{
		// check either resource or SensorData for type
		if (data.getTypeID() ==ConfigConst.HUMIDITY_SENSOR_TYPE) {
			this.handleHumiditySensorAnalysis(resource,data);
			handleUpstreamTransmission(resource, data, 0);

		}
	}

	private void handleHumiditySensorAnalysis(ResourceNameEnum resource, SensorData data) {
		_Logger.info("Analyzing humidity data from CDA: " + data.getLocationID() + ". Value: " + data.getValue());

		boolean isLow = data.getValue() < this.triggerHumidifierFloor;
		boolean isHigh = data.getValue() > this.triggerHumidifierCeiling;

		if (isLow || isHigh) {
			_Logger.info("Humidity data from CDA exceeds nominal range.");

			if (this.latestHumiditySensorData == null) {
				this.latestHumiditySensorData = data;
				this.latestHumiditySensorTimeStamp = getDateTimeFromData(data);

				_Logger.info(
					"Starting humidity nominal exception timer. Waiting for seconds: " +
					this.humidityMaxTimePastThreshold);
				return;
			} else {
				OffsetDateTime curHumiditySensorTimeStamp = getDateTimeFromData(data);
				long diffSeconds = ChronoUnit.SECONDS.between(this.latestHumiditySensorTimeStamp, curHumiditySensorTimeStamp);

				_Logger.info("Checking Humidity value exception time delta: " + diffSeconds);

				if (diffSeconds >= this.humidityMaxTimePastThreshold) {
					ActuatorData ad = new ActuatorData();
					ad.setName(ConfigConst.HUMIDIFIER_ACTUATOR_NAME);
					ad.setLocationID(data.getLocationID());
					ad.setTypeID(ConfigConst.HUMIDIFIER_ACTUATOR_TYPE);
					ad.setValue(this.nominalHumiditySetting);

					if (isLow) {
						ad.setCommand(ConfigConst.ON_COMMAND);
					} else if (isHigh) {
						ad.setCommand(ConfigConst.OFF_COMMAND);
					}

					_Logger.info("Humidity exceptional value reached. Sending actuation event to CDA: " + ad);

					this.lastKnownHumidifierCommand = ad.getCommand();
					sendActuatorCommandtoCda(ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE, ad);

					this.latestHumidifierActuatorData = ad;
					this.latestHumiditySensorData = null;
					this.latestHumiditySensorTimeStamp = null;
				}
			}
		} else if (this.lastKnownHumidifierCommand == ConfigConst.ON_COMMAND) {
			if (this.latestHumidifierActuatorData != null) {
				if (data.getValue() >= this.nominalHumiditySetting) {
					this.latestHumidifierActuatorData.setCommand(ConfigConst.OFF_COMMAND);

					_Logger.info("Humidity nominal value reached. Sending OFF actuation event to CDA: " +
						this.latestHumidifierActuatorData);

					sendActuatorCommandtoCda(ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE, this.latestHumidifierActuatorData);

					this.lastKnownHumidifierCommand = this.latestHumidifierActuatorData.getCommand();
					this.latestHumidifierActuatorData = null;
					this.latestHumiditySensorData = null;
					this.latestHumiditySensorTimeStamp = null;
				} else {
					_Logger.info("Humidifier is still on. Not yet at nominal levels (OK).");
				}
			} else {
				_Logger.warning("ERROR: ActuatorData for humidifier is null (shouldn't be). Can't send command.");
			}
		}
	}


	private void sendActuatorCommandtoCda(ResourceNameEnum resource, ActuatorData data)
	{
		if (this.actuatorDataListener != null) {
			this.actuatorDataListener.onActuatorDataUpdate(data);
		}

		if (this.enableMqttClient && this.mqttClient != null) {
			String jsonData = DataUtil.getInstance().actuatorDataToJson(data);

			if (this.mqttClient.publishMessage(resource, jsonData, ConfigConst.DEFAULT_QOS)) {
				_Logger.info("Published ActuatorData command from GDA to CDA: " + data.getCommand());
			} else {
				_Logger.warning("Failed to publish ActuatorData command from GDA to CDA: " + data.getCommand());
			}
		}
		if (this.enablePersistenceClient && this.redisClient != null) {
			this.redisClient.storeData(data.getName(), 0, data);
		}
		
	}

	private OffsetDateTime getDateTimeFromData(BaseIotData data)
	{
		OffsetDateTime odt = null;

		try {
			odt = OffsetDateTime.parse(data.getTimeStamp());
		} catch (Exception e) {
			_Logger.warning("Failed to extract ISO 8601 timestamp from IoT data. Using local current time.");
			odt = OffsetDateTime.now();
		}
		return odt;
	}


	private void handleIncomingDataAnalysis(ResourceNameEnum resourceName, SystemStateData data)
	{
		_Logger.info("Handling incoming data analysis  (system state) for resource: " + resourceName.toString());
	}

	private boolean handleUpstreamTransmission(ResourceNameEnum resourceName, SensorData data, int qos)
	{
		_Logger.info("Sending Json data to cloud: " + resourceName.toString());
		if (this.cloudClient != null) {
			if (this.cloudClient.sendEdgeDataToCloud(resourceName, data)) {
				_Logger.info("Published data to cloud: " + resourceName.toString());
				return true;
			} else {
				_Logger.warning("Failed to publish data to cloud: " + resourceName.toString());
			}
		} else {
			_Logger.warning("Cloud client is not enabled. Cannot publish data.");
		}
		return false;
	}

	private boolean handleUpstreamTransmission(ResourceNameEnum resourceName, SystemPerformanceData data, int qos)
	{
		_Logger.info("Sending Json data to cloud: " + resourceName.toString());
		if (this.cloudClient != null) {
			if (this.cloudClient.sendEdgeDataToCloud(resourceName, data)) {
				_Logger.info("Published data to cloud: " + resourceName.toString());
				return true;
			} else {
				_Logger.warning("Failed to publish data to cloud: " + resourceName.toString());
			}
		} else {
			_Logger.warning("Cloud client is not enabled. Cannot publish data.");
		}
		return false;
	}

}
