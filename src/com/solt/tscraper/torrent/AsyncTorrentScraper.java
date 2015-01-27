package com.solt.tscraper.torrent;

import java.util.concurrent.Future;

import com.solt.tscraper.common.TorrentState;

public interface AsyncTorrentScraper {
	
	public Future<TorrentState> scrape();

	public void addScrapeListener(ScrapeListener listener);
}
