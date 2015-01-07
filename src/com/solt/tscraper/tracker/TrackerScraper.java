package com.solt.tscraper.tracker;

import java.net.URI;

import com.solt.tscraper.common.Torrent;
import com.solt.tscraper.common.TorrentState;
import com.solt.tscraper.tracker.http.HTTPTrackerScraper;
import com.solt.tscraper.tracker.udp.UDPTrackerScraper;

public abstract class TrackerScraper {
	protected URI announce;
	protected Torrent torrent;
	
	public TrackerScraper(Torrent torrent, URI announce) {
		this.torrent = torrent;
		this.announce = announce;
	}
	
	public abstract TorrentState scrape();

	public URI getAnnounce() {
		return announce;
	}

	public void setAnnounce(URI announce) {
		this.announce = announce;
	}
	
	public void close() {
		
	}
	
	public static TrackerScraper create(Torrent torrent, URI announce) {
		String protocol = announce.getScheme().toLowerCase();
		if (protocol.equals("http")) {
			return new HTTPTrackerScraper(torrent, announce);
		} else if (protocol.equals("udp")) {
			return new UDPTrackerScraper(torrent, announce);
		} else {
			return null;
		}
	}
}
