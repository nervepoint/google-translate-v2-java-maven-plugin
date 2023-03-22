package com.nervepoint.googletranslate;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalCacheBackend implements CacheBackend {

	final static Logger LOG = LoggerFactory.getLogger(LocalCacheBackend.class);

	private final Path cacheDir;

	public LocalCacheBackend(Path cacheDir, String groupId, String projectId, Optional<String> cacheTag,
			boolean failIfMissing) throws IOException {
		Path groupDir = cacheTag.isPresent() ? cacheDir.resolve(groupId).resolve(cacheTag.get())
				: cacheDir.resolve(groupId);
		if (!Files.exists(groupDir) && failIfMissing) {
			throw new FileNotFoundException(
					"Master cache folder is empty. This will result in full translation of all texts, either set failOnMissingCacheDir to false in plugin configuration, or create the folder to override this setting.");
		}
		this.cacheDir = groupDir.resolve(projectId);
		if (!Files.exists(cacheDir))
			Files.createDirectories(cacheDir);
	}

	public LocalCacheBackend() {
		cacheDir = Paths.get(System.getProperty("user.home")).resolve(".i18n_cache");
	}

	@Override
	public Properties retrieve(Optional<Path> resourcePath, String baseName, String language) throws IOException {
		if (resourcePath.isPresent())
			Files.createDirectories(resourcePath.get());

		Path cacheFile = resolveCacheFile(resourcePath, baseName, language);

		if (Files.exists(cacheFile)) {
			LOG.info("Loading cache file " + cacheFile.toAbsolutePath().toString());
		}
		Properties p = new Properties();
		try {
			InputStream in = Files.newInputStream(cacheFile);
			try {
				p.load(in);
			} finally {
				in.close();
			}
		} catch (FileNotFoundException ex) {
			LOG.warn("Could not find cache file " + cacheFile + " so a complete translation will be performed");
		}
		return p;
	}

	protected Path resolveCacheFile(Optional<Path> resourcePath, String baseName, String language) {
		return (resourcePath.isPresent() ? cacheDir.resolve(resourcePath.get()) : cacheDir)
				.resolve(baseName + "_" + language + ".properties");
	}

	@Override
	public void store(Optional<Path> resourcePath, String baseName, String language, Properties cached)
			throws IOException {

		Path cacheFile = resolveCacheFile(resourcePath, baseName, language);
		OutputStream out = Files.newOutputStream(cacheFile);
		try {
			cached.store(out, "Cache of auto generated google translations for Google Translate V2 API maven plugin");
		} finally {
			out.close();
		}
	}

	@Override
	public String toString() {
		return "LocalCacheBackend [cacheDir=" + cacheDir + "]";
	}

	@Override
	public void close() throws IOException {
	}
}
