package com.solt.tscraper.tracker.udp;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import com.solt.tscraper.common.Torrent;

public class ErrorResponse extends TrackerResponse {
	private String message;

	public ErrorResponse(int actionId, int transactionId, String message) {
		super(actionId, transactionId);
		this.message = message;
	}

	public String getMessage() {
		return message;
	}
	
	public static ErrorResponse parse(ByteBuffer data) throws InvalidResponseException {
		if (data.remaining() < 8) {
			throw new InvalidResponseException("Invalid packet size!");
		}
		int actionId = data.getInt();
		if (actionId != Action.ERROR) {
			throw new InvalidResponseException("Invalid action code for connection response!");
		}
		int transactionId = data.getInt();
		byte[] reasonBytes = new byte[data.remaining()];
		data.get(reasonBytes);
		try {
			return new ErrorResponse(actionId, transactionId, new String(reasonBytes, Torrent.BYTE_ENCODING));
		} catch (UnsupportedEncodingException e) {
			throw new InvalidResponseException("Could not decode error message!", e);
		}
	}

}
