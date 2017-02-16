package com.nervepoint.maven.plugins;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatternReplacer {

    /**
     * A string that the translate API will not translate. This is the best I
     * could find that works well enough for now. Anything we don't want
     * translated (such a Wicket variables) gets turned into this before being
     * sent to the translator. The result content is then processed again,
     * putting the variable names back
     */
    private String untranslatableString = "_999_";

    private List<String> patterns = new ArrayList<String>();
    private List<String> contentMap = new ArrayList<String>();

    public PatternReplacer() {
    }

    public String getUntranslatableString() {
        return untranslatableString;
    }
    
    public void setUntranslatableString(String untranslatableString) {
        this.untranslatableString = untranslatableString;
    }

    public void addPattern(String pattern) {
        patterns.add(pattern);
    }

    public List<String> getContentMap() {
        return contentMap;
    }

    public String preProcess(String content) {
        contentMap.clear();
        if (patterns.size() > 0) {
            StringBuilder b = new StringBuilder();
            for (String p : patterns) {
                if (b.length() > 0) {
                    b.append("|");
                }
                b.append(p);
            }
            Pattern p = Pattern.compile(b.toString());
            Matcher m = p.matcher(content);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String match = m.group();
                contentMap.add(match);
                m.appendReplacement(sb, untranslatableString);
            }
            m.appendTail(sb);
            content = m.replaceAll(untranslatableString);
        }
        return content;
    }

    public String postProcess(String content) {
        String originalContent = content;
        int idx;
        for (String s : contentMap) {
            idx = content.indexOf(untranslatableString);
            if(idx == -1) {
                throw new RuntimeException("Expected to find an untranslateable string, but there was not one. The string we were given was '" + originalContent + "'. So far, we replace variables so it contains '" + content + "'. The content map contains " + contentMap.size() + " variables that should be replaced.");
            }
            content = content.substring(0, idx) + s + content.substring(idx + untranslatableString.length());
        }
        return content;
    }

    public static void main(String[] args) {
        PatternReplacer pr = new PatternReplacer();
        pr.addPattern("\\$\\{\\w*\\}");
        pr.addPattern("\\%\\{\\w*\\}");
        check(pr, "This text translates, ${thisDoesNot}, ${or1}, %{or2}, ${this1}, %{this2}, but this does");
        check(pr, "And now with NOTHING to replace");
        check(pr, "And now with another pattern {0}");

    }

    private static void check(PatternReplacer pr, String original) {
        System.out.println("Original:" + original);
        String preProcessed = pr.preProcess(original);
        System.out.println("Pre-processed:" + preProcessed);
        String postProcessed = pr.postProcess(preProcessed);
        System.out.println("Post-processed:" + postProcessed);
        System.out.println(postProcessed);
        if (postProcessed.equals(original)) {
            System.out.println("Replaced as expected");
        } else {
            System.out.println("Failed!");
        }
    }
}
