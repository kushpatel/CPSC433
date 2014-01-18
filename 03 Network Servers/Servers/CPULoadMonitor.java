import java.lang.management.ManagementFactory;

/*
 * This is a simple algorithm to be used as a plugin for LoadBalancer
 * It simply checks the load average on CPU
 */

public class CPULoadMonitor implements LoadBalancer {

	private final double MAX_CPU_LOAD = 90.00; // Max CPU load of 90%
	
	public CPULoadMonitor() {}
	
	public boolean isSystemOverloaded() {
		// Returns true if CPU % usage is higher than max usage allowed
		double loadAverage = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
		int numCores = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();
		double cpuUsage = (loadAverage / numCores) * 100;
		return (cpuUsage > MAX_CPU_LOAD);
	}
}
