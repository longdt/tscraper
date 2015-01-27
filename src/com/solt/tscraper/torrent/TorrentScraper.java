package com.solt.tscraper.torrent;

import com.solt.tscraper.common.TorrentState;

public interface TorrentScraper {
	public TorrentState scrape();
	
	public void addScrapeListener(ScrapeListener listener);
}
