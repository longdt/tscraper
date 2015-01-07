package com.solt.tscraper.tracker.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.solt.tscraper.common.Torrent;
import com.solt.tscraper.common.TorrentState;
import com.solt.tscraper.tracker.ScrapeException;
import com.solt.tscraper.tracker.TrackerScraper;
import com.solt.tscraper.tracker.udp.TrackerResponse.InvalidResponseException;

public class UDPTrackerScraper extends TrackerScraper {

	protected static final Logger logger = LoggerFactory
			.getLogger(UDPTrackerScraper.class);

	/**
	 * Back-off timeout uses 15 * 2 ^ n formula.
	 */
	private static final int UDP_BASE_TIMEOUT_SECONDS = 15;

	/**
	 * We don't try more than 8 times (3840 seconds, as per the formula defined
	 * for the backing-off timeout.
	 *
	 * @see #UDP_BASE_TIMEOUT_SECONDS
	 */
	private static final int UDP_MAX_TRIES = 8;

	/**
	 * For STOPPED announce event, we don't want to be bothered with waiting
	 * that long. We'll try once and bail-out early.
	 */
	private static final int UDP_MAX_TRIES_ON_STOPPED = 1;

	/**
	 * Maximum UDP packet size expected, in bytes.
	 *
	 * The biggest packet in the exchange is the announce response, which in 20
	 * bytes + 6 bytes per peer. Common numWant is 50, so 20 + 6 * 50 = 320.
	 * With headroom, we'll ask for 512 bytes.
	 */
	private static final int UDP_PACKET_LENGTH = 512;

	private final InetSocketAddress address;
	private final Random random;

	private DatagramSocket socket;
	private Date connectionExpiration;
	private long connectionId;
	private int transactionId;
	private boolean stop;

	private enum State {
		CONNECT_REQUEST, SCRAPE_REQUEST;
	};

	private final static long DEFAULT_CONNECTION_ID = 0x41727101980l;


	public UDPTrackerScraper(Torrent torrent, URI announce) {
		super(torrent, announce);
		this.address = new InetSocketAddress(announce.getHost(),
				announce.getPort());

		this.random = new Random();
		this.stop = false;
	}

	/**
	 * Offset Size Name Value 0 64-bit integer connection_id 0x41727101980 8
	 * 32-bit integer action 0 // connect 12 32-bit integer transaction_id 16
	 */
	private ByteBuffer createConnectRequest() {
		ByteBuffer bBuffer = ByteBuffer.allocate(16);
		bBuffer.putLong(DEFAULT_CONNECTION_ID);
		bBuffer.putInt(Action.CONNECT);
		bBuffer.putInt(transactionId);
		return bBuffer;
	}

	private ByteBuffer createScrapeRequest() {
		ByteBuffer bBuffer = ByteBuffer.allocate(36);
		bBuffer.putLong(connectionId);
		bBuffer.putInt(Action.SCRAPE);
		bBuffer.putInt(transactionId);
		bBuffer.put(torrent.getInfoHash());
		return bBuffer;
	}

	private void handleConnectResponse(TrackerResponse response) {
		this.validateTrackerResponse(response);

		if (!(response instanceof ConnectResponse)) {
			throw new ScrapeException("Unexpected tracker message type "
					+ response.getActionId() + "!");
		}

		this.connectionId = ((ConnectResponse) response).getConnectionId();
		Calendar now = Calendar.getInstance();
		now.add(Calendar.MINUTE, 1);
		this.connectionExpiration = now.getTime();
	}

	private TorrentState handleScrapeResponse(TrackerResponse response) {
		this.validateTrackerResponse(response);

		if (!(response instanceof ScrapeResponse)) {
			throw new ScrapeException("Unexpected tracker message type "
					+ response.getActionId() + "!");
		}
		ScrapeResponse scrapeResp = (ScrapeResponse) response;
		TorrentState state = new TorrentState(scrapeResp.getComplete(),
				scrapeResp.getDownloaded(), scrapeResp.getIncomplete());
		return state;
	}

	@Override
	public TorrentState scrape() {
		State state = State.CONNECT_REQUEST;
		int maxAttempts = UDP_MAX_TRIES;
		int attempts = -1;

		try {
			this.socket = new DatagramSocket();
			this.socket.connect(this.address);

			while (++attempts <= maxAttempts) {
				// Transaction ID is randomized for each exchange.
				this.transactionId = this.random.nextInt();

				// Immediately decide if we can send the announce request
				// directly or not. For this, we need a valid, non-expired
				// connection ID.
				if (this.connectionExpiration != null) {
					if (new Date().before(this.connectionExpiration)) {
						state = State.SCRAPE_REQUEST;
					} else {
						logger.debug("Announce connection ID expired, "
								+ "reconnecting with tracker...");
					}
				}

				switch (state) {
				case CONNECT_REQUEST:
					send(createConnectRequest());

					try {
						this.handleConnectResponse(TrackerResponse.parse(this
								.recv(attempts)));
						attempts = -1;
					} catch (SocketTimeoutException ste) {
						// Silently ignore the timeout and retry with a
						// longer timeout, unless announce stop was
						// requested in which case we need to exit right
						// away.
						if (stop) {
							return null;
						}
					}
					break;

				case SCRAPE_REQUEST:
					this.send(createScrapeRequest());

					try {
						return handleScrapeResponse(TrackerResponse
								.parse(recv(attempts)));
						// If we got here, we succesfully completed this
						// announce exchange and can simply return to exit the
						// loop.
					} catch (SocketTimeoutException ste) {
						// Silently ignore the timeout and retry with a
						// longer timeout, unless announce stop was
						// requested in which case we need to exit right
						// away.
						if (stop) {
							return null;
						}
					}
					break;
				default:
					throw new IllegalStateException("Invalid announce state!");
				}
			}

			// When the maximum number of attempts was reached, the announce
			// really timed-out. We'll try again in the next announce loop.
			throw new ScrapeException("Timeout while scraping to tracker!");
		} catch (IOException ioe) {
			throw new ScrapeException("Error while announcing to tracker: "
					+ ioe.getMessage(), ioe);
		} catch (InvalidResponseException mve) {
			throw new ScrapeException("Tracker message violates expected "
					+ "protocol (" + mve.getMessage() + ")", mve);
		}
	}

	/**
	 * Close this announce connection.
	 */
	@Override
	public void close() {
		this.stop = true;

		// Close the socket to force blocking operations to return.
		if (this.socket != null && !this.socket.isClosed()) {
			this.socket.close();
		}
	}

	/**
	 * Validates an incoming tracker message.
	 *
	 * <p>
	 * Verifies that the message is not an error message (throws an exception
	 * with the error message if it is) and that the transaction ID matches the
	 * current one.
	 * </p>
	 *
	 * @param response
	 *            The incoming tracker message.
	 */
	private void validateTrackerResponse(TrackerResponse response)
			throws ScrapeException {
		if (response instanceof ErrorResponse) {
			throw new ScrapeException(((ErrorResponse) response).getMessage());
		}

		if (response.getTransactionId() != this.transactionId) {
			throw new ScrapeException("Invalid transaction ID!");
		}
	}

	/**
	 * Send a UDP packet to the tracker.
	 *
	 * @param data
	 *            The {@link ByteBuffer} to send in a datagram packet to the
	 *            tracker.
	 */
	private void send(ByteBuffer data) {
		try {
			this.socket.send(new DatagramPacket(data.array(), data.capacity(),
					this.address));
		} catch (IOException ioe) {
			logger.warn("Error sending datagram packet to tracker at {}: {}.",
					this.address, ioe.getMessage());
		}
	}

	/**
	 * Receive a UDP packet from the tracker.
	 *
	 * @param attempt
	 *            The attempt number, used to calculate the timeout for the
	 *            receive operation.
	 * @return Returns a {@link ByteBuffer} containing the packet data.
	 */
	private ByteBuffer recv(int attempt) throws IOException, SocketException,
			SocketTimeoutException {
		int timeout = UDP_BASE_TIMEOUT_SECONDS * (int) Math.pow(2, attempt);
		logger.trace("Setting receive timeout to {}s for attempt {}...",
				timeout, attempt);
		this.socket.setSoTimeout(timeout * 1000);

		try {
			DatagramPacket p = new DatagramPacket(new byte[UDP_PACKET_LENGTH],
					UDP_PACKET_LENGTH);
			this.socket.receive(p);
			return ByteBuffer.wrap(p.getData(), 0, p.getLength());
		} catch (SocketTimeoutException ste) {
			throw ste;
		}
	}

}
