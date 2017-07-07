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
    private String[] untranslatableStrings = new String[] { "_999_" };

    private List<String> patterns = new ArrayList<String>();
    private List<String> contentMap = new ArrayList<String>();

    public PatternReplacer() {
    }

    public String getUntranslatableString() {
        return untranslatableStrings.length == 0 ? null : untranslatableStrings[0];
    }
    
    public void setUntranslatableString(String untranslatableString) {
        this.untranslatableStrings = new String[] { untranslatableString };
    }

    public String[] getUntranslatableStrings() {
        return untranslatableStrings;
    }
    
    public void setUntranslatableStrings(String... untranslatableStrings) {
        this.untranslatableStrings = untranslatableStrings;
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
                m.appendReplacement(sb, untranslatableStrings[0]);
            }
            m.appendTail(sb);
            content = m.replaceAll(untranslatableStrings[0]);
        }
        return content;
    }

    public String postProcess(String content) {
        String originalContent = content;
        int idx;
        int i;
        for (String s : contentMap) {
            idx = -1;
            i = 0;
            while(idx == -1 && i < untranslatableStrings.length) {
                idx = content.indexOf(untranslatableStrings[i]);
                if(idx == -1)
                   i++;
            }
            if(idx == -1) {
                throw new RuntimeException("Expected to find an untranslateable string, but there was not one. The string we were given was '" + originalContent + "'. So far, we replace variables so it contains '" + content + "'. The content map contains " + contentMap.size() + " variables that should be replaced.");
            }
            content = content.substring(0, idx) + s + content.substring(idx + untranslatableStrings[i].length());
        }
        return content;
    }
}
