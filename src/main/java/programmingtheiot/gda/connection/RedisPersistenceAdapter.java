/**
 * This class is part of the Programming the Internet of Things project.
 * 
 * It is provided as a simple shell to guide the student and assist with
 * implementation for the Programming the Internet of Things exercises,
 * and designed to be modified by the student as needed.
 */ 

package programmingtheiot.gda.connection;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SensorData;
import programmingtheiot.data.SystemPerformanceData;
import programmingtheiot.gda.app.DeviceDataManager;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.exceptions.JedisConnectionException;

/**
 * Shell representation of class for student implementation.
 * 
 */
public class RedisPersistenceAdapter implements IPersistenceClient
{
	// static
	
	private static final Logger _Logger =
		Logger.getLogger(RedisPersistenceAdapter.class.getName());
	
	// private var's
	private Jedis jedis = null;
	private String redisHost = null;
	private int redisPort = 0;

    private boolean isSubscribed = false;
    private Thread subscriptionThread = null;
	
	// constructors
	
	/**
	 * Default.
	 * 
	 */
	public RedisPersistenceAdapter()
	{
		super();

		redisHost = ConfigUtil.getInstance().getProperty(
			ConfigConst.DATA_GATEWAY_SERVICE, ConfigConst.HOST_KEY);
		redisPort = ConfigUtil.getInstance().getInteger(
			ConfigConst.DATA_GATEWAY_SERVICE, ConfigConst.PORT_KEY);
		try {
			jedis = new Jedis(redisHost, redisPort);
			_Logger.info("Connected to Redis at " + redisHost + ":" + redisPort);
		} catch (JedisConnectionException e) {
			_Logger.log(Level.SEVERE, "Failed to connect to Redis", e);
		}
		initConfig();
	}
	
	
	// public methods
	
	// public methods
	
	/**
	 *
	 */
	@Override
	public boolean connectClient()
	{
		if (this.jedis.isConnected()) {
            _Logger.log(Level.INFO, "Redis client already connected");
			return true;
		}else {
			try {
				this.jedis.connect();
				return true;
			} catch (JedisConnectionException e) {
				_Logger.log(Level.SEVERE, "Error connecting to Redis", e);
				return false;
			}
		}
	}

	/**
	 *
	 */
	@Override
	public boolean disconnectClient()
	{
		if (this.jedis.isConnected()) {
			this.jedis.close();
			return true;
		}
		return false;
	}

	/**
	 *
	 */
	@Override
	public void registerDataStorageListener(Class cType, IPersistenceListener listener, String... topics)
	{
	}

	/**
     * Stores ActuatorData in Redis.
     */
    @Override
    public boolean storeData(String topic, int qos, ActuatorData... data) {
        if (!this.jedis.isConnected()) {
            _Logger.warning("Cannot store data: Redis is not connected.");
            return false;
        }

        try {
            if (!"list".equals(jedis.type(topic))) {
                _Logger.warning("Redis key type mismatch for topic '" + topic + "'. Deleting existing key.");
                jedis.del(topic);
            }
            for (ActuatorData d : data) {
                String jsonData = DataUtil.getInstance().actuatorDataToJson(d);
				// TODO: Add timestamp to data
				// lpush añade elementos a una lista.
                this.jedis.lpush(topic, jsonData);
                _Logger.info("Stored data in Redis at topic: " + topic);
            }
            return true;
        } catch (Exception e) {
            _Logger.log(Level.SEVERE, "Failed to store data in Redis", e);
            return false;
        }
    }

    /**
     * Stores SensorData in Redis.
     */
    @Override
    public boolean storeData(String topic, int qos, SensorData... data) {
        if (!this.jedis.isConnected()) {
            _Logger.warning("Cannot store data: Redis is not connected.");
            return false;
        }

        try {
            if (!"list".equals(jedis.type(topic))) {
                _Logger.warning("Redis key type mismatch for topic '" + topic + "'. Deleting existing key.");
                jedis.del(topic);
            }
            for (SensorData d : data) {
                String jsonData = DataUtil.getInstance().sensorDataToJson(d);
                
                this.jedis.lpush(topic, jsonData);
                _Logger.info("Stored data in Redis at topic: " + topic);
            }
            return true;
        } catch (Exception e) {
            _Logger.log(Level.SEVERE, "Failed to store data in Redis", e);
            return false;
        }
    }

    /**
     * Stores SystemPerformanceData in Redis.
     */
    @Override
    public boolean storeData(String topic, int qos, SystemPerformanceData... data) {
        if (!this.jedis.isConnected()) {
            _Logger.warning("Cannot store data: Redis is not connected.");
            return false;
        }

        try {
            if (!"list".equals(jedis.type(topic))) {
				_Logger.warning("Redis key type mismatch for topic '" + topic + "'. Deleting existing key.");
				jedis.del(topic);
			}
            for (SystemPerformanceData d : data) {
                String jsonData = DataUtil.getInstance().systemPerformanceDataToJson(d);

                this.jedis.lpush(topic, jsonData);
                _Logger.info("Stored SystemPerformanceData in Redis at topic: " + topic);
            }
            return true;
        } catch (Exception e) {
            _Logger.log(Level.SEVERE, "Failed to store data in Redis", e);
            return false;
        }
    }


    /**
     * Retrieves ActuatorData from Redis based on the given time range.
     */
    @Override
    public ActuatorData[] getActuatorData(String topic, Date startDate, Date endDate) {
        List<String> jsonList = retrieveDataFromRedis(topic);
		ActuatorData[] actuatorDataList = new ActuatorData[jsonList.size()];
		for (int i = 0; i < jsonList.size(); i++) {
			actuatorDataList[i] = DataUtil.getInstance().jsonToActuatorData(jsonList.get(i));
		}
		return actuatorDataList;
    }

    /**
     * Retrieves SensorData from Redis based on the given time range.
     */
    @Override
    public SensorData[] getSensorData(String topic, Date startDate, Date endDate) {
        List<String> jsonList = retrieveDataFromRedis(topic);
        SensorData[] sensorDataList = new SensorData[jsonList.size()];
        for (int i = 0; i < jsonList.size(); i++) {
            sensorDataList[i] = DataUtil.getInstance().jsonToSensorData(jsonList.get(i));
        }
        return sensorDataList;
    }

    /**
     * Retrieves data from Redis.
     */
    private List<String> retrieveDataFromRedis(String topic) {
        List<String> jsonList = new ArrayList<>();
        if (!this.jedis.isConnected()) {
            _Logger.warning("Cannot retrieve data: Redis is not connected.");
            return jsonList;
        }

        try {
            jsonList = this.jedis.lrange(topic, 0, -1);
            _Logger.info("Retrieved " + jsonList.size() + " records from Redis topic: " + topic);
        } catch (Exception e) {
            _Logger.log(Level.SEVERE, "Failed to retrieve data from Redis", e);
        }
        return jsonList;
    }
	
	
	// private methods
	
	/**
	 * 
	 */
	private void initConfig()
	{
	}


	public boolean isConnected() {
		return this.jedis.isConnected();
	}

    public void subscribeToChannel(JedisPubSub subscriber, ResourceNameEnum resource) {
        if (isSubscribed) {
            _Logger.warning("Already subscribed to channel: " + resource.getResourceName());
            return;
        }

        isSubscribed = true;
        subscriptionThread = new Thread(() -> {
            try {
                _Logger.info("Subscribe to channel: " + resource.getResourceName());
                jedis.subscribe(subscriber, resource.getResourceName());
            } catch (Exception e) {
                _Logger.log(Level.SEVERE, "Error in subscription thread", e);
            }
        });

        subscriptionThread.start();
    }

    public void unsubscribeFromChannel(JedisPubSub subscriber) {
        if (isSubscribed) {
            _Logger.info("Cancel subscription to channel");
            isSubscribed = false;
            subscriber.unsubscribe(); // Cierra la suscripción
        }

        if (subscriptionThread != null && subscriptionThread.isAlive()) {
            try {
                subscriptionThread.join(); // Espera a que el hilo termine
            } catch (InterruptedException e) {
                _Logger.log(Level.SEVERE, "Error waiting for subscription thread to finish", e);
            }
        }
    }

}
