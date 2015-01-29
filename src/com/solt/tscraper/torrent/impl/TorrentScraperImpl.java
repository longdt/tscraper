package com.solt.tscraper.torrent.impl;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

import com.solt.tscraper.common.Torrent;
import com.solt.tscraper.common.TorrentState;
import com.solt.tscraper.torrent.ScrapeListener;
import com.solt.tscraper.torrent.TorrentScraper;
import com.solt.tscraper.tracker.ScrapeException;
import com.solt.tscraper.tracker.http.HTTPTrackerScraper;
import com.solt.tscraper.tracker.udp.ConnectResponse;
import com.solt.tscraper.tracker.udp.ConnectionCache;
import com.solt.tscraper.tracker.udp.ErrorResponse;
import com.solt.tscraper.tracker.udp.ScrapeResponse;
import com.solt.tscraper.tracker.udp.TrackerResponse;
import com.solt.tscraper.tracker.udp.TrackerResponse.InvalidResponseException;
import com.solt.tscraper.tracker.udp.UDPTrackerScraper;
import com.solt.tscraper.util.Pair;

public class TorrentScraperImpl implements TorrentScraper {
	private ConnectionCache connCache;
	private Torrent torrent;
	private List<ScrapeListener> listeners;
	private Set<URI> httpTrackers;
	private Map<InetSocketAddress, State> udpTrackers;

	static class State {
		private static final Random rand = new Random();
		private long connectionId;
		private int transactionId;
		private URI tracker;

		public State(URI tracker) {
			this(tracker, UDPTrackerScraper.DEFAULT_CONNECTION_ID);
		}

		public State(URI tracker, long connectionId) {
			transactionId = rand.nextInt();
			this.connectionId = connectionId;
			this.tracker = tracker;
		}

		public int getTransactionId() {
			return transactionId;
		}

		public int newTransactionId() {
			transactionId = rand.nextInt();
			return transactionId;
		}

		public long getConnectionId() {
			return connectionId;
		}

		public void setConnectionId(long connectionId) {
			this.connectionId = connectionId;
		}

		public boolean isConnecting() {
			return connectionId == UDPTrackerScraper.DEFAULT_CONNECTION_ID;
		}

		public URI getTracker() {
			return tracker;
		}
	}

	public TorrentScraperImpl(Torrent torrent) {
		this.torrent = torrent;
		listeners = new ArrayList<>();
		httpTrackers = new HashSet<>();
		udpTrackers = new HashMap<>();
		connCache = ConnectionCache.getInstance();
	}

	private void loadTrackers() {
		List<List<URI>> trackerTiers = torrent.getAnnounceList();
		for (List<URI> tier : trackerTiers) {
			for (URI tracker : tier) {
				String protocol = tracker.getScheme().toLowerCase();
				if (protocol.equals("udp")) {
					udpTrackers.put(new InetSocketAddress(tracker.getHost(),
							tracker.getPort()), new State(tracker));
				} else if (protocol.equals("http")) {
					httpTrackers.add(tracker);
				} else {
					fireTrackerErrorEvent(tracker, new ScrapeException(
							"Doesn't support protocol of tracker: " + tracker));
				}
			}
		}
	}

	@Override
	public TorrentState scrape() {
		loadTrackers();
		try {
			DatagramChannel dataChannel = DatagramChannel.open();
			dataChannel.configureBlocking(false);
			dataChannel.bind(null);
			Selector selector = Selector.open();
			initUDPRequest(dataChannel, selector);
			initTCPRequest(selector);
			loopIO(selector);
			closeIOResources(selector);
		} catch (IOException e) {
			fireErrorEvent(e);
		}
		return null;
	}

	private void closeIOResources(Selector selector) throws IOException {
		Set<SelectionKey> keys = selector.keys();
		for (SelectionKey key : keys) {
			key.cancel();
			key.channel().close();
		}
		selector.close();
	}

	private void initTCPRequest(Selector selector) throws IOException {
		int port = -1;
		URL tracker = null;
		String request = null;
		for (URI announce : httpTrackers) {
			SocketChannel socketChannel = SocketChannel.open();
			socketChannel.configureBlocking(false);
			tracker = HTTPTrackerScraper.buildScrapeRequestUrl(announce, torrent);
			port = tracker.getPort() == -1 ? tracker.getDefaultPort() : tracker.getPort();
			socketChannel
					.connect(new InetSocketAddress(tracker.getHost(), port));
			request = "GET " + tracker.getPath() + "?" + tracker.getQuery() + " HTTP/1.1\r\n"
					+ "Host: torviet.com\r\n"
					+ "Accept: text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2\r\n"
					+ "Connection: keep-alive\r\n"
					+ "\r\n";
			Pair<URI, ByteBuffer> att = Pair.of(announce, ByteBuffer.wrap(request.getBytes()));
			socketChannel.register(selector, SelectionKey.OP_CONNECT, att);
		}
	}
	
	private void finishConnection(SelectionKey key) throws IOException {
		SocketChannel socketChannel = (SocketChannel) key.channel();
	
		// Finish the connection. If the connection operation failed
		// this will raise an IOException.
		try {
			socketChannel.finishConnect();
		} catch (IOException e) {
			// Cancel the channel's registration with our selector
			System.out.println(e);
			key.cancel();
			throw e;
		}
	
		// Register an interest in writing on this channel
		key.interestOps(SelectionKey.OP_WRITE);
	}

	private void loopIO(Selector selector) throws IOException {
		while (!udpTrackers.isEmpty() || !httpTrackers.isEmpty()) {
			int readyChannels = selector.select(5000);
			if (readyChannels == 0)
				break;

			Set<SelectionKey> selectedKeys = selector.selectedKeys();
			Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
			while (keyIterator.hasNext()) {
				SelectionKey key = keyIterator.next();
				keyIterator.remove();
				if (!key.isValid()) {
					continue;
				}

				if (key.isReadable()) {
					read(key);
				} else if (key.isWritable()) {
					write(key);
				} else if (key.isConnectable()) {
					finishConnection(key);
				}
			}
		}
	}

	private void write(SelectionKey key) throws IOException {
		SelectableChannel channel = key.channel();
		if (channel instanceof DatagramChannel) {
			writeUDP(key);
		} else if (channel instanceof SocketChannel) {
			writeTCP(key);
		}
	}

	private void writeTCP(SelectionKey key) throws IOException {
		SocketChannel channel = (SocketChannel) key.channel();
		Pair<URI, ByteBuffer> att = (Pair<URI, ByteBuffer>) key.attachment();
		ByteBuffer data = att.getRight();
		channel.write(data);
		if (data.remaining() == 0) {
			key.interestOps(SelectionKey.OP_READ);
		}
	}

	private void writeUDP(SelectionKey key) throws IOException {
		DatagramChannel channel = (DatagramChannel) key.channel();
		Queue<Pair<ByteBuffer, SocketAddress>> writeQueue = (Queue<Pair<ByteBuffer, SocketAddress>>) key
				.attachment();
		Pair<ByteBuffer, SocketAddress> writeTask = null;
		ByteBuffer buf = null;
		while ((writeTask = writeQueue.peek()) != null) {
			buf = writeTask.getLeft();
			channel.send(buf, writeTask.getRight());
			if (buf.remaining() > 0) {
				// ... or the socket's buffer fills up
				break;
			}
			writeQueue.poll();
		}
		if (writeQueue.isEmpty()) {
			key.interestOps(SelectionKey.OP_READ);
		}
	}

	private void read(SelectionKey key) throws IOException {
		SelectableChannel channel = key.channel();
		if (channel instanceof DatagramChannel) {
			readUDP(key);
		} else if (channel instanceof SocketChannel) {
			readTCP(key);
		}
	}

	private void readTCP(SelectionKey key) throws IOException {
		SocketChannel channel = (SocketChannel) key.channel();
		Pair<URI, ByteBuffer> att = (Pair<URI, ByteBuffer>) key.attachment();
		URI tracker = att.getLeft();
		ByteBuffer buf = ByteBuffer.allocate(1024);
		int numRead = channel.read(buf);
		if (numRead == -1) {
			// Remote entity shut the socket down cleanly. Do the
			// same from our end and cancel the channel.
			channel.close();
			key.cancel();
			httpTrackers.remove(tracker);
			return;
		}
		// TODO debug print content response http
		int offset = 4;
		for (; offset < numRead; ++offset) {
			if (buf.get(offset - 4) == '\r' && buf.get(offset - 3) == '\n' && buf.get(offset - 2) == '\r' && buf.get(offset - 1) == '\n'
					) {
				break;
			}
		}
		buf.position(offset);
		buf.limit(numRead);
		TorrentState torState = HTTPTrackerScraper.parseScrapeResponse(buf);
		fireTrackerSuccess(torrent, tracker, torState);
		channel.close();
		key.cancel();
		httpTrackers.remove(tracker);
	}

	private void readUDP(SelectionKey key) throws IOException {
		DatagramChannel channel = (DatagramChannel) key.channel();
		Queue<Pair<ByteBuffer, SocketAddress>> writeQueue = (Queue<Pair<ByteBuffer, SocketAddress>>) key
				.attachment();
		ByteBuffer buffer = ByteBuffer
				.allocate(UDPTrackerScraper.UDP_PACKET_LENGTH);
		SocketAddress address = channel.receive(buffer);
		buffer.flip();
		State state = udpTrackers.get(address);
		if (state == null) {
			return;
		}
		try {
			TrackerResponse response = TrackerResponse.parse(buffer);
			if (response.getTransactionId() != state.getTransactionId()) {
				// invalid transaction id
				return;
			}
			if (response instanceof ErrorResponse) {
				fireTrackerErrorEvent(state.getTracker(), new ScrapeException(
						((ErrorResponse) response).getMessage()));
			} else if (response instanceof ConnectResponse) {
				long connectionId = ((ConnectResponse) response)
						.getConnectionId();
				connCache.put(address, connectionId);
				state.setConnectionId(connectionId);
				ByteBuffer writeData = UDPTrackerScraper.createScrapeRequest(
						connectionId, state.newTransactionId(), torrent);
				writeQueue.add(Pair.of(writeData, address));
				key.interestOps(SelectionKey.OP_WRITE);
			} else if (response instanceof ScrapeResponse) {
				ScrapeResponse scrapeResp = (ScrapeResponse) response;
				TorrentState torState = new TorrentState(
						scrapeResp.getComplete(), scrapeResp.getDownloaded(),
						scrapeResp.getIncomplete());
				fireTrackerSuccess(torrent, state.getTracker(), torState);
			}
		} catch (InvalidResponseException e) {
			fireTrackerErrorEvent(state.getTracker(), e);
		}
	}

	private void initUDPRequest(DatagramChannel dataChannel, Selector selector)
			throws IOException {
		Queue<Pair<ByteBuffer, SocketAddress>> writeQueue = new LinkedList<>();
		for (Entry<InetSocketAddress, State> addressState : udpTrackers
				.entrySet()) {
			InetSocketAddress address = addressState.getKey();
			State state = addressState.getValue();
			Long connectionId = connCache.get(addressState.getKey());
			ByteBuffer buffer = null;
			if (connectionId != null) {
				state.setConnectionId(connectionId);
				// create scrape request
				buffer = UDPTrackerScraper.createScrapeRequest(connectionId,
						state.getTransactionId(), torrent);
			} else {
				// create connect request
				buffer = UDPTrackerScraper.createConnectRequest(state
						.getTransactionId());
			}
			// send request
			try {
				if (dataChannel.send(buffer, address) == 0) {
					writeQueue.add(Pair.of(buffer, (SocketAddress)address));
				}
			} catch (UnresolvedAddressException e) {
				e.printStackTrace();
			}
		}
		int ops = writeQueue.isEmpty() ? SelectionKey.OP_READ
				: SelectionKey.OP_READ | SelectionKey.OP_WRITE;
		dataChannel.register(selector, ops, writeQueue);
	}

	private void fireTrackerSuccess(Torrent torrent, URI tracker,
			TorrentState state) {
		for (ScrapeListener listener : listeners) {
			listener.onTrackerSuccess(torrent, tracker, state);
		}
	}

	private void fireTrackerErrorEvent(URI tracker, Throwable e) {
		for (ScrapeListener listener : listeners) {
			listener.onTrackerError(torrent, tracker, e);
		}
	}

	private void fireErrorEvent(Throwable e) {
		for (ScrapeListener listener : listeners) {
			listener.onError(torrent, e);
		}
	}

	@Override
	public void addScrapeListener(ScrapeListener listener) {
		listeners.add(listener);
	}

}
