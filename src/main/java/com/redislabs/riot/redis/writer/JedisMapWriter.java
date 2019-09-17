package com.redislabs.riot.redis.writer;

import java.util.Map;

import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;

public interface JedisMapWriter {

	Response<?> write(Pipeline pipeline, Map<String, Object> item);

	void write(JedisCluster cluster, Map<String, Object> item);

}