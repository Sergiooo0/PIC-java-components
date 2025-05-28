/**
 * This class is part of the Programming the Internet of Things project.
 * 
 * It is provided as a simple shell to guide the student and assist with
 * implementation for the Programming the Internet of Things exercises,
 * and designed to be modified by the student as needed.
 */ 

package programmingtheiot.gda.system;

import java.io.ObjectInputFilter.Config;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.SystemPerformanceData;
import programmingtheiot.gda.app.GatewayDeviceApp;

import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Shell representation of class for student implementation.
 * 
 */
public class SystemPerformanceManager
{
	// private vars
	
	private static final Logger _Logger =
		Logger.getLogger(SystemPerformanceManager.class.getName());

	private int pollRate = ConfigConst.DEFAULT_POLL_CYCLES;
	private ScheduledExecutorService schedExecSvc = null;
	private SystemCpuUtilTask cpuUtilTask = null;
	private SystemMemUtilTask memUtilTask = null;
	private SystemDiskUtilTask diskUtilTask = null;
	
	private Runnable taskRunner = null;
	private boolean isStarted = false;

	private String locationID = ConfigConst.NOT_SET;
	private IDataMessageListener dataMsgListener = null;
	
	// constructors
	
	/**
	 * Default.
	 * 
	 */
	public SystemPerformanceManager()
	{
		this.pollRate = ConfigUtil.getInstance().getInteger(
			ConfigConst.GATEWAY_DEVICE, ConfigConst.POLL_CYCLES_KEY,
			ConfigConst.DEFAULT_POLL_CYCLES);
		
		if (this.pollRate <= 0) {
			this.pollRate = ConfigConst.DEFAULT_POLL_CYCLES;
		}
		
		this.schedExecSvc   = Executors.newScheduledThreadPool(1);
		this.cpuUtilTask = new SystemCpuUtilTask();
		this.memUtilTask = new SystemMemUtilTask();
		this.diskUtilTask = new SystemDiskUtilTask();

		this.taskRunner = () -> handleTelemetry();

		this.locationID =
			ConfigUtil.getInstance().getProperty(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.DEVICE_LOCATION_ID_KEY, ConfigConst.NOT_SET);
	}
	
	
	// public methods
	
	public void handleTelemetry()
	{
		float cpuUtil = this.cpuUtilTask.getTelemetryValue();
		float memUtil = this.memUtilTask.getTelemetryValue();
		float diskUtil = this.diskUtilTask.getTelemetryValue();

		_Logger.info("CPU utilization: " + cpuUtil + " %" + "\nMemory utilization: " + memUtil + " %" + "\nDisk utilization: " + diskUtil + " %");

		SystemPerformanceData spd = new SystemPerformanceData();
		spd.setLocationID(this.locationID);
		spd.setCpuUtilization(cpuUtil);
		spd.setMemoryUtilization(memUtil);
		spd.setDiskUtilization(diskUtil);

		if (this.dataMsgListener != null) {
			this.dataMsgListener.handleSystemPerformanceMessage(
				ResourceNameEnum.GDA_SYSTEM_PERF_MSG_RESOURCE, spd);
	}
	}
	
	public void setDataMessageListener(IDataMessageListener listener)
	{
		if (listener != null) {
			this.dataMsgListener = listener;
		}
	}
	
	public boolean startManager()
	{
		if (! this.isStarted) {
			_Logger.info("Starting SystemPerformanceManager...");
			// ScheduledFuture<?> futureTask =	
			this.schedExecSvc.scheduleAtFixedRate(
				this.taskRunner, 1L, this.pollRate, 
				TimeUnit.SECONDS);
	
			this.isStarted = true;
		} else {
			_Logger.info("SystemPerformanceManager is already started.");
		}
	
		return this.isStarted;
	}
	
	public boolean stopManager()
	{
		if (this.schedExecSvc != null) {
			this.schedExecSvc.shutdown();
			try {
				if (!this.schedExecSvc.awaitTermination(5, TimeUnit.SECONDS)) {
					this.schedExecSvc.shutdownNow();
				}
			} catch (InterruptedException e) {
				this.schedExecSvc.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
		this.isStarted = false;

		_Logger.info("Stopping SystemPerformanceManager...");

			if (this.schedExecSvc != null) {
				this.schedExecSvc.shutdown();
				try {
					if (!this.schedExecSvc.awaitTermination(5, TimeUnit.SECONDS)) {
						this.schedExecSvc.shutdownNow();
					}
				} catch (InterruptedException e) {
					this.schedExecSvc.shutdownNow();
					Thread.currentThread().interrupt();
				}
			}
		return true;
	}
	
}
