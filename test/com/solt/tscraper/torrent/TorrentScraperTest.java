package com.solt.tscraper.torrent;

import com.solt.tscraper.common.Torrent;
import com.solt.tscraper.common.TorrentState;
import com.solt.tscraper.torrent.impl.TorrentScraperImpl;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class TorrentScraperTest {

    @org.junit.jupiter.api.Test
    void scrape() throws IOException {
        byte[] torrent = Files.readAllBytes(Paths.get("[SubsPlease] Spy x Family - 08 (1080p) [E1866698].mkv.torrent"));
        TorrentScraper torrentScraper = new TorrentScraperImpl(new Torrent(torrent, false));
        torrentScraper.addScrapeListener(new ScrapeListener() {
            @Override
            public void onTrackerSuccess(Torrent torrent, URI tracker, TorrentState state) {
                System.out.println("tracker: " + tracker + "\t" + state.getComplete() + "S, " + state.getIncomplete() + "L");
            }

            @Override
            public void onTrackerError(Torrent torrent, URI tracker, Throwable cause) {
                System.out.println("tracker: " + tracker + "\t" + cause.getMessage());
            }

            @Override
            public void onError(Torrent torrent, Throwable cause) {
                System.out.println("torrent error:\t" + cause.getMessage());
            }
        });
        torrentScraper.scrape();
    }
}