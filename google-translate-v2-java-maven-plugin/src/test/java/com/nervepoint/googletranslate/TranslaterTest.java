package com.nervepoint.googletranslate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Properties;
import java.util.UUID;

import org.codehaus.plexus.util.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import com.nervepoint.googletranslate.Translater.FileProvider;

import edu.emory.mathcs.backport.java.util.Arrays;

public class TranslaterTest {

    static File cacheDir;
    static File targetDir;
    static {
        cacheDir = new File(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString() + ".test");
        targetDir = new File("target/test-translations");
    }

    @Test
    public void simpleTest() throws IOException {
        Translater t = createTranslater();
        StringFileProvider p = new StringFileProvider("hello", "Hello World");
        t.setLanguages("fr");
        t.setFileProvider(p);
        t.execute();
        Assert.assertEquals("Bonjour le monde", p.getResult("fr"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testNoTranslate() throws IOException {
        Translater t = createTranslater();
        StringFileProvider p = new StringFileProvider("hello", "Hello ${0} ${abc} World");
        t.setNoTranslatePattern(Arrays.asList(new String[] { "\\$\\{[/|!]*[a-zA-Z_\\.0-9]*\\}", "\\{[0-9]+\\}" }));
        t.setLanguages("fr");
        t.setFileProvider(p);
        t.execute();
        Assert.assertEquals("Bonjour ${0} ${abc} Monde", p.getResult("fr"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testContainEncodedHTML() throws IOException {
        Translater t = createTranslater();
        StringFileProvider p = new StringFileProvider("hello", "Hello &lt;script&gt; and &lt;/script&gt; World");
        t.setNoTranslatePattern(Arrays.asList(new String[] { "\\&lt\\;[\\w/]+\\&gt\\;" }));
        t.setLanguages("fr");
        t.setFileProvider(p);
        t.execute();
        Assert.assertEquals("Bonjour &lt;script&gt; et &lt;/script&gt; World", p.getResult("fr"));
    }

    Translater createTranslater() throws IOException {
        Properties p = new Properties();
        InputStream in = getClass().getResourceAsStream("/translate.properties");
        try {
            p.load(in);
        } finally {
            in.close();
        }

        /*
         * Use a global cache directory for most tests that is cleaned out each
         * time so translation happens
         */
        FileUtils.deleteDirectory(cacheDir);
        cacheDir.mkdirs();
        cacheDir.deleteOnExit();

        Translater t = new Translater();
        t.setApikey(p.getProperty("apikey"));
        t.setCacheDir(cacheDir);
        t.setUseHtmlForNonTranslatable(true);
        t.setTargetDirectory(targetDir);

        return t;
    }

    class StringFileProvider implements FileProvider {
        File f;
        String key;

        public StringFileProvider(String key, String str) throws IOException {
            this.key = key;
            Properties p = new Properties();
            p.setProperty(key, str);
            f = File.createTempFile("string", ".properties");
            f.deleteOnExit();
            PrintWriter pw = new PrintWriter(new FileWriter(f), true);
            try {
                p.store(pw, "Test translation");
            } finally {
                pw.close();
            }

        }

        public String getResult(String lang) throws IOException {
            Properties p = new Properties();
            String fp = f.getName();
            fp = fp.substring(0, fp.length() - 11);
            InputStream in = new FileInputStream(new File(targetDir, fp + "_" + lang + ".properties"));
            try {
                p.load(in);
            } finally {
                in.close();
            }
            return p.getProperty(key);
        }

        @SuppressWarnings("unchecked")
        @Override
        public Iterable<File> getFiles() throws IOException {
            return Arrays.asList(new File[] { f });
        }

    }
}
