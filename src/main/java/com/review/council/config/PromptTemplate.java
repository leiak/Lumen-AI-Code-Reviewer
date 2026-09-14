package com.review.council.config;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record PromptTemplate(String raw) {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    public String render(Map<String, ?> vars) {
        Matcher m = PLACEHOLDER.matcher(raw);
        var sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            Object val = vars.get(key);
            if (val == null) {
                throw new IllegalArgumentException("Missing placeholder: " + key);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(val.toString()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static PromptTemplate fromResource(String classpathPath) {
        try (var in = PromptTemplate.class.getResourceAsStream("/" + classpathPath)) {
            if (in == null) throw new IllegalArgumentException("Not found: " + classpathPath);
            return new PromptTemplate(new String(in.readAllBytes()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load template: " + classpathPath, e);
        }
    }
}
