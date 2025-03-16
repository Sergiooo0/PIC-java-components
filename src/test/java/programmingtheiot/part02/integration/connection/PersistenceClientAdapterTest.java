/**
 * 
 * This class is part of the Programming the Internet of Things
 * project, and is available via the MIT License, which can be
 * found in the LICENSE file at the top level of this repository.
 * 
 * Copyright (c) 2020 by Andrew D. King
 */ 

package programmingtheiot.part02.integration.connection;

import static org.junit.Assert.*;

import java.util.Date;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.SensorData;
import programmingtheiot.gda.connection.RedisPersistenceAdapter;
import programmingtheiot.data.SystemPerformanceData;

/**
 * This test case class contains very basic integration tests for
 * RedisPersistenceAdapter. It should not be considered complete,
 * but serve as a starting point for the student implementing
 * additional functionality within their Programming the IoT
 * environment.
 *
 */
public class PersistenceClientAdapterTest
{
	// static
	
	private static final Logger _Logger =
		Logger.getLogger(PersistenceClientAdapterTest.class.getName());
	
	
	// member var's
	
	private RedisPersistenceAdapter rpa = new RedisPersistenceAdapter();
	
	
	// test setup methods
	
	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception
	{
	}
	
	/**
	 * @throws java.lang.Exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception
	{
	}
	
	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception
	{
	}
	
	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception
	{
	}
	
	// test methods
	
	/**
	 * Test method for {@link programmingtheiot.gda.connection.RedisPersistenceAdapter#connectClient()}.
	 */
	@Test
	public void testConnectClient()
	{
		rpa.connectClient();
		assertTrue(rpa.isConnected());
		rpa.disconnectClient();
		assertFalse(rpa.isConnected());
	}
	
	/**
	 * Test method for {@link programmingtheiot.gda.connection.RedisPersistenceAdapter#disconnectClient()}.
	 */
	@Test
	public void testDisconnectClient()
	{	
		rpa.connectClient();
		assertTrue(rpa.isConnected());
		rpa.disconnectClient();
		assertFalse(rpa.isConnected());
	}
	
	/**
	 * Test method for {@link programmingtheiot.gda.connection.RedisPersistenceAdapter#getActuatorData(java.lang.String, java.util.Date, java.util.Date)}.
	 */
	@Test
	public void testGetActuatorData()
	{
		rpa.connectClient();
		ActuatorData[] ad = rpa.getActuatorData("actuator-data_vacio", new Date(), new Date());
		assertNotNull(ad);
		assertEquals(0, ad.length);
		rpa.disconnectClient();
	}
	
	/**
	 * Test method for {@link programmingtheiot.gda.connection.RedisPersistenceAdapter#getSensorData(java.lang.String, java.util.Date, java.util.Date)}.
	 */
	@Test
	public void testGetSensorData()
	{
		rpa.connectClient();
		SensorData[] sd = rpa.getSensorData("sensor-data", new Date(), new Date());
		assertNotNull(sd);
		assertEquals(0, sd.length);
		rpa.disconnectClient();
	}
	
	/**
	 * Test method for {@link programmingtheiot.gda.connection.RedisPersistenceAdapter#storeData(java.lang.String, int, programmingtheiot.data.ActuatorData[])}.
	 */
	@Test
	public void testStoreDataStringIntActuatorDataArray()
	{
		rpa.connectClient();
		ActuatorData[] ad = new ActuatorData[1];
		ad[0] = new ActuatorData();

		rpa.storeData("actuator-data-test", 0, ad);
		assertTrue(rpa.isConnected());
		
		ActuatorData[] ad2 = rpa.getActuatorData("actuator-data-test", new Date(), new Date());
		assertNotNull(ad2);
		// comparamos los valores de los atributos de la clase ActuatorData
		assertEquals(ad[0].getStateData(), ad2[0].getStateData());
		assertEquals(ad[0].getName(), ad2[0].getName());
		assertEquals(ad[0].getValue(), ad2[0].getValue(), 0.0001);
		assertEquals(ad[0].getCommand(), ad2[0].getCommand());
		assertEquals(ad[0].isResponseFlagEnabled(), ad2[0].isResponseFlagEnabled());
		rpa.disconnectClient();
	}
	
	/**
	 * Test method for {@link programmingtheiot.gda.connection.RedisPersistenceAdapter#storeData(java.lang.String, int, programmingtheiot.data.SensorData[])}.
	 */
	@Test
	public void testStoreDataStringIntSensorDataArray()
	{
		rpa.connectClient();
		SensorData[] sd = new SensorData[1];
		sd[0] = new SensorData();
		
		rpa.storeData("sensor-data-test", 0, sd);
		assertTrue(rpa.isConnected());
		
		SensorData[] sd2 = rpa.getSensorData("sensor-data-test", new Date(), new Date());
		assertNotNull(sd2);
		// comparamos los valores de los atributos de la clase SensorData
		assertEquals(sd[0].getTimeStamp(), sd2[0].getTimeStamp());
		assertEquals(sd[0].getName(), sd2[0].getName());
		assertEquals(sd[0].getValue(), sd2[0].getValue(), 0.0001);
		assertEquals(sd[0].getTypeID(), sd2[0].getTypeID());
		assertEquals(sd[0].getLocationID(), sd2[0].getLocationID());
		rpa.disconnectClient();
	}
	
	/**
	 * Test method for {@link programmingtheiot.gda.connection.RedisPersistenceAdapter#storeData(java.lang.String, int, programmingtheiot.data.SystemPerformanceData[])}.
	 */
	@Test
	public void testStoreDataStringIntSystemPerformanceDataArray()
	{
		rpa.connectClient();
		SystemPerformanceData[] spd = new SystemPerformanceData[1];
		spd[0] = new SystemPerformanceData();

		rpa.storeData("system-performance-data", 0, spd);
		assertTrue(rpa.isConnected());
		rpa.disconnectClient();
	}
	
}
