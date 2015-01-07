package com.solt.tscraper.tracker.udp;

import java.nio.ByteBuffer;

public class ConnectResponse extends TrackerResponse {
	private long connectionId;

	public ConnectResponse(long connectionId, int actionId, int transactionId) {
		super(actionId, transactionId);
		this.connectionId = connectionId;
	}

	public long getConnectionId() {
		return connectionId;
	}
	
	public static ConnectResponse parse(ByteBuffer data) throws InvalidResponseException {
		if (data.remaining() != 16) {
			throw new InvalidResponseException("Invalid packet size!");
		}
		int actionId = data.getInt();
		if (actionId != Action.CONNECT) {
			throw new InvalidResponseException("Invalid action code for connection response!");
		}
		int transactionId = data.getInt();
		long connectionId = data.getLong();
		return new ConnectResponse(connectionId, actionId, transactionId);
	}
}
