package com.solt.tscraper.tracker.udp;

import java.nio.ByteBuffer;

import javax.activity.InvalidActivityException;

public abstract class TrackerResponse {
	private static final int UDP_MIN_RESPONSE_PACKET_SIZE = 8;
	protected int actionId;
	protected int transactionId;

	public TrackerResponse(int actionId, int transactionId) {
		this.actionId = actionId;
		this.transactionId = transactionId;
	}
	
	public int getActionId() {
		return actionId;
	}

	public int getTransactionId() {
		return transactionId;
	}
	
	public static TrackerResponse parse(ByteBuffer data) throws InvalidResponseException {
		if (data.remaining() < UDP_MIN_RESPONSE_PACKET_SIZE) {
			throw new InvalidResponseException("Invalid packet size!");
		}

		/**
		 * UDP response packets always start with the action (4 bytes), so
		 * we can extract it immediately.
		 */
		data.mark();
		int action = data.getInt();
		data.reset();
		if (action == Action.CONNECT) {
			return ConnectResponse.parse(data);
		} else if (action == Action.SCRAPE) {
			return ScrapeResponse.parse(data);
		} else if (action == Action.ERROR) {
			return ErrorResponse.parse(data);
		}
		throw new InvalidResponseException("Unknown UDP tracker " +
				"response message!");
	}
	
	public static class InvalidResponseException extends Exception {
		public InvalidResponseException(String msg) {
			super(msg);
		}
		
		public InvalidResponseException(String msg, Throwable cause) {
			super(msg, cause);
		}
	}
}
