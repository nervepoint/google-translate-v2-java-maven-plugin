package com.nervepoint.googletranslate;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisCacheBackend implements CacheBackend {

	final static Logger LOG = LoggerFactory.getLogger(RedisCacheBackend.class);

	private final Path cacheDir;
	private final JedisPool pool;

	private final Optional<String> username;
	private final Optional<String> password;

	public RedisCacheBackend(JedisPool pool, String groupId, String projectId, Optional<String> cacheTag,
			boolean failIfMissing, Optional<String> username, Optional<String> password) throws IOException {
		this.pool = pool;
		this.username = username;
		this.password = password;
		Path groupDir = cacheTag.isPresent() ? Paths.get(groupId).resolve(cacheTag.get()) : Paths.get(groupId);
		if (failIfMissing) {
			Jedis jedis = createClient(pool, username, password);
			try {
				if (jedis.get(groupDir.toString()) == null)
					throw new FileNotFoundException(
							"Master cache is empty. This will result in full translation of all texts, either set failOnMissingCacheDir to false in plugin configuration, or create a string key with any content at '"
									+ groupDir.toString() + "' on " + pool);
			} finally {
				jedis.close();
			}
		}

		this.cacheDir = groupDir.resolve(projectId);
	}

	protected Jedis createClient(JedisPool pool, Optional<String> username, Optional<String> password) {
		Jedis jedis = pool.getResource();
		if(username.isPresent()) {
			jedis.auth(username.get(),password.orElse(""));
		}
		else if(password.isPresent()) {
			jedis.auth(password.get());
		}
		return jedis;
	}

	@Override
	public Properties retrieve(Optional<Path> resourcePath, String baseName, String language) throws IOException {

		Path cacheKey = resolveCacheFile(resourcePath, baseName, language);

		if (Files.exists(cacheKey)) {
			LOG.info("Loading cache file " + cacheKey.toAbsolutePath().toString());
		}
		Properties p = new Properties();
		Jedis jedis = createClient(pool, username, password);
		try {
			for(Map.Entry<String, String> en : jedis.hgetAll(cacheKey.toString()).entrySet()) {
				p.setProperty(en.getKey(), en.getValue());
			}
		} finally {
			jedis.close();
		}
		if(p.isEmpty()) {
			LOG.warn("Could not find cache file " + cacheKey + " so a complete translation will be performed");
		}
		return p;
	}

	protected Path resolveCacheFile(Optional<Path> resourcePath, String baseName, String language) {
		return (resourcePath.isPresent() ? cacheDir.resolve(resourcePath.get()) : cacheDir)
				.resolve(baseName + "_" + language);
	}

	@Override
	public void store(Optional<Path> resourcePath, String baseName, String language, Properties cached)
			throws IOException {

		Path cacheFile = resolveCacheFile(resourcePath, baseName, language);
		Jedis jedis = createClient(pool, username, password);
		try {
			Map<String, String> m = new HashMap<String, String>();
			for(Object key : cached.keySet()) {
				m.put((String)key, cached.getProperty((String)key));
			}
			jedis.hset(cacheFile.toString(), m);
		} finally {
			jedis.close();
		}
		
	}

	@Override
	public String toString() {
		return "LocalCacheBackend [cacheDir=" + cacheDir + "]";
	}

	@Override
	public void close() throws IOException {
		pool.close();
	}
}
