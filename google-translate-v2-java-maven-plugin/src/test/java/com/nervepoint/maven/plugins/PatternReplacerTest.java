package com.nervepoint.maven.plugins;

import org.junit.Test;

import junit.framework.Assert;

public class PatternReplacerTest {

    @Test
    public void phraseGoogleBugFixed() {
        PatternReplacer pr = createReplacer();
        pr.setUntranslatableStrings("<span class=\"notranslate\">NO_TRANSLATE</span>","NO_TRANSLATE");

        String original = "A new user request on ${approval.connector.name} has been created by you or someone who "
                        + "supplied this email address. You will be informed when your account is ready. "
                        + "You may visit ${approvalURL} at any time before appproval to amend these details.";
        String preProcessed = pr.preProcess(original);
        preProcessed = preProcessed.replace("You may visit <span class=\"notranslate\">NO_TRANSLATE</span> ",
            "You may visit NO_TRANSLATE ");

        String postProcessed = pr.postProcess(preProcessed);
        Assert.assertEquals(original, postProcessed);
    }

    @Test(expected = RuntimeException.class)
    public void phraseGoogleBug() {
        PatternReplacer pr = createReplacer();
        pr.setUntranslatableString("<span class=\"notranslate\">NO_TRANSLATE</span>");

        String original = "A new user request on ${approval.connector.name} has been created by you or someone who "
                        + "supplied this email address. You will be informed when your account is ready. "
                        + "You may visit ${approvalURL} at any time before appproval to amend these details.";
        String preProcessed = pr.preProcess(original);
        preProcessed = preProcessed.replace("You may visit <span class=\"notranslate\">NO_TRANSLATE</span> ",
            "You may visit NO_TRANSLATE ");

        pr.postProcess(preProcessed);
    }

    @Test
    public void simpleTest() {
        PatternReplacer pr = createReplacer();
        check(pr,
            "This text translates, ${thisDoesNot}, ${or1}, %{or2}, ${or.this}, ${or_this}, ${orThis}, ${this1}, %{this2}, #{this},"
                            + " ${!/this}, ${/this}, #{!/this}, %{!/this}, %{/this} #{/this} but this does");
    }

    @Test
    public void testNumberPattern() {
        PatternReplacer pr = createReplacer();
        check(pr, "And now with another pattern {0}");
    }

    @Test
    public void replaceNothing() {
        PatternReplacer pr = createReplacer();
        check(pr, "And now with NOTHING to replace");
    }

    protected PatternReplacer createReplacer() {
        PatternReplacer pr = new PatternReplacer();
        pr.addPattern("\\$\\{[/|!]*[a-zA-Z_\\.0-9]*\\}");
        pr.addPattern("\\%\\{[/|!]*[a-zA-Z_\\.0-9]*\\}");
        pr.addPattern("\\#\\{[/|!]*[a-zA-Z_\\.0-9]*\\}");
        pr.addPattern("\\=\\{[/|!]*[a-zA-Z_\\.0-9]*\\}");
        return pr;
    }

    private static void check(PatternReplacer pr, String original) {
        System.out.println("Original:" + original);
        String preProcessed = pr.preProcess(original);
        System.out.println("Pre-processed:" + preProcessed);
        String postProcessed = pr.postProcess(preProcessed);
        System.out.println("Post-processed:" + postProcessed);
        System.out.println(postProcessed);
        Assert.assertEquals(original, postProcessed);
    }
}
