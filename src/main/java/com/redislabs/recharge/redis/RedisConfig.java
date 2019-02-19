package com.redislabs.recharge.redis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.CompositeItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import com.redislabs.lettusearch.RediSearchAsyncCommands;
import com.redislabs.lettusearch.StatefulRediSearchConnection;
import com.redislabs.lettusearch.search.DropOptions;
import com.redislabs.lettusearch.search.Schema;
import com.redislabs.lettusearch.search.Schema.SchemaBuilder;
import com.redislabs.lettusearch.search.field.Field;
import com.redislabs.lettusearch.search.field.GeoField;
import com.redislabs.lettusearch.search.field.NumericField;
import com.redislabs.lettusearch.search.field.TextField;
import com.redislabs.lettusearch.search.field.TextField.TextFieldBuilder;
import com.redislabs.recharge.RechargeConfiguration;
import com.redislabs.recharge.RechargeException;
import com.redislabs.recharge.redis.writers.AbstractRedisWriter;
import com.redislabs.recharge.redis.writers.FTAddWriter;
import com.redislabs.recharge.redis.writers.GeoAddWriter;
import com.redislabs.recharge.redis.writers.HMSetWriter;
import com.redislabs.recharge.redis.writers.LPushWriter;
import com.redislabs.recharge.redis.writers.NilWriter;
import com.redislabs.recharge.redis.writers.SAddWriter;
import com.redislabs.recharge.redis.writers.StringWriter;
import com.redislabs.recharge.redis.writers.SuggestionWriter;
import com.redislabs.recharge.redis.writers.XAddWriter;
import com.redislabs.recharge.redis.writers.ZAddWriter;

import lombok.extern.slf4j.Slf4j;

@Configuration
@SuppressWarnings("rawtypes")
@Slf4j
public class RedisConfig {

	@Autowired
	private RechargeConfiguration config;

	@Autowired
	private GenericObjectPool<StatefulRediSearchConnection<String, String>> rediSearchConnectionPool;

	public ItemWriter<Map> writer() throws RechargeException {
		List<RedisWriterConfiguration> redis = config.getRedis();
		if (redis.size() == 0) {
			return new NilWriter(new NilConfiguration());
		}
		if (redis.size() == 1) {
			return writer(redis.get(0)).setConnectionPool(rediSearchConnectionPool);
		}
		List<ItemWriter<? super Map>> writers = new ArrayList<>();
		for (RedisWriterConfiguration config : redis) {
			writers.add(writer(config).setConnectionPool(rediSearchConnectionPool));
		}
		CompositeItemWriter<Map> composite = new CompositeItemWriter<>();
		composite.setDelegates(writers);
		return composite;
	}

	private AbstractRedisWriter<?> writer(RedisWriterConfiguration writer) throws RechargeException {
		if (writer.getSet() != null) {
			return new SAddWriter(writer.getSet());
		}
		if (writer.getGeo() != null) {
			return new GeoAddWriter(writer.getGeo());
		}
		if (writer.getList() != null) {
			return new LPushWriter(writer.getList());
		}
		if (writer.getSearch() != null) {
			SearchConfiguration config = writer.getSearch();
			try {
				StatefulRediSearchConnection<String, String> connection = rediSearchConnectionPool.borrowObject();
				try {
					RediSearchAsyncCommands<String, String> commands = connection.async();
					String keyspace = config.getKeyspace();
					if (config.isDrop()) {
						log.debug("Dropping index {}", keyspace);
						try {
							commands.drop(keyspace, DropOptions.builder().build());
						} catch (Exception e) {
							log.debug("Could not drop index {}", keyspace, e);
						}
					}
					if (config.isCreate() && !config.getSchema().isEmpty()) {
						SchemaBuilder builder = Schema.builder();
						config.getSchema().forEach(entry -> builder.field(getField(entry)));
						Schema schema = builder.build();
						log.debug("Creating schema {}", keyspace);
						try {
							commands.create(keyspace, schema);
						} catch (Exception e) {
							if (e.getMessage().startsWith("Index already exists")) {
								log.debug("Ignored failure to create index {}", keyspace, e);
							} else {
								log.error("Could not create index {}", keyspace, e);
							}
						}
					}
					commands.flushCommands();
				} finally {
					connection.close();
				}
				return new FTAddWriter(writer.getSearch());
			} catch (Exception e) {
				throw new RechargeException("Could not get connection from pool", e);
			}
		}
		if (writer.getSet() != null) {
			return new SAddWriter(writer.getSet());
		}
		if (writer.getString() != null) {
			return new StringWriter(writer.getString());
		}
		if (writer.getSuggest() != null) {
			return new SuggestionWriter(writer.getSuggest());
		}
		if (writer.getZset() != null) {
			return new ZAddWriter(writer.getZset());
		}
		if (writer.getStream() != null) {
			return new XAddWriter(writer.getStream());
		}
		if (writer.getHash() != null) {
			return new HMSetWriter(writer.getHash());
		}
		return new HMSetWriter(new HashConfiguration());
	}

	private Field getField(RediSearchField field) {
		switch (field.getType()) {
		case Geo:
			return GeoField.builder().name(field.getName()).sortable(field.isSortable()).build();
		case Numeric:
			return NumericField.builder().name(field.getName()).sortable(field.isSortable()).build();
		default:
			TextFieldBuilder builder = TextField.builder().name(field.getName()).sortable(field.isSortable())
					.noStem(field.isNoStem());
			if (field.getWeight() != null) {
				builder.weight(field.getWeight());
			}
			if (field.getMatcher() != null) {
				builder.matcher(field.getMatcher());
			}
			return builder.build();
		}
	}

}