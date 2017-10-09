package com.nervepoint.maven.plugins;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.model.FileSet;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;

import com.nervepoint.googletranslate.Translatable;
import com.nervepoint.googletranslate.Translater;
import com.nervepoint.googletranslate.Translater.TranslatableProvider;

/**
 * @author Lee David Painter
 * @author Brett Smith
 * 
 */
@Mojo(name = "translate", requiresProject = false)
public class GoogleTranslateV2 extends AbstractMojo {

	@Parameter(defaultValue = "${api.key}")
	private String apikey;

	@Parameter(defaultValue = "${basedir}/src/main/resources")
	private String sourceDirectory;

	@Parameter
	private FileSet fileSet;

	@Parameter
	private boolean recurse;

	@Parameter(defaultValue = "${basedir}/target/classes")
	private String targetDirectory;

	@Parameter(defaultValue = "en")
	private String sourceLanguage;

	@Parameter
	private String sourceCountry;

	@Parameter
	private String sourceScript;

	@Parameter
	private String sourceVariant;

	@Parameter(defaultValue = "es,fr,nl,it,pl")
	private String languages;

	@Parameter(defaultValue = "${translateCacheDir}")
	private String cacheDir;

	@Parameter
	private String cacheTag;

	@Parameter
	private String format;

	@Parameter(defaultValue = "true")
	private boolean useHtmlForNonTranslatable = true;

	@Parameter
	private int maxSourcesPerCall = 10;

	@Parameter
	private List<String> noTranslatePattern = new ArrayList<String>();

	@Parameter
	private List<String> excludeKeys = new ArrayList<String>();

	@Parameter(defaultValue = "true")
	private boolean failOnMissingCacheDir;

	@Parameter(defaultValue = "false")
	private boolean failOnMissingSourceDir;

	@Component
	private MavenProject project;

	public void execute() throws MojoExecutionException, MojoFailureException {

		// Work out cache dir
		File masterCache;

		getLog().info("Cache dir is " + cacheDir);

		if (cacheDir == null || cacheDir.equals("${translateCacheDir}")) {
			getLog().info("Using default cache");
			masterCache = new File(System.getProperty("user.home"), ".i18n_cache");
		} else {
			getLog().info("Using user defined cache " + cacheDir);
			masterCache = new File(cacheDir);
		}

		File rootCacheDir = new File(masterCache,
				project.getGroupId() + (cacheTag != null ? File.separator + cacheTag : ""));

		getLog().info("Master cache folder for this group/tag is " + rootCacheDir.getAbsolutePath());

		if (!rootCacheDir.exists() && failOnMissingCacheDir) {
			throw new MojoFailureException(
					"Master cache folder is empty. This will result in full translation of all texts, either set failOnMissingCacheDir to false in plugin configuration, or create the folder to override this setting.");
		}

		rootCacheDir = new File(rootCacheDir, project.getArtifactId());
		getLog().info("Actual project cache is " + rootCacheDir.getAbsolutePath());
		rootCacheDir.mkdirs();

		// Build translater
		Translater translater = new Translater();
		translater.setCacheDir(rootCacheDir);
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

}
