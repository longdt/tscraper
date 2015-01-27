package com.solt.tscraper.tracker.udp;

import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class ConnectionCache {
	private static ConnectionCache instance = new ConnectionCache();
	private Cache<SocketAddress, Long> cache;
	private ConnectionCache() {
		cache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();
	}
	
	public static ConnectionCache getInstance() {
		return instance;
	}
	
	public Long get(SocketAddress address) {
		return cache.getIfPresent(address);
	}
	
	public void put(SocketAddress address, long connId) {
		cache.put(address, connId);
	}
}
