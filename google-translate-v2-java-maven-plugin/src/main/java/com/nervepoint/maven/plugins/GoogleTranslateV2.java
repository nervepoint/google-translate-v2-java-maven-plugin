package com.nervepoint.maven.plugins;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.maven.model.FileSet;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;

import com.nervepoint.googletranslate.CacheBackend;
import com.nervepoint.googletranslate.LocalCacheBackend;
import com.nervepoint.googletranslate.RedisCacheBackend;
import com.nervepoint.googletranslate.Translatable;
import com.nervepoint.googletranslate.Translater;
import com.nervepoint.googletranslate.Translater.TranslatableProvider;

import redis.clients.jedis.JedisPool;

/**
 * @author Lee David Painter
 * @author Brett Smith
 * 
 */
@Mojo(name = "translate", requiresProject = false, threadSafe = true)
public class GoogleTranslateV2 extends AbstractMojo {

	@Parameter(defaultValue = "${api.key}", property = "translate.apiKey")
	private String apikey;

	@Parameter(defaultValue = "${basedir}/src/main/resources", property = "translate.sourceDirectory")
	private String sourceDirectory;

	@Parameter
	private FileSet fileSet;

	@Parameter(property = "translate.recurse")
	private boolean recurse;

	@Parameter(defaultValue = "${basedir}/target/classes", property = "translate.targetDirectory")
	private String targetDirectory;

	@Parameter(defaultValue = "en", property = "translate.sourceLanguage")
	private String sourceLanguage;

	@Parameter(property = "translate.sourceCountry")
	private String sourceCountry;

	@Parameter(property = "translate.sourceScript")
	private String sourceScript;

	@Parameter(property = "translate.sourceVariant")
	private String sourceVariant;

	@Parameter(defaultValue = "es,fr,nl,it,pl", property = "translate.language")
	private String languages;

	@Parameter(defaultValue = "${translateCacheDir}", property = "translate.cacheDir")
	private String cacheDir;

	@Parameter(property = "translate.cacheTag")
	private String cacheTag;

	@Parameter(property = "translate.format")
	private String format;

	@Parameter(property = "translate.distributedCache")
	private String distributedCache;

	@Parameter(property = "translate.username")
	private String username;

	@Parameter(property = "translate.password")
	private String password;

	@Parameter(defaultValue = "true", property = "translate.useHtmlForNonTranslatable")
	private boolean useHtmlForNonTranslatable = true;

	@Parameter(property = "translate.skip")
	private boolean skip = false;

	@Parameter(property = "translate.maxSourcesPerCall")
	private int maxSourcesPerCall = 10;

	@Parameter(property = "translate.noTranslate")
	private List<String> noTranslatePattern = new ArrayList<String>();

	@Parameter(property = "translate.excludeKeys")
	private List<String> excludeKeys = new ArrayList<String>();

	@Parameter(defaultValue = "true", property = "translate.failOnMissingCacheDir")
	private boolean failOnMissingCacheDir;

	@Parameter(defaultValue = "false", property = "translate.failOnMissingSourceDir")
	private boolean failOnMissingSourceDir;

	@Component
	private MavenProject project;

	public void execute() throws MojoExecutionException, MojoFailureException {
		if(skip) {
			getLog().info("Skipping translation");
			return;
		}

		CacheBackend cacheBackend;
		

		// Work out cache dir
		if(distributedCache != null) {
			getLog().info("Using distributed cache @" + distributedCache);
			
			String redisHost = distributedCache;
			int port = 6379;
			int idx = redisHost.indexOf(':');
			if(idx != -1) {
				port = Integer.parseInt(redisHost.substring(idx + 1));
				redisHost = redisHost.substring(0, idx);
			}
			JedisPool pool = new JedisPool(redisHost, port);
			try {
				cacheBackend = new RedisCacheBackend(pool, project.getGroupId(), project.getArtifactId(), Optional.ofNullable(cacheTag), failOnMissingCacheDir, stringOptional(username), stringOptional(password));
			} catch (IOException e) {
				throw new MojoExecutionException(e.getMessage(), e);
			}
		}
		else {
		
			File masterCache;
	
			getLog().info("Cache dir is " + cacheDir);
	
			if (cacheDir == null || cacheDir.equals("${translateCacheDir}")) {
				getLog().info("Using default cache");
				masterCache = new File(System.getProperty("user.home"), ".i18n_cache");
			} else {
				getLog().info("Using user defined cache " + cacheDir);
				masterCache = new File(cacheDir);
			}
	
			try {
				cacheBackend = new LocalCacheBackend(masterCache.toPath(), project.getGroupId(), project.getArtifactId(), Optional.ofNullable(cacheTag), failOnMissingCacheDir);
			} catch (IOException e) {
				throw new MojoExecutionException(e.getMessage(), e);
			}
		}

		try {
			getLog().info("Actual project cache is " + cacheBackend);
	
			// Build translater
			Translater translater = new Translater();
			translater.setCacheDir(cacheBackend);
			translater.setThreadSafe(true);
			translater.setExcludeKeys(excludeKeys);
			translater.setFailOnMissingCacheDir(failOnMissingCacheDir);
			translater.setApikey(apikey);
			translater.setFormat(format);
			translater.setLanguages(languages);
			translater.setMaxSourcesPerCall(maxSourcesPerCall);
			translater.setNoTranslatePattern(noTranslatePattern);
			translater.setSourceCountry(sourceCountry);
			translater.setSourceLanguage(sourceLanguage);
			translater.setSourceScript(sourceScript);
			translater.setSourceVariant(sourceVariant);
			translater.setUseHtmlForNonTranslatable(useHtmlForNonTranslatable);
			translater.setTargetDirectory(new File(targetDirectory));
			translater.setFileProvider(new TranslatableProvider() {
	
				@Override
				public Iterable<Translatable> getTranslatables() throws IOException {
					File sourceDir = new File(sourceDirectory);
					if (!sourceDir.exists()) {
	
						if (failOnMissingSourceDir) {
							throw new IOException("sourceDirectory " + sourceDirectory
									+ " does not exist. To ignore this setting set failOnMissingSourceDir=false");
						}
						getLog().warn("sourceDirectory " + sourceDirectory + " does not exist");
						return Collections.emptyList();
					}
	
					DirectoryScanner scanner = new DirectoryScanner();
					scanner.setBasedir(sourceDir);
					if (fileSet == null || fileSet.getIncludes() == null || fileSet.getIncludes().size() == 0) {
						if (recurse) {
							scanner.setIncludes(new String[] { "*.properties" });
						} else {
							scanner.setIncludes(new String[] { "**/*.properties" });
						}
					} else {
						scanner.setIncludes((String[]) fileSet.getIncludes().toArray(new String[0]));
					}
					if (fileSet != null && fileSet.getExcludes() != null) {
						scanner.setExcludes((String[]) fileSet.getExcludes().toArray(new String[0]));
					}
					scanner.scan();
					String[] included = scanner.getIncludedFiles();
					getLog().info("Found " + included.length + " included files");
					List<Translatable> files = new ArrayList<Translatable>(included.length);
					for (String s : included) {
						files.add(new Translatable(sourceDir, new File(sourceDir, s)));
					}
					return files;
				}
			});
	
			// Go!
			try {
				translater.execute();
			} catch (IOException e) {
				throw new MojoExecutionException("Failed to translate.", e);
			}
		}
		finally {
			try {
				cacheBackend.close();
			} catch (IOException e) {
				getLog().error("Failed to close cache backend.", e);
			}
		}
	}

	
	static Optional<String> stringOptional(String str) {
		return Optional.ofNullable(str).filter(s -> !s.isEmpty());
	}
}
