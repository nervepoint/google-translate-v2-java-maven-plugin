package com.nervepoint.googletranslate;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Callable;

import org.apache.maven.plugin.MojoExecutionException;

import redis.clients.jedis.JedisPool;

public class LocalToRedis implements Callable<Integer> {

	public static void main(String[] args) throws Exception {
		Optional<String> cacheTag = Optional.empty();
		Optional<String> hostSpec = Optional.empty();
		String groupId = null;
		String projectId = null;
		Path cacheDir = Paths.get(System.getProperty("user.home")).resolve(".i18n_cache");
		Optional<String> username = Optional.empty();
		Optional<String> password = Optional.empty();
		try {
			for (int i = 0; i < args.length; i++) {
				if (args[i].equals("-c") || args[i].equals("--cacheTag")) {
					cacheTag = Optional.of(args[++i]);
				} else if (args[i].equals("-d") || args[i].equals("--cacheDir")) {
					cacheDir = Paths.get(args[++i]);
				} else if (args[i].equals("-h") || args[i].equals("--host")) {
					hostSpec = Optional.of(args[++i]);
				} else if (args[i].equals("-u") || args[i].equals("--user")) {
					username = Optional.of(args[++i]);
				} else if (args[i].equals("-p") || args[i].equals("--password")) {
					password = Optional.of(args[++i]);
				} else if (args[i].startsWith("-")) {
					throw new IllegalArgumentException();
				} else if (groupId == null) {
					groupId = args[i];
				} else if (projectId == null) {
					projectId = args[i];
				} else
					throw new IllegalArgumentException();

			}
			if (groupId == null)
				throw new IllegalArgumentException();
		} catch (IllegalArgumentException iae) {
			System.err.println(
					"local-to-redis: <groupId> [<projectId>] [-c|--cacheTag <tag>] [--d|--cacheDir <dir>] [-u|--user <user>] [-p|--password <password>] [-h|--host <hostSpec>]");
			System.exit(1);
		}

		if (projectId == null) {
			Path dir = cacheDir.resolve(groupId);
			if (cacheTag.isPresent())
				dir = dir.resolve(cacheTag.get());
			DirectoryStream<Path> str = Files.newDirectoryStream(dir);
			try {
				for (Path path : str) {
					System.out.println("Module " + path.toString());
					LocalToRedis r = new LocalToRedis(cacheDir, groupId, path.getFileName().toString(), hostSpec,
							cacheTag, username, password);
					if (r.call() != 0)
						System.out.println("Failed module.");
				}
			} finally {
				str.close();
			}
		} else {
			LocalToRedis r = new LocalToRedis(cacheDir, groupId, projectId, hostSpec, cacheTag, username, password);
			System.exit(r.call());
		}
	}

	private final Path cacheDir;
	private final String groupId;
	private final Optional<String> cacheTag;
	private final Optional<String> userName;
	private final Optional<String> password;
	private final String projectId;
	private final Optional<String> hostSpec;

	public LocalToRedis(Path cacheDir, String groupId, String projectId, Optional<String> hostSpec,
			Optional<String> cacheTag, Optional<String> userName, Optional<String> password) {
		this.cacheDir = cacheDir;
		this.groupId = groupId;
		this.hostSpec = hostSpec;
		this.projectId = projectId;
		this.cacheTag = cacheTag;
		this.userName = userName;
		this.password = password;
	}

	@Override
	public Integer call() throws Exception {
		LocalCacheBackend local = new LocalCacheBackend(cacheDir, groupId, projectId, cacheTag, true);

		String redisHost = hostSpec.orElse("localhost");
		int port = 6379;
		int idx = redisHost.indexOf(':');
		if (idx != -1) {
			port = Integer.parseInt(redisHost.substring(idx + 1));
			redisHost = redisHost.substring(0, idx);
		}
		JedisPool pool = new JedisPool(redisHost, port);
		try {
			RedisCacheBackend remote = new RedisCacheBackend(pool, groupId, projectId, cacheTag, false, userName,
					password);

			try {
				Files.walkFileTree(local.getCacheDir(), new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						Path rel = local.getCacheDir().relativize(file);
						Path dir = rel.getParent();
						Path base = rel.getFileName();
						String baseName = base.toString();
						if (baseName.endsWith(".properties")) {
							baseName = baseName.substring(0, baseName.length() - 11);
							int idx = baseName.lastIndexOf('_');
							String lang = baseName.substring(idx + 1);
							baseName = baseName.substring(0, idx);
							Properties sheet = local.retrieve(Optional.ofNullable(dir), baseName, lang);
							if (!sheet.isEmpty()) {
								System.out.println(
										dir + " for " + baseName + " (" + lang + ") has " + sheet.size() + " keys");
								remote.store(Optional.ofNullable(dir), baseName, lang, sheet);
							}
						}
						return FileVisitResult.CONTINUE;
					}
				});
			} finally {
				remote.close();
			}
		} catch (IOException e) {
			throw new MojoExecutionException(e.getMessage(), e);
		}

		return 0;
	}

}
