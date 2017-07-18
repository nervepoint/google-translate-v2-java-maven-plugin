package com.nervepoint.googletranslate;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.translate.Translate;
import com.google.api.services.translate.TranslateRequestInitializer;
import com.google.api.services.translate.model.TranslationsListResponse;
import com.google.api.services.translate.model.TranslationsResource;

public class Translater {

    public interface FileProvider {
        Iterable<File> getFiles() throws IOException;
    }

    final static Logger LOG = LoggerFactory.getLogger(Translater.class);

    private final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private HttpTransport httpTransport;

    private static Translate client;

    private String apikey;
    private File targetDirectory = new File("target/classes");
    private String sourceLanguage = "en";
    private String sourceCountry;
    private String sourceScript;
    private String sourceVariant;
    private String languages = "es,fr,nl,it,pl";
    private File cacheDir = new File(System.getProperty("user.home") + File.separator + ".i18n_cache");
    private String format;
    private boolean useHtmlForNonTranslatable = true;
    private int maxSourcesPerCall = 10;
    private List<String> noTranslatePattern = new ArrayList<String>();
    private List<String> excludeKeys = new ArrayList<String>();
    private boolean failOnMissingCacheDir = true;
    private PatternReplacer replacer;
    private FileProvider fileProvider;

    public FileProvider getFileProvider() {
        return fileProvider;
    }

    public void setFileProvider(FileProvider fileProvider) {
        this.fileProvider = fileProvider;
    }

    public String getApikey() {
        return apikey;
    }

    public void setApikey(String apikey) {
        this.apikey = apikey;
    }

    public File getTargetDirectory() {
        return targetDirectory;
    }

    public void setTargetDirectory(File targetDirectory) {
        this.targetDirectory = targetDirectory;
    }

    public String getSourceLanguage() {
        return sourceLanguage;
    }

    public void setSourceLanguage(String sourceLanguage) {
        this.sourceLanguage = sourceLanguage;
    }

    public String getSourceCountry() {
        return sourceCountry;
    }

    public void setSourceCountry(String sourceCountry) {
        this.sourceCountry = sourceCountry;
    }

    public String getSourceScript() {
        return sourceScript;
    }

    public void setSourceScript(String sourceScript) {
        this.sourceScript = sourceScript;
    }

    public String getSourceVariant() {
        return sourceVariant;
    }

    public void setSourceVariant(String sourceVariant) {
        this.sourceVariant = sourceVariant;
    }

    public String getLanguages() {
        return languages;
    }

    public void setLanguages(String languages) {
        this.languages = languages;
    }

    public File getCacheDir() {
        return cacheDir;
    }

    public void setCacheDir(File cacheDir) {
        this.cacheDir = cacheDir;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public boolean isUseHtmlForNonTranslatable() {
        return useHtmlForNonTranslatable;
    }

    public void setUseHtmlForNonTranslatable(boolean useHtmlForNonTranslatable) {
        this.useHtmlForNonTranslatable = useHtmlForNonTranslatable;
    }

    public int getMaxSourcesPerCall() {
        return maxSourcesPerCall;
    }

    public void setMaxSourcesPerCall(int maxSourcesPerCall) {
        this.maxSourcesPerCall = maxSourcesPerCall;
    }

    public List<String> getNoTranslatePattern() {
        return noTranslatePattern;
    }

    public void setNoTranslatePattern(List<String> noTranslatePattern) {
        this.noTranslatePattern = noTranslatePattern;
    }

    public List<String> getExcludeKeys() {
        return excludeKeys;
    }

    public void setExcludeKeys(List<String> excludeKeys) {
        this.excludeKeys = excludeKeys;
    }

    public boolean isFailOnMissingCacheDir() {
        return failOnMissingCacheDir;
    }

    public void setFailOnMissingCacheDir(boolean failOnMissingCacheDir) {
        this.failOnMissingCacheDir = failOnMissingCacheDir;
    }

    public void execute() throws IOException {

        if (apikey == null) {
            throw new IOException("Translation will not be performed because there is no API key available");
        }

        if (fileProvider == null) {
            throw new IOException("Translation will not be performed as fileProvider has not been set.");
        }

        if (targetDirectory == null) {
            throw new IOException("Translation will not be performed as targetDirectory has not been set.");
        }

        LOG.info("Cache dir is " + cacheDir);

        cacheDir.mkdirs();

        replacer = new PatternReplacer();
        if (!noTranslatePattern.isEmpty() && useHtmlForNonTranslatable) {
            replacer.setUntranslatableStrings("<span class=\"notranslate\">NO_TRANSLATE</span>", "NO_TRANSLATE");
        }
        for (String p : noTranslatePattern) {
            LOG.info("Will not translate content matching " + p);
            replacer.addPattern(p);
        }

        try {
            // initialize the transport
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            // set up global Translate instance
            client = new Translate.Builder(httpTransport, JSON_FACTORY, null).setGoogleClientRequestInitializer(
                new TranslateRequestInitializer(apikey)).setApplicationName("GoogleTranslateMavenPlugin/0.2").build();

            try {
                processDirectory(targetDirectory, cacheDir);
            } catch (Exception e) {
                throw new IOException("Translate failed: " + e.getMessage());
            }

            return;
        } catch (Throwable t) {
            throw new IOException("Failed to translate.", t);
        }

    }

    private void processDirectory(File destinationDir, File sourceCacheDir) throws IOException, URISyntaxException {

        LOG.info("Using target directory " + destinationDir.getAbsolutePath());

        destinationDir.mkdirs();

        for (File p : fileProvider.getFiles()) {
            if (p.isFile()) {
                String fileName = p.getName();
                int lidx = fileName.lastIndexOf('/');
                String dir = lidx == -1 ? "" : fileName.substring(0, lidx);
                String pname = p.getName();
                int idx = pname.lastIndexOf('.');
                if (idx == -1) {
                    LOG.error("Resource bundles must end with .properties");
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
                    LOG.info("Skipping " + p.getName() + " because it is not the same as the source locale");
                } else {
                    File dest = dir.equals("") ? destinationDir : new File(destinationDir, dir);
                    File destCache = dir.equals("") ? sourceCacheDir : new File(sourceCacheDir, dir);

                    LOG.info("    " + fileName + " -> " + dest.getAbsolutePath() + " [" + destCache.getAbsolutePath() + "]");

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
                LOG.info("Skipping " + baseName + ".properties as its an override file.");
                continue;
            }

            translateFileToLanguage(sourceFile, baseName, desintationDir, sourceCacheDir, l);

        }

    }

    private void translateFileToLanguage(File sourceFile, String baseName, File destinationDir, File sourceCacheDir,
                                         String language) throws IOException, URISyntaxException {

        sourceCacheDir.mkdirs();

        LOG.info("Translating " + sourceFile.getName() + " to " + language);

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
                LOG.info("Detected overridden text for " + name);
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
                LOG.info("Detected change to cached text for " + name);
            }

            LOG.debug("Marking " + name + " for translation");

            /* Determine format **/
            String format = this.format;
            String originalFormat = processed.indexOf("<html>") != -1 ? "html" : "text";
            if (useHtmlForNonTranslatable && !noTranslatePattern.isEmpty())
                format = "html";
            else if (format == null)
                format = originalFormat;

            ops.add(new TranslationOp(name, processed, new ArrayList<String>(replacer.getContentMap()), format, originalFormat));

            // if (characters > 4000) {
            //
            // LOG.info("Translating " + characters + " characters");
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

            LOG.info("Translating " + ops.size() + " properties");

            String format = null;
            while (!ops.isEmpty()) {
                List<TranslationOp> todo = new ArrayList<TranslationOp>();

                for (TranslationOp op : new ArrayList<TranslationOp>(ops)) {
                    ops.remove(op);

                    if (op.value.length() == 0)
                        continue;

                    if (!hasAnyAlpha(op.value))
                        continue;

                    if (!isIncludeKey(op.keyName))
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
                            LOG.warn("Failed to translate '" + op.value + "'. " + rte.getMessage() + ". Will use processed text.");
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
            LOG.info("Deleting existing target " + target.getName() + " as we have a new translation.");
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
        for (String p : patterns) {
            if (text.matches(p))
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
                if (!op.format.equals(op.originalFormat) && op.originalFormat.equals("text")) {
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
            LOG.info("Loading " + type + " file " + path.getAbsolutePath());
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
                LOG.warn("Could not find cache file " + path + " so a complete translation will be performed");
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
