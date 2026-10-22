package de.zft2.gbanking.hbci;

import java.util.Properties;

import org.kapott.hbci.manager.HBCIHandler;

/**
 * Central helper for the check whether a bank access supports a lowlevel HBCI job.
 *
 * <p>
 * The supported jobs are reported by the bank as a property collection. The collection is
 * {@code null} if the bank did not report any job at all, which is handled like "no job
 * supported" by all callers.
 * </p>
 */
public final class HbciJobSupport {

	private HbciJobSupport() {
	}

	/**
	 * Checks whether the given lowlevel job is part of the supported jobs.
	 *
	 * @param supportedJobs the supported jobs of the bank access, may be {@code null}
	 * @param jobName       the name of the lowlevel job
	 * @return {@code true} if the job is supported
	 */
	public static boolean isSupported(Properties supportedJobs, String jobName) {
		return supportedJobs != null && supportedJobs.containsKey(jobName);
	}

	/**
	 * Checks whether the given handler reports the given lowlevel job as supported.
	 *
	 * @param handler the HBCI handler of the dialog, may be {@code null}
	 * @param jobName the name of the lowlevel job
	 * @return {@code true} if the job is supported
	 */
	public static boolean isSupported(HBCIHandler handler, String jobName) {
		return handler != null && isSupported(handler.getSupportedLowlevelJobs(), jobName);
	}
}
