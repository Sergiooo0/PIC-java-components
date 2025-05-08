/**
 * This class is part of the Programming the Internet of Things project.
 * 
 * It is provided as a simple shell to guide the student and assist with
 * implementation for the Programming the Internet of Things exercises,
 * and designed to be modified by the student as needed.
 */ 

package programmingtheiot.data;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.logging.Log;
import org.apache.qpid.proton.amqp.messaging.Data;

import com.google.gson.Gson;
import org.json.JSONObject;

/**
 * Shell representation of class for student implementation.
 *
 */
public class DataUtil
{
	// static

	// private var's
	private static final Logger _Logger = Logger.getLogger(DataUtil.class.getName());
	private static final DataUtil _Instance = new DataUtil();
	Gson gson = new Gson();
	
	// constructors
	/**
	 * Returns the Singleton instance of this class.
	 * 
	 * @return ConfigUtil
	 */
	public static final DataUtil getInstance()
	{
		return _Instance;
	}
	
	/**
	 * Default (private).
	 * 
	 */
	private DataUtil()
	{
		super();
	}
	
	
	// public methods
	
	public String actuatorDataToJson(ActuatorData aData)
	{
		String jsonData = null;

		if (aData != null) {			
			jsonData = gson.toJson(aData);
		}
		return jsonData;
	}
	
	public String sensorDataToJson(SensorData sData)
	{
		String jsonData = null;

		if (sData != null) {			
			jsonData = gson.toJson(sData);
		}
		return jsonData;
	}
	
	public String systemPerformanceDataToJson(SystemPerformanceData sysPerfData)
	{
		String jsonData = null;
		
		if (sysPerfData != null) {			
			jsonData = gson.toJson(sysPerfData);
		}
		return jsonData;
	}
	
	public String systemStateDataToJson(SystemStateData sysStateData)
	{
		String jsonData = null;
		
		if (sysStateData != null) {			
			jsonData = gson.toJson(sysStateData);
		}
		return jsonData;
	}
	
	public ActuatorData jsonToActuatorData(String jsonData)
	{
		ActuatorData aData = null;
		try {
			aData = gson.fromJson(jsonData, ActuatorData.class);
		} catch (Exception e) {
			_Logger.warning("Error parsing JSON: " + e.getMessage());
		}
		return aData;
	}
	
	public SensorData jsonToSensorData(String jsonData)
	{
		SensorData sData = null;
		try {
			sData = gson.fromJson(jsonData, SensorData.class);
		} catch (Exception e) {
			_Logger.warning("Error parsing JSON: " + e.getMessage());
		}
		return sData;
	}
	
	public SystemPerformanceData jsonToSystemPerformanceData(String jsonData)
	{
		SystemPerformanceData sysPerfData = null;
		try {
			sysPerfData = gson.fromJson(jsonData, SystemPerformanceData.class);
		} catch (Exception e) {
			_Logger.warning("Error parsing JSON: " + e.getMessage());
		}
		return sysPerfData;
	}
	
	public SystemStateData jsonToSystemStateData(String jsonData)
	{
		SystemStateData sysStateData = null;
		try {
			sysStateData = gson.fromJson(jsonData, SystemStateData.class);
		} catch (Exception e) {
			_Logger.warning("Error parsing JSON: " + e.getMessage());
		}
		return sysStateData;
	}

	public String payloadToCloudPayload(String payload){
		// Convert our payload to a format that the cloud can understand
		// The payload is a JSON string, so we need to parse it
		// and convert it to the format that the cloud expects
		// The cloud expects a JSON object with the following format:
		// {
		//   "value": <value>,
		//   "timestamp": <timestamp>,
		//   "context": {
		//     <key>: <value>,
		//     ...
		//   }
		// }
		String cloudPayload = null;
		JSONObject oldPayloadjson = new JSONObject(payload);
		JSONObject cloudPayloadJson = new JSONObject();
		cloudPayloadJson.put("value", oldPayloadjson.get("value"));
		cloudPayloadJson.put("timestamp", oldPayloadjson.get("timeStampMillis"));

        // Copy the rest of old payload in "context"
        JSONObject context = new JSONObject();
        Iterator<String> keys = oldPayloadjson.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!key.equals("value") && !key.equals("timeStampMillis")) {
                context.put(key, oldPayloadjson.get(key));
            }
        }

        cloudPayloadJson.put("context", context);

		// Convert the cloud payload to a string
		_Logger.info("Cloud payload: " + cloudPayloadJson.toString(2));
		cloudPayload = cloudPayloadJson.toString();

		return cloudPayload;
	}

	public String cloudPayloadToPayload(String payload){
		// Convert the Cloud payload to a format that can be translate to BaseIoTData
		// Parse the cloud-style JSON
		JSONObject cloudPayloadJson = new JSONObject(payload);

		JSONObject originalPayloadJson = new JSONObject();
	
		originalPayloadJson.put("value", cloudPayloadJson.get("value"));
		originalPayloadJson.put("timeStampMillis", cloudPayloadJson.get("timestamp"));

		if (cloudPayloadJson.has("context")) {
			JSONObject context = cloudPayloadJson.getJSONObject("context");
	
			Iterator<String> keys = context.keys();
			while (keys.hasNext()) {
				String key = keys.next();
				originalPayloadJson.put(key, context.get(key));
			}
		}
	
		return originalPayloadJson.toString();
	}
	
}
