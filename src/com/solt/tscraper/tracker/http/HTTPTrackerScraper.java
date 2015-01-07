package com.solt.tscraper.tracker.http;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import com.solt.tscraper.common.Torrent;
import com.solt.tscraper.common.TorrentState;
import com.solt.tscraper.tracker.ScrapeException;
import com.solt.tscraper.tracker.TrackerScraper;

public class HTTPTrackerScraper extends TrackerScraper {
	private URL scrapeUrl;
	public HTTPTrackerScraper(Torrent torrent, URI announce) {
		super(torrent, announce);
	}

	@Override
	public TorrentState scrape() {
		return null;
	}
	
	   /**
     * Retrieves the scrape url for the tracker.
     * 
     * Taken from http://groups.yahoo.com/group/BitTorrent/message/3275
     * 
     * Take the tracker url. Find the last '/' in it. If the text immediately
     * following that '/' isn't 'announce' it will be taken as a sign that that
     * tracker doesn't support the scrape convention. If it does, substitute
     * 'scrape' for 'announce' to find the scrape page.
     * 
     * @return
	 * @throws MalformedURLException 
     */
    public URL getScrapeUrl() throws MalformedURLException {
    	if (scrapeUrl != null) {
    		return scrapeUrl;
    	}
    	String announce = this.announce.toString();
        int slashIndex = announce.lastIndexOf('/');
        if (slashIndex == -1) {
            throw new ScrapeException(
                    "Could not find a / in the announce URL '" + announce
                            + "'");
        }

        int announceIndex = announce.indexOf("announce", slashIndex);
        if (announceIndex == -1) {
            throw new ScrapeException(
                    "Could not find 'announce' after the last / in the announce URL '"
                            + announce + "'");
        }

        if ((slashIndex == (announceIndex - 1)) == false) {
            throw new ScrapeException(
                    "Could not find 'announce' after the last / in the announce URL '"
                            + announce + "'");
        }

        // Get text including the last slash
        String scrapeLink = announce.substring(0, slashIndex + 1);
        // Replace the announce with scrape
        scrapeLink += "scrape";
        // Get text after announce
        scrapeLink += announce.substring(
                announceIndex + "announce".length(), announce.length());
        this.scrapeUrl = new URL(scrapeLink);
        return scrapeUrl;
    }

}
