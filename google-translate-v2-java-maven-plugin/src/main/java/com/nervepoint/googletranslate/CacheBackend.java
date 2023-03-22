package com.nervepoint.googletranslate;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

public interface CacheBackend extends Closeable {

	Properties retrieve(Optional<Path> resourcePath, String baseName, String language) throws IOException;
	
	void store(Optional<Path> resourcePath, String baseName, String language, Properties cached) throws IOException;
}
