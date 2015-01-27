package com.solt.tscraper.torrent;

import java.net.URI;

import com.solt.tscraper.common.Torrent;
import com.solt.tscraper.common.TorrentState;

public interface ScrapeListener {
	
	public void onTrackerSuccess(Torrent torrent, URI tracker, TorrentState state);
	
	public void onTrackerError(Torrent torrent, URI tracker, Throwable cause);
	
	public void onError(Torrent torrent, Throwable cause);
}
