package com.solt.tscraper;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.List;

import org.apache.commons.io.IOUtils;

import com.solt.tscraper.common.Torrent;
import com.solt.tscraper.common.TorrentState;
import com.solt.tscraper.tracker.TrackerScraper;

public class TScraper {

	public static void main(String[] args) throws IOException {
		URL torUrl = new URL("http://sharephim.vn/api/movie/800");
		byte[] torBytes = IOUtils.toByteArray(torUrl);
		Torrent torrent = new Torrent(torBytes, false);
		List<List<URI>> trackers = torrent.getAnnounceList();
		for (List<URI> trackerTier : trackers) {
			for (URI tracker : trackerTier) {
				TrackerScraper scraper = TrackerScraper.create(torrent, tracker);
				TorrentState state = scraper.scrape();
				scraper.close();
				System.out.println(tracker + ":\tcomplete: " + state.getComplete() + "\tdownloaded: " + state.getDownloaded() + "\tincomplete: " + state.getIncomplete());
			}
		}
	}

}
