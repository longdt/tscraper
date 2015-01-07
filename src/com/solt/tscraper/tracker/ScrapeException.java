/*
 * Created on Jul 12, 2004
 */
package com.solt.tscraper.tracker;

/**
 * Thrown if the server does not support the Tracker Scrape Convention.
 * See http://groups.yahoo.com/group/BitTorrent/message/3275
 * 
 * @author Larry Williams
 *  
 */
public class ScrapeException extends RuntimeException {

    /**
     * @param arg0
     */
    public ScrapeException(String message) {
        super(message);
    }
    
    public ScrapeException(String message, Throwable e) {
    	super(message, e);
	}
}