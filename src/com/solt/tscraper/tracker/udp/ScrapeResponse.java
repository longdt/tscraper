package com.solt.tscraper.tracker.udp;

import java.nio.ByteBuffer;

import com.solt.tscraper.tracker.udp.TrackerResponse.InvalidResponseException;

public class ScrapeResponse extends TrackerResponse {
	private int complete;
	private int downloaded;
	private int incomplete;
	
	public ScrapeResponse(int actionId, int transactionId, int complete, int downloaded, int incomplete) {
		super(actionId, transactionId);
		this.complete = complete;
		this.downloaded = downloaded;
		this.incomplete = incomplete;
	}

	public int getComplete() {
		return complete;
	}

	public int getDownloaded() {
		return downloaded;
	}

	public int getIncomplete() {
		return incomplete;
	}
	
	public static ScrapeResponse parse(ByteBuffer data) throws InvalidResponseException {
		if (data.remaining() != 20) {
			throw new InvalidResponseException("Invalid packet size!");
		}
		int actionId = data.getInt();
		if (actionId != Action.SCRAPE) {
			throw new InvalidResponseException("Invalid action code for connection response!");
		}
		int transactionId = data.getInt();
		int complete = data.getInt();
		int downloaded = data.getInt();
		int incomplete = data.getInt();
		return new ScrapeResponse(actionId, transactionId, complete, downloaded, incomplete);
	}

}
