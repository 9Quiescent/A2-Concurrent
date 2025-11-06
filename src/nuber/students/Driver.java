package nuber.students;

public class Driver extends Person {

    private Passenger currentPassenger;

    public Driver(String driverName, int maxSleep) {
        super(driverName, maxSleep);
    }

    /**
     * Stores the provided passenger as the driver's current passenger and then
     * sleeps the thread for between 0-maxDelay milliseconds.
     *
     * @param newPassenger Passenger to collect
     * @throws InterruptedException
     */
    public void pickUpPassenger(Passenger newPassenger) {
        this.currentPassenger = newPassenger;
        try {
            Thread.sleep((int)(Math.random() * maxSleep));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sleeps the thread for the amount of time returned by the current
     * passenger's getTravelTime() function
     *
     * @throws InterruptedException
     */
    public void driveToDestination() {
        if (currentPassenger == null) return;
        try {
            Thread.sleep(currentPassenger.getTravelTime());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
