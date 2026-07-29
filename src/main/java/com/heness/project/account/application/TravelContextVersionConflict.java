package com.heness.project.account.application;

public final class TravelContextVersionConflict extends RuntimeException {
	private final long latestVersion;

	public TravelContextVersionConflict(long latestVersion) {
		super("TRAVEL_CONTEXT_VERSION_CONFLICT");
		this.latestVersion = latestVersion;
	}

	public long latestVersion() {
		return latestVersion;
	}
}
