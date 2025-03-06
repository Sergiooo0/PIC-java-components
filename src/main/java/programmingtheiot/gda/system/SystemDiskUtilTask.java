package programmingtheiot.gda.system;
import java.lang.management.ManagementFactory;

import java.io.File;

import programmingtheiot.common.ConfigConst;


public class SystemDiskUtilTask extends BaseSystemUtilTask {
    
	// constructors
	
	/**
	 * Default.
	 * 
	 */
	public SystemDiskUtilTask()
	{
		super(ConfigConst.DISK_UTIL_NAME, ConfigConst.DISK_UTIL_TYPE);
	}
	
	
	// public methods
	
	@Override
	public float getTelemetryValue()
	{
        File diskPartition = new File("/home/sergio/Escritorio/PIC/PIC-java-components/src/main");
        long totalSpace = diskPartition.getTotalSpace(); // Total disk space
        long freeSpace = diskPartition.getUsableSpace(); // Free space
        long usedSpace = totalSpace - freeSpace; // Used space

        float diskUsage = (float) usedSpace / totalSpace * 100; // Percentage usage
        _Logger.info("Disk Usage: " + diskUsage + "%");
        return diskUsage;
	}
	
    
}
