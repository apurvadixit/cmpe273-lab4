package edu.sjsu.cmpe273.CRDTClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.async.Callback;
import com.mashape.unirest.http.exceptions.UnirestException;

public class CRDTClient {
	private List<DistributedCacheService> cacheserver;
	private CountDownLatch cntdown;
	public CRDTClient() {
		DistributedCacheService cache3000 = new DistributedCacheService(
				"http://localhost:3000");
		DistributedCacheService cache3001 = new DistributedCacheService(
				"http://localhost:3001");
		DistributedCacheService cache3002 = new DistributedCacheService(
				"http://localhost:3002");

		this.cacheserver = new ArrayList<DistributedCacheService>();

		cacheserver.add(cache3000);
		cacheserver.add(cache3001);
		cacheserver.add(cache3002);
	}
	public String get(long key) throws InterruptedException, UnirestException, IOException {
		this.cntdown = new CountDownLatch(cacheserver.size());
		final Map<DistributedCacheService, String> map1 = new HashMap<DistributedCacheService, String>();
		for (final DistributedCacheService cacheServer : cacheserver) {
			Future<HttpResponse<JsonNode>> ff = Unirest.get(cacheServer.getCacheServerUrl() + "/cache/{key}")
					.header("accept", "application/json")
					.routeParam("key", Long.toString(key))
					.asJsonAsync(new Callback<JsonNode>() {

						public void failed(UnirestException e) {
							System.out.println("Request:Failed");
							cntdown.countDown();
						}

						public void completed(HttpResponse<JsonNode> response) {
							map1.put(cacheServer, response.getBody().getObject().getString("value"));
							System.out.println("Request:Success"+cacheServer.getCacheServerUrl());
							cntdown.countDown();
						}

						public void cancelled() {
							System.out.println("Request:Cancel");
							cntdown.countDown();
						}
				});
		}
		this.cntdown.await(3, TimeUnit.SECONDS);
		final Map<String, Integer> countMap = new HashMap<String, Integer>();
		int cnt = 0;
		for (String value : map1.values()) {
			int count = 1;
			if (countMap.containsKey(value)) {
				count = countMap.get(value);
				count++;
			}
			if (cnt < count)
				cnt = count;
			countMap.put(value, count);
		}
		System.out.println("cnt "+cnt);
		String value = this.getKeyByValue(countMap, cnt);
		System.out.println("cnt value "+value);
		if (cnt != this.cacheserver.size()) {
			for (Entry<DistributedCacheService, String> cacheServerData : map1.entrySet()) {
				if (!value.equals(cacheServerData.getValue())) {
					System.out.println("Repair this  "+cacheServerData.getKey());
					HttpResponse<JsonNode> response = Unirest.put(cacheServerData.getKey() + "/cache/{key}/{value}")
							.header("accept", "application/json")
							.routeParam("key", Long.toString(key))
							.routeParam("value", value)
							.asJson();
				}
			}
			for (DistributedCacheService cacheServer : this.cacheserver) {
				if (map1.containsKey(cacheServer)) continue;
				System.out.println("Repair this "+cacheServer.getCacheServerUrl());
				HttpResponse<JsonNode> response = Unirest.put(cacheServer.getCacheServerUrl() + "/cache/{key}/{value}")
						.header("accept", "application/json")
						.routeParam("key", Long.toString(key))
						.routeParam("value", value)
						.asJson();
			}
		} else {
			System.out.println("No repair");
		}
		Unirest.shutdown();
		return value;
	}
	public boolean put(long key, String value) throws InterruptedException, IOException {
		final AtomicInteger completedCount = new AtomicInteger(0);
		this.cntdown = new CountDownLatch(cacheserver.size());
		final ArrayList<DistributedCacheService> writeser = new ArrayList<DistributedCacheService>(3);
		for (final DistributedCacheService cacheServer : cacheserver) {
			Future<HttpResponse<JsonNode>> ff = Unirest.put(cacheServer.getCacheServerUrl()+ "/cache/{key}/{value}")
					.header("accept", "application/json")
					.routeParam("key", Long.toString(key))
					.routeParam("value", value)
					.asJsonAsync(new Callback<JsonNode>() {

						public void failed(UnirestException e) {
							System.out.println("Fail"+cacheServer.getCacheServerUrl());
							cntdown.countDown();
						}

						public void completed(HttpResponse<JsonNode> response) {
							int count = completedCount.incrementAndGet();
							writeser.add(cacheServer);
							System.out.println("Success""+cacheServer.getCacheServerUrl());
							cntdown.countDown();
						}

						public void cancelled() {
							System.out.println("Cancel the request");
							cntdown.countDown();
						}

					});
		}
		this.cntdown.await();
		if (completedCount.intValue() > 1) {
			return true;
		} else {
			
			this.cntdown = new CountDownLatch(writeser.size());
			for (final DistributedCacheService cacheServer : writeser) {
				Future<HttpResponse<JsonNode>> ff = Unirest.get(cacheServer.getCacheServerUrl() + "/cache/{key}")
						.header("accept", "application/json")
						.routeParam("key", Long.toString(key))
						.asJsonAsync(new Callback<JsonNode>() {

							public void failed(UnirestException e) {
								System.out.println("Delete:fail"+cacheServer.getCacheServerUrl());
								cntdown.countDown();
							}

							public void completed(HttpResponse<JsonNode> response) {
								System.out.println("Delete:success"+cacheServer.getCacheServerUrl());
								cntdown.countDown();
							}

							public void cancelled() {
								System.out.println("Cancel req");
								cntdown.countDown();
							}
					});
			}
			this.cntdown.await(3, TimeUnit.SECONDS);
			Unirest.shutdown();
			return false;
		}
	}

	public String getKeyByValue(Map<String, Integer> map, int value) {
		for (Entry<String, Integer> entry : map.entrySet()) {
			if (value == entry.getValue()) return entry.getKey();
		}
		return null;
	}
}