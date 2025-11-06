package nuber.students;

import java.util.HashMap;
import java.util.concurrent.Future;

/**
 * The core Dispatch class that instantiates and manages everything for Nuber.
 *
 * It owns the global pool of idle drivers, distributes drivers to bookings,
 * tracks how many bookings are awaiting a driver, and constructs regions.
 *
 * @author james
 */
public class NuberDispatch {

    private final int MAX_DRIVERS = 999;
    private final java.util.ArrayDeque<Driver> idleDrivers = new java.util.ArrayDeque<>();
    private final Object driverLock = new Object();
    private int bookingsAwaitingDriver = 0;
    private final HashMap<String, NuberRegion> regions = new HashMap<>();
    private volatile boolean shuttingDown = false;
    private final boolean logEvents;

    /**
     * Creates a new dispatch object and instantiates the required regions and any other objects required.
     * It should be able to handle a variable number of regions based on the HashMap provided.
     *
     * @param regionInfo Map of region names and the max simultaneous bookings they can handle
     * @param logEvents  Whether logEvent should print out events passed to it
     */
    public NuberDispatch(HashMap<String, Integer> regionInfo, boolean logEvents)
    {
        this.logEvents = logEvents;

        System.out.println("Creating Nuber Dispatch");
        System.out.println("Creating " + regionInfo.size() + " regions");
        for (var e : regionInfo.entrySet()) {
            System.out.println("Creating Nuber region for " + e.getKey());
            // Region registers itself back into this dispatch
            new NuberRegion(this, e.getKey(), e.getValue());
        }
        System.out.println("Done creating " + regionInfo.size() + " regions");
    }

    /**
     * Adds a driver to the queue of idle drivers.
     * Must be able to have drivers added from multiple threads.
     *
     * @param newDriver The driver to add to the queue
     * @return true if the driver was added, false if the queue was full or driver was null
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
     * Gets a driver from the front of the idle queue, blocking until one is available.
     *
     * @return a Driver, or null if interrupted
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
     * Prints:
     *     booking + ": " + message
     * to standard output only if logging is enabled.
     *
     * @param booking The booking responsible for the event
     * @param message The message to show
     */
    public void logEvent(Booking booking, String message) {
        if (!logEvents) return;
        System.out.println(booking + ": " + message);
    }

    /**
     * Books a given passenger into a given Nuber region.
     *
     * Behavior:
     * - Logs "Creating booking" with a real job id even if the dispatch is shutting down.
     * - Increments the "awaiting driver" counter only when the booking is accepted.
     *
     * @param passenger The passenger to book
     * @param regionName The region to book them into
     * @return a Future<BookingResult>, or null if rejected due to shutdown or unknown region
     */
    public Future<BookingResult> bookPassenger(Passenger passenger, String regionName) {
        NuberRegion region = regions.get(regionName);
        if (region == null) return null;

        // Always create a Booking so the logs show an actual id
        Booking job = new Booking(this, passenger);
        logEvent(job, "Creating booking");

        if (shuttingDown) {
            logEvent(job, "Rejected booking");
            return null;
        }

        // Booking is accepted, count it as awaiting a driver until it actually gets one
        synchronized (driverLock) {
            bookingsAwaitingDriver++;
        }
        // Pass the constructed job so logs refer to the same id
        return region.bookPassenger(job);
    }

    /**
     * Gets the number of non-completed bookings that are awaiting a driver from dispatch.
     * Once a driver is given to a booking, this value is reduced by one.
     *
     * @return number of bookings awaiting a driver across all regions
     */
    public int getBookingsAwaitingDriver()
    {
        synchronized (driverLock) {
            return bookingsAwaitingDriver;
        }
    }

    /**
     * Tells all regions to finish existing bookings already allocated, and stop accepting new bookings.
     */
    public void shutdown() {
        shuttingDown = true;
        for (var reg : regions.values()) {
            reg.shutdown();
        }
    }

    /**
     * Used when a booking successfully obtains a driver.
     * Reduces the awaiting counter. Guard against underflow.
     */
    void decrementAwaitingDriver() {
        synchronized (driverLock) {
            if (bookingsAwaitingDriver > 0) bookingsAwaitingDriver--;
        }
    }

    /**
     * Registers a region name to a region instance.
     * Silently ignores duplicates to match the sample output.
     *
     * @param name   Region name
     * @param region Region instance
     */
    synchronized void registerRegion(String name, NuberRegion region) {
        if (name == null || region == null) return;
        if (!regions.containsKey(name)) {
            regions.put(name, region);
        }
    }
}
