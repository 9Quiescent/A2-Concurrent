package nuber.students;

import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.Callable;

/**
 * A single Nuber region that operates independently of other regions, other than getting 
 * drivers from bookings from the central dispatch.
 * 
 * A region has a maxSimultaneousJobs setting that defines the maximum number of bookings 
 * that can be active with a driver at any time. For passengers booked that exceed that 
 * active count, the booking is accepted, but must wait until a position is available, and 
 * a driver is available.
 * 
 * Bookings do NOT have to be completed in FIFO order.
 * 
 * @author james
 *
 */
public class NuberRegion {

	private final NuberDispatch dispatch;
	private final String regionName;
	private final int maxSimultaneousJobs;

	private final Semaphore permits;
	private final ExecutorService executor;
	private volatile boolean shuttingDown = false;

	/**
	 * Creates a new Nuber region
	 * 
	 * @param dispatch The central dispatch to use for obtaining drivers, and logging events
	 * @param regionName The regions name, unique for the dispatch instance
	 * @param maxSimultaneousJobs The maximum number of simultaneous bookings the region is allowed to process
	 */
	public NuberRegion(NuberDispatch dispatch, String regionName, int maxSimultaneousJobs)
	{
		this.dispatch = dispatch;
		this.regionName = regionName;
		this.maxSimultaneousJobs = maxSimultaneousJobs;

		this.permits = new Semaphore(maxSimultaneousJobs, true);
		this.executor = Executors.newCachedThreadPool();

		// register this region with the dispatch so bookPassenger(...) can route to it
		this.dispatch.registerRegion(regionName, this);
	}
	
	/**
	 * Creates a booking for given passenger, and adds the booking to the 
	 * collection of jobs to process. Once the region has a position available, and a driver is available, 
	 * the booking should commence automatically. 
	 * 
	 * If the region has been told to shutdown, this function should return null, and log a message to the 
	 * console that the booking was rejected.
	 * 
	 * @param waitingPassenger
	 * @return a Future that will provide the final BookingResult object from the completed booking
	 */
	public Future<BookingResult> bookPassenger(Passenger waitingPassenger)
	{		
		if (shuttingDown) {
			dispatch.logEvent(null, "Booking rejected for region \"" + regionName + "\" (shutdown)");
			return null;
		}

		Booking job = new Booking(dispatch, waitingPassenger);

		Callable<BookingResult> task = () -> {
			permits.acquire();
			try {
				dispatch.logEvent(job, "started in region \"" + regionName + "\"");
				return job.call();
			} finally {
				permits.release();
				dispatch.logEvent(job, "completed in region \"" + regionName + "\"");
			}
		};

		return executor.submit(task);
	}
	
	/**
	 * Called by dispatch to tell the region to complete its existing bookings and stop accepting any new bookings
	 */
	public void shutdown()
	{
		shuttingDown = true;
		executor.shutdown();
	}
		
}
