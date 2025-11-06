package nuber.students;

import java.util.HashMap;
import java.util.concurrent.Future;

/**
 * The core Dispatch class that instantiates and manages everything for Nuber
 * 
 * @author james
 *
 */
public class NuberDispatch {

	/**
	 * The maximum number of idle drivers that can be awaiting a booking 
	 */
	private final int MAX_DRIVERS = 999;
	
	private boolean logEvents = false;

	// shared state protected by driverLock
	private final java.util.ArrayDeque<Driver> idleDrivers = new java.util.ArrayDeque<Driver>();
	private final Object driverLock = new Object();
	private int bookingsAwaitingDriver = 0;
	private final HashMap<String, Integer> regionConfig;
	
	/**
	 * Creates a new dispatch objects and instantiates the required regions and any other objects required.
	 * It should be able to handle a variable number of regions based on the HashMap provided.
	 * 
	 * @param regionInfo Map of region names and the max simultaneous bookings they can handle
	 * @param logEvents Whether logEvent should print out events passed to it
	 */
	public NuberDispatch(HashMap<String, Integer> regionInfo, boolean logEvents)
	{//
		this.logEvents = logEvents;
		this.regionConfig = new HashMap<String, Integer>(regionInfo);
	}
	
	/**
	 * Adds drivers to a queue of idle driver.
	 *  
	 * Must be able to have drivers added from multiple threads.
	 * 
	 * @param The driver to add to the queue.
	 * @return Returns true if driver was added to the queue
	 */
	public boolean addDriver(Driver newDriver)
	{
		if (newDriver == null) return false;
		synchronized (driverLock) {
			if (idleDrivers.size() >= MAX_DRIVERS) {
				return false;
			}
			idleDrivers.addLast(newDriver);
			driverLock.notifyAll();
			return true;
		}
	}
	
	/**
	 * Gets a driver from the front of the queue
	 *  
	 * Must be able to have drivers added from multiple threads.
	 * 
	 * @return A driver that has been removed from the queue
	 */
	public Driver getDriver()
	{
		synchronized (driverLock) {
			while (idleDrivers.isEmpty()) {
				try {
					driverLock.wait();
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					return null;
				}
			}
			return idleDrivers.pollFirst();
		}
	}

	/**
	 * Prints out the string
	 * 	    booking + ": " + message
	 * to the standard output only if the logEvents variable passed into the constructor was true
	 * 
	 * @param booking The booking that's responsible for the event occurring
	 * @param message The message to show
	 */
	public void logEvent(Booking booking, String message) {
		
		if (!logEvents) return;
		
		System.out.println(booking + ": " + message);
		
	}

	/**
	 * Books a given passenger into a given Nuber region.
	 * 
	 * Once a passenger is booked, the getBookingsAwaitingDriver() should be returning one higher.
	 * 
	 * If the region has been asked to shutdown, the booking should be rejected, and null returned.
	 * 
	 * @param passenger The passenger to book
	 * @param region The region to book them into
	 * @return returns a Future<BookingResult> object
	 */
	public Future<BookingResult> bookPassenger(Passenger passenger, String region) {
		synchronized (driverLock) {
			bookingsAwaitingDriver++;
		}
		return null;
	}

	/**
	 * Gets the number of non-completed bookings that are awaiting a driver from dispatch
	 * 
	 * Once a driver is given to a booking, the value in this counter should be reduced by one
	 * 
	 * @return Number of bookings awaiting driver, across ALL regions
	 */
	public int getBookingsAwaitingDriver()
	{
		synchronized (driverLock) {
			return bookingsAwaitingDriver;
		}
	}
	
	/**
	 * Tells all regions to finish existing bookings already allocated, and stop accepting new bookings
	 */
	public void shutdown() {
	}

	// this is just a helper that's used when a booking successfully grabs a driver
	void decrementAwaitingDriver() {
		synchronized (driverLock) {
			if (bookingsAwaitingDriver > 0) bookingsAwaitingDriver--;
		}
	}
}
