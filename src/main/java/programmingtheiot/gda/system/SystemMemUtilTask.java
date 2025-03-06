/**
 * This class is part of the Programming the Internet of Things project.
 * 
 * It is provided as a simple shell to guide the student and assist with
 * implementation for the Programming the Internet of Things exercises,
 * and designed to be modified by the student as needed.
 */ 

package programmingtheiot.gda.system;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;

import programmingtheiot.common.ConfigConst;

/**
 * Shell representation of class for student implementation.
 * 
 */
public class SystemMemUtilTask extends BaseSystemUtilTask
{
	// constructors
	
	/**
	 * Default.
	 * 
	 */
	public SystemMemUtilTask()
	{
		super(ConfigConst.MEM_UTIL_NAME, ConfigConst.MEM_UTIL_TYPE);
	}
	
	
	// public methods
	
	@Override
	public float getTelemetryValue()
	{
		// my code
		MemoryUsage memUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
		_Logger.info("Memory Usage: " + memUsage.toString());
		// si no se especifica double en los dividendos, el resultado es 0
		double memUtil = ( (double) memUsage.getUsed() / (double) memUsage.getMax()) * 100.0d;
		return (float) memUtil;
	}
	
}
