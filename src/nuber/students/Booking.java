package nuber.students;

import java.util.Date;

/**
 * 
 * Booking represents the overall "job" for a passenger getting to their destination.
 * 
 * It begins with a passenger, and when the booking is commenced by the region 
 * responsible for it, an available driver is allocated from dispatch. If no driver is 
 * available, the booking must wait until one is. When the passenger arrives at the destination,
 * a BookingResult object is provided with the overall information for the booking.
 * 
 * The Booking must track how long it takes, from the instant it is created, to when the 
 * passenger arrives at their destination. This should be done using Date class' getTime().
 * 
 * Booking's should have a globally unique, sequential ID, allocated on their creation. 
 * This should be multi-thread friendly, allowing bookings to be created from different threads.
 * 
 * @author james
 *
 */
public class Booking {
    private static int NEXT_ID = 1;
    private static synchronized int nextId() { return NEXT_ID++; }
    private final NuberDispatch dispatch;
    private final Passenger passenger;
    private final int jobID;
    private final long startTime;
    private volatile Driver allocatedDriver;

    /**
     * Creates a new booking for a given Nuber dispatch and passenger, noting that no
     * driver is provided as it will depend on whether one is available when the region 
     * can begin processing this booking.
     * 
     * @param dispatch
     * @param passenger
     */
    public Booking(NuberDispatch dispatch, Passenger passenger)
    {
        this.dispatch = dispatch;
        this.passenger = passenger;
        this.jobID = nextId();
        this.startTime = new Date().getTime();
    }
    
    /**
     * At some point, the Nuber Region responsible for the booking can start it (has free spot),
     * and calls the Booking.call() function, which:
     * 1.    Asks Dispatch for an available driver
     * 2.    If no driver is currently available, the booking must wait until one is available. 
     * 3.    Once it has a driver, it must call the Driver.pickUpPassenger() function, with the 
     *          thread pausing whilst as function is called.
     * 4.    It must then call the Driver.driveToDestination() function, with the thread pausing 
     *          whilst as function is called.
     * 5.    Once at the destination, the time is recorded, so we know the total trip duration. 
     * 6.    The driver, now free, is added back into Dispatch’s list of available drivers. 
     * 7.    The call() function the returns a BookingResult object, passing in the appropriate 
     *          information required in the BookingResult constructor.
     *
     * @return A BookingResult containing the final information about the booking 
     */
    public BookingResult call() {

        // set up a driver via dispatch
        Driver driver = dispatch.getDriver();

        try {
            // if no driver was available (the driver from dispatch is null)
            // duration becomes the current time minus when the booking was created
            if (driver == null) {
                long duration = new Date().getTime() - startTime;
                return new BookingResult(jobID, passenger, null, duration);
            }

            // we've actually got a driver now, so this job is no longer in an awaiting ether
            dispatch.decrementAwaitingDriver();

            // mark driver for logging/toString so long as active
            allocatedDriver = driver;

            // Then since we have a driver, we're on the way to the passenger
            dispatch.logEvent(this, "Starting, on way to passenger");

            // block thread by simulating pickup 
            driver.pickUpPassenger(passenger);

            // then since we have a passenger, now going to destination
            dispatch.logEvent(this, "Collected passenger, on way to destination");

            // block thread by simulating drive
            driver.driveToDestination();

            // and then since we are at the destination, driver will be freed
            dispatch.logEvent(this, "At destination, driver is now free");

            // calculation for the total time taken from booking creation to arrival
            long duration = new Date().getTime() - startTime;
            return new BookingResult(jobID, passenger, driver, duration);

        } finally {
            // always return the driver to dispatch if we had one in the first place
            if (driver != null) {
                dispatch.addDriver(driver);
            }
            // clear once completed so bookings in an idle state aren't claiming a driver in logs
            allocatedDriver = null;
        }
    }

    /***
     * Should return the:
     * - booking ID, 
     * - followed by a colon, 
     * - followed by the driver's name (if the driver is null, it should show the word "null")
     * - followed by a colon, 
     * - followed by the passenger's name (if the passenger is null, it should show the word "null")
     * 
     * @return The compiled string
     */
    @Override
    public String toString()
    {
        String driverName = (allocatedDriver == null ? "null" : allocatedDriver.name);
        String passengerName = (passenger == null ? "null" : passenger.name);
        return jobID + ":" + driverName + ":" + passengerName;
    }

    public String getPassengerName() {
        return (passenger == null ? "null" : passenger.name);
    }

    public String getDriverName() {
        Driver d = allocatedDriver; 
        return (d == null ? "null" : d.name);
    }

    public int getJobID() {
        return jobID;
    }
}
