package com.solt.tscraper.tracker.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import com.solt.tscraper.bcodec.BDecoder;
import com.solt.tscraper.bcodec.BEValue;
import com.solt.tscraper.common.Torrent;
import com.solt.tscraper.common.TorrentState;
import com.solt.tscraper.tracker.ScrapeException;
import com.solt.tscraper.tracker.TrackerScraper;

public class HTTPTrackerScraper extends TrackerScraper {
	private URL scrapeUrl;
	public HTTPTrackerScraper(Torrent torrent, URI announce) {
		super(torrent, announce);
	}
	
	private URL buildScrapeRequestUrl() throws MalformedURLException, UnsupportedEncodingException {
		String base = getScrapeUrl().toString();
		StringBuilder url = new StringBuilder(base);
		url.append(base.contains("?") ? "&" : "?")
			.append("info_hash=")
			.append(URLEncoder.encode(
				new String(torrent.getInfoHash(), Torrent.BYTE_ENCODING),
				Torrent.BYTE_ENCODING));
		return new URL(url.toString());
	}

	@Override
	public TorrentState scrape() {
		HttpURLConnection conn = null;
		InputStream in = null;
		try {
			URL target = buildScrapeRequestUrl();
			conn = (HttpURLConnection)target .openConnection();
			in = conn.getInputStream();
		} catch (IOException ioe) {
			if (conn != null) {
				in = conn.getErrorStream();
			}
		}
		// At this point if the input stream is null it means we have neither a
		// response body nor an error stream from the server. No point in going
		// any further.
		if (in == null) {
			throw new ScrapeException("No response or unreachable tracker!");
		}
		try {
			byte[] data = IOUtils.toByteArray(in);
			BEValue decoded = BDecoder.bdecode(ByteBuffer.wrap(data));
			if (decoded == null) {
				throw new ScrapeException(
					"Could not decode tracker message (not B-encoded?)!");
			}
			Map<String, BEValue> params = decoded.getMap();
			BEValue failure = params.get("failure reason");
			if (failure != null) {
				throw new ScrapeException("failure reason from tracker: " + failure.getString());
			}
			BEValue files = params.get("files");
			Map<String, BEValue> infos = files.getMap().values().iterator().next().getMap();
			int compelete = infos.get("complete").getInt();
			int downloaded = infos.get("downloaded").getInt();
			int incomplete = infos.get("incomplete").getInt();
			return new TorrentState(compelete, downloaded, incomplete);
		} catch (IOException e) {
			e.printStackTrace();
			return new TorrentState(0, 0, 0);
		} finally {
			try {
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			InputStream err = conn.getErrorStream();
			try {
				if (err != null) {
					err.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
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
    private URL getScrapeUrl() throws MalformedURLException {
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
