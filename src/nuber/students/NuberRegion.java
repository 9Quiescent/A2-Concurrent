package nuber.students;

import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.Callable;

/**
 * A single Nuber region that operates independently of other regions, other than getting
 * drivers for bookings from the central dispatch.
 *
 * A region has a maxSimultaneousJobs setting that defines the maximum number of bookings
 * that can be active with a driver at any time. For passengers booked that exceed that
 * active count, the booking is accepted, but must wait until a position is available, and
 * a driver is available.
 *
 * Bookings do NOT have to be completed in FIFO order.
 *
 * @author james
 */
public class NuberRegion {

    private final NuberDispatch dispatch;
    private final String regionName;

    // This is for the amount of  bookings in this region can be active at once 
    private final Semaphore permits;

    // This is for managing threads that execute bookings for this region 
    private final ExecutorService executor;

    // This is set when the region should stop accepting new bookings
    private volatile boolean shuttingDown = false;

    /**
     * Creates a new Nuber region.
     *
     * @param dispatch            The central dispatch to use for obtaining drivers, and logging events
     * @param regionName          The region name, unique within the dispatch instance
     * @param maxSimultaneousJobs The maximum number of simultaneous bookings the region is allowed to process
     */
    public NuberRegion(NuberDispatch dispatch, String regionName, int maxSimultaneousJobs)
    {
        this.dispatch = dispatch;
        this.regionName = regionName;

        this.permits = new Semaphore(maxSimultaneousJobs, true);
        this.executor = Executors.newCachedThreadPool();

        this.dispatch.registerRegion(regionName, this);
    }

    /**
     * Creates a booking for the given passenger and queues it for processing.
     * Once the region has a position available, and a driver is available,
     * the booking commences automatically.
     *
     * If the region has been told to shutdown, this returns null and logs that the booking was rejected.
     *
     * @param waitingPassenger The passenger to book
     * @return a Future that will provide the final BookingResult from the completed booking, or null if rejected
     */
    public Future<BookingResult> bookPassenger(Passenger waitingPassenger)
    {
        Booking job = new Booking(dispatch, waitingPassenger);
        dispatch.logEvent(job, "Creating booking");
        return bookPassenger(job);
    }

    /**
     * Primary entry: accept an existing Booking.
     * This lets us log with the real job id before scheduling work.
     *
     * @param job The already-constructed booking
     * @return a Future for the eventual BookingResult, or null if rejected due to shutdown
     */
    public Future<BookingResult> bookPassenger(Booking job)
    {
        if (shuttingDown) {
            dispatch.logEvent(job, "Rejected booking");
            return null;
        }

        dispatch.logEvent(job, "Starting booking, getting driver");

        Callable<BookingResult> task = () -> {
            permits.acquire();
            try {
                return job.call();
            } finally {
                permits.release();
            }
        };

        return executor.submit(task);
    }

    /**
     * Called by dispatch to tell the region to complete its existing bookings
     * and stop accepting any new bookings.
     */
    public void shutdown()
    {
        shuttingDown = true;
        executor.shutdown();
    }
}
