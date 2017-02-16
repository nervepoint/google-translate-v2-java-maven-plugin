package com.nervepoint.maven.plugins;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.maven.model.FileSet;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.translate.Translate;
import com.google.api.services.translate.TranslateRequestInitializer;
import com.google.api.services.translate.model.TranslationsListResponse;
import com.google.api.services.translate.model.TranslationsResource;

/**
 * @author Lee David Painter
 * @author Brett Smith
 * 
 */
@Mojo(name = "translate", requiresProject = false)
public class GoogleTranslateV2 extends AbstractMojo {

    private final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private HttpTransport httpTransport;

    private static Translate client;

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

    private PatternReplacer replacer;
    private File rootCacheDir;

    public void execute() throws MojoExecutionException, MojoFailureException {

        if (apikey == null) {
            getLog().info("Translation will not be performed because there is no API key available");
            return;
        }

        File masterCache;

        getLog().info("Cache dir is " + cacheDir);

        if (cacheDir == null || cacheDir.equals("${translateCacheDir}")) {
            getLog().info("Using default cache");
            masterCache = new File(System.getProperty("user.home"), ".i18n_cache");
        } else {
            getLog().info("Using user defined cache " + cacheDir);
            masterCache = new File(cacheDir);
        }

        rootCacheDir = new File(masterCache, project.getGroupId() + (cacheTag != null ? File.separator + cacheTag : ""));

        getLog().info("Master cache folder for this group/tag is " + rootCacheDir.getAbsolutePath());

        if (!rootCacheDir.exists() && failOnMissingCacheDir) {
            throw new MojoFailureException(
                            "Master cache folder is empty. This will result in full translation of all texts, either set failOnMissingCacheDir to false in plugin configuration, or create the folder to override this setting.");
        }

        rootCacheDir = new File(rootCacheDir, project.getArtifactId());

        getLog().info("Actual project cache is " + rootCacheDir.getAbsolutePath());

        rootCacheDir.mkdirs();

        replacer = new PatternReplacer();
        if (!noTranslatePattern.isEmpty() && useHtmlForNonTranslatable) {
            replacer.setUntranslatableString("<span class=\"notranslate\">NO_TRANSLATE</span>");
        }
        for (String p : noTranslatePattern) {
            getLog().info("Will not translate content matching " + p);
            replacer.addPattern(p);
        }

        try {
            // initialize the transport
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            // set up global Translate instance
            client = new Translate.Builder(httpTransport, JSON_FACTORY, null).setGoogleClientRequestInitializer(
                new TranslateRequestInitializer(apikey)).setApplicationName("GoogleTranslateMavenPlugin/0.2").build();

            try {

                File source = new File(sourceDirectory);
                processDirectory(source, new File(targetDirectory), rootCacheDir);
            } catch (Exception e) {
                getLog().error(e);
                throw new MojoFailureException("Translate failed: " + e.getMessage());
            }

            return;
        } catch (IOException e) {
            getLog().error(e);
        } catch (Throwable t) {
            getLog().error(t);
        }

        throw new MojoFailureException("Translation failed due ot previous exceptions");

    }

    private void processDirectory(File sourceDir, File destinationDir, File sourceCacheDir) throws IOException, URISyntaxException {

        getLog().info("Using source directory " + sourceDir.getAbsolutePath());
        getLog().info("Using target directory " + destinationDir.getAbsolutePath());

        destinationDir.mkdirs();

        if (!sourceDir.exists()) {

            if (failOnMissingSourceDir) {
                throw new IOException("sourceDirectory " + sourceDirectory
                                + " does not exist. To ignore this setting set failOnMissingSourceDir=false");
            }
            getLog().warn("sourceDirectory " + sourceDirectory + " does not exist");
            return;
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

        for (String fileName : included) {
            File p = new File(sourceDir, fileName);
            if (p.isFile()) {
                int lidx = fileName.lastIndexOf('/');
                String dir = lidx == -1 ? "" : fileName.substring(0, lidx);
                String pname = p.getName();
                int idx = pname.lastIndexOf('.');
                if (idx == -1) {
                    getLog().error("Resource bundles must end with .properties");
                    continue;
                }
                pname = pname.substring(0, idx);

                // https://docs.oracle.com/javase/7/docs/api/java/util/ResourceBundle.html
                String[] parts = pname.split("_");
                String base = parts[0];

                String lang = sourceLanguage;
                String country = sourceCountry;
                String script = sourceScript;
                String variant = sourceVariant;

                if (parts.length > 1) {
                    lang = parts[1];
                    if (parts.length > 4) {
                        script = parts[2];
                        country = parts[3];
                        variant = parts[4];
                    } else if (parts.length > 3) {
                        // Ambiguous, could be either
                        // language + "_" + script + "_" + country
                        // language + "_" + country + "_" + variant
                        for (Locale l : Locale.getAvailableLocales()) {
                            if (l.getCountry().equals(parts[2])) {
                                country = parts[2];
                                variant = parts[3];
                                break;
                            }
                        }
                        if (country == null) {
                            script = parts[2];
                            country = parts[3];
                        }

                    } else if (parts.length > 2) {
                        // Ambiguous, could be either
                        // language + "_" + script
                        // language + "_" + country

                        for (Locale l : Locale.getAvailableLocales()) {
                            if (l.getCountry().equals(parts[2])) {
                                country = parts[2];
                                break;
                            }
                        }
                        if (country == null) {
                            script = parts[2];
                        }
                    }
                }

                if (!Objects.equals(sourceLanguage, lang) || !Objects.equals(sourceCountry, country) || !Objects.equals(
                    sourceScript, script) || !Objects.equals(sourceVariant, variant)) {
                    getLog().info("Skipping " + p.getName() + " because it is not the same as the source locale");
                } else {
                    File dest = dir.equals("") ? destinationDir : new File(destinationDir, dir);
                    File destCache = dir.equals("") ? sourceCacheDir : new File(sourceCacheDir, dir);

                    getLog().info("    " + fileName + " -> " + dest.getAbsolutePath() + " [" + destCache.getAbsolutePath() + "]");

                    translateFile(p, base, dest, destCache);
                }
            }
        }

    }

    private void translateFile(File sourceFile, String baseName, File desintationDir, File sourceCacheDir) throws IOException,
                    URISyntaxException {

        StringTokenizer t = new StringTokenizer(languages, ",");
        while (t.hasMoreTokens()) {

            String l = t.nextToken();

            if (baseName.endsWith("_" + l)) {
                getLog().info("Skipping " + baseName + ".properties as its an override file.");
                continue;
            }

            translateFileToLanguage(sourceFile, baseName, desintationDir, sourceCacheDir, l);

        }

    }

    private void translateFileToLanguage(File sourceFile, String baseName, File destinationDir, File sourceCacheDir,
                                         String language) throws IOException, URISyntaxException {

        sourceCacheDir.mkdirs();

        getLog().info("Translating " + sourceFile.getName() + " to " + language);

        File overrideFile = new File(sourceFile.getParentFile(), baseName + "_" + language + ".properties");
        File previousTranslation = new File(sourceCacheDir, baseName + "_" + language + ".properties");

        Properties p;
        Properties translated = new Properties();
        Properties override;
        Properties cached;

        p = loadProperties(sourceFile, "source");
        override = loadProperties(overrideFile, "override");
        cached = loadProperties(previousTranslation, "cache");

        boolean needCacheWrite = false;

        List<TranslationOp> ops = new ArrayList<TranslationOp>();

        for (String name : p.stringPropertyNames()) {

            // The unprocessed content from the base resource file
            String originalContent = p.getProperty(name).trim();

            /*
             * We process the source property for any patterns we don't want to
             * translate. These are sent to Google and the returned content is
             * processed again, putting the untranslatable text back where it
             * was.
             */
            String processed = replacer.preProcess(originalContent);

            if (originalContent.equals("")) {
                translated.put(name, "");
                cached.put(name, hash("") + "|" + "");
                needCacheWrite = true;
                continue;
            }

            if (override.containsKey(name)) {
                translated.put(name, override.getProperty(name));
                getLog().info("Detected overridden text for " + name);
                continue;
            } else if (cached.containsKey(name)) {

                String c = cached.getProperty(name);
                int idx = c.indexOf('|');
                String h = c.substring(0, idx);
                String text = c.substring(idx + 1);

                if (hash(processed).equals(h)) {
                    translated.put(name, replacer.postProcess(text));
                    continue;
                }
                getLog().info("Detected change to cached text for " + name);
            }

            getLog().debug("Marking " + name + " for translation");
            
            /* Determine format **/
            String format = this.format;
            String originalFormat = processed.indexOf("<html>") != -1 ? "html" : "text";
            if(useHtmlForNonTranslatable && !noTranslatePattern.isEmpty())
                format = "html";
            else if(format == null)
                format = originalFormat;
                

            ops.add(new TranslationOp(name, processed, new ArrayList<String>(replacer.getContentMap()), format, originalFormat));

            // if (characters > 4000) {
            //
            // getLog().info("Translating " + characters + " characters");
            // List<TranslationsResource> translations =
            // translate(toTranslateValues, sourceLanguage, language);
            //
            // for (TranslationsResource t : translations) {
            //
            // name = toTranslateKeys.remove(0);
            // processed = toTranslateValues.remove(0);
            // replacer.getContentMap().clear();
            // replacer.getContentMap().addAll(toTranslateMap.remove(0));
            //
            // // And now the bit where the original untranslatable
            // // text is
            // // put
            // // back
            // String postProcessed =
            // replacer.postProcess(t.getTranslatedText());
            //
            // translated.put(name, postProcessed);
            // cached.put(name, hash(processed) + "|" + t.getTranslatedText());
            // needCacheWrite = true;
            // }
            //
            // characters = 0;
            // }
        }

        if (!ops.isEmpty()) {

            getLog().info("Translating " + ops.size() + " properties");

            String format = null;
            while (!ops.isEmpty()) {
                List<TranslationOp> todo = new ArrayList<TranslationOp>();

                for (TranslationOp op : new ArrayList<TranslationOp>(ops)) {
                    ops.remove(op);

                    if (op.value.length() == 0)
                        continue;

                    if (!hasAnyAlpha(op.value))
                        continue;
                    
                    if(!isIncludeKey(op.keyName))
                        continue;

                    if (format == null)
                        format = op.format;
                    else if (!format.equals(op.format))
                        break;

                    todo.add(op);

                    if (todo.size() >= maxSourcesPerCall)
                        break;
                }

                if (!todo.isEmpty()) {
                    translateOps(todo, sourceLanguage, language);

                    /* Post process */
                    for (TranslationOp op : todo) {
                        replacer.getContentMap().clear();
                        replacer.getContentMap().addAll(op.map);
                        String postProcessed = op.value;
                        try {
                            postProcessed = replacer.postProcess(op.translated);
                        } catch (RuntimeException rte) {
                            getLog().warn("Failed to translate '" + op.value + "'. " + rte.getMessage()
                                            + ". Will use processed text.");
                        }
                        translated.put(op.keyName, postProcessed);
                        cached.put(op.keyName, hash(op.value) + "|" + op.translated);
                        needCacheWrite = true;
                    }
                }

                format = null;
            }

        }

        File target = new File(destinationDir, baseName + "_" + language + ".properties");

        if (target.exists()) {
            getLog().info("Deleting existing target " + target.getName() + " as we have a new translation.");
            target.delete();
        }

        FileOutputStream out = new FileOutputStream(target);
        try {
            translated.store(out, "Auto generated by Google Translate V2 API maven plugin");
        } finally {
            out.close();
        }

        if (needCacheWrite) {
            out = new FileOutputStream(previousTranslation);
            try {
                cached.store(out, "Cache of auto generated google translations for Google Translate V2 API maven plugin");
            } finally {
                out.close();
            }
        }

    }

    private boolean isIncludeKey(String keyName) {
        return !matches(excludeKeys, keyName);
    }
    
    private boolean matches(List<String> patterns, String text) {
        for(String p : patterns) {
            if(text.matches(p))
                return true;
        }
        return false;
    }

    boolean hasAnyAlpha(String str) {
        for (char c : str.toCharArray()) {
            if (Character.isAlphabetic(c))
                return true;
        }
        return false;
    }

    void translateOps(List<TranslationOp> sources, String sourceLang, String targetLang) throws IOException {
        List<String> strings = new ArrayList<String>();
        String format = null;
        for (TranslationOp op : sources) {
            strings.add(op.value);
            if (format != null && !format.equals(op.format)) {
                throw new IllegalStateException("Can only translate one format at a time.");
            }
            format = op.format;
        }
        Translate.Translations.List res = client.translations().list(strings, targetLang);
        res.setSource(sourceLang);
        res.setFormat(format);
        try {
            TranslationsListResponse c = res.execute();
            List<TranslationsResource> translations = c.getTranslations();
            Iterator<TranslationsResource> it = translations.iterator();
            for (TranslationOp op : sources) {
                op.translated = it.next().getTranslatedText();

                /* Convert back to original format */
                if(!op.format.equals(op.originalFormat) && op.originalFormat.equals("text")) {
                    op.translated = StringEscapeUtils.unescapeHtml4(op.translated);
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed while translating '" + sources.toString() + "' from " + sourceLang + " into "
                            + targetLang, e);
        }
    }

    private String hash(String content) {
        try {
            MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            digest.update(content.getBytes("UTF-8"));
            byte[] hash = digest.digest();
            return Hex.encodeHexString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("");
        }
    }

    private Properties loadProperties(File path, String type) throws UnsupportedEncodingException, IOException {
        if (path.exists()) {
            getLog().info("Loading " + type + " file " + path.getAbsolutePath());
        }
        Properties p = new Properties();
        try {
            FileInputStream in = new FileInputStream(path);
            try {
                p.load(in);
            } finally {
                in.close();
            }
        } catch (FileNotFoundException ex) {
            if (type.equals("cache")) {
                getLog().warn("Could not find cache file " + path + " so a complete translation will be performed");
            }
        }

        return p;
    }

    class TranslationOp {
        String keyName;
        String value;
        List<String> map;
        String translated;
        String format;
        String originalFormat;

        TranslationOp(String keyName, String value, List<String> map, String format, String originalFormat) {
            this.keyName = keyName;
            this.value = value;
            this.map = map;
            this.format = format;
            this.originalFormat = originalFormat;
        }
    }

}
