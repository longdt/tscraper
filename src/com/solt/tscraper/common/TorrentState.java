/*
 * Created on Jul 11, 2004
 */
package com.solt.tscraper.common;

/**
 * Represents the information retrieved by doing a Torrent Scrape. This
 * information contains the number of seeds (complete) and peers (incomplete).
 * 
 * @author Larry Williams
 *  
 */
public class TorrentState {
    int complete;

    int downloaded;

    int incomplete;

    String name;

    public TorrentState(int complete, int downloaded, int incomplete) {
    	this.complete = complete;
    	this.downloaded = downloaded;
    	this.incomplete = incomplete;
    }

	/**
     * @return Returns the number of peers with the entire file, i.e. seeders.
     */
    public int getComplete() {
        return complete;
    }

    /**
     * @param complete
     *            The complete to set.
     */
    public void setComplete(int complete) {
        this.complete = complete;
    }

    /**
     * @return Returns the total number of times the tracker has registered a
     *         completion ("event=complete", i.e. a client finished downloading
     *         the torrent.
     */
    public int getDownloaded() {
        return downloaded;
    }

    /**
     * @param downloaded
     *            The downloaded to set.
     */
    public void setDownloaded(int downloaded) {
        this.downloaded = downloaded;
    }

    /**
     * @return Returns the number of non-seeder peers, aka "leechers".
     */
    public int getIncomplete() {
        return incomplete;
    }

    /**
     * @param incomplete
     *            The incomplete to set.
     */
    public void setIncomplete(int incomplete) {
        this.incomplete = incomplete;
    }

    /**
     * @return Returns the torrent's internal name, as specified by the "name"
     *         file in the info section of the .torrent file.
     */
    public String getName() {
        return name;
    }

    /**
     * @param name
     *            The name to set.
     */
    public void setName(String name) {
        this.name = name;
    }
}