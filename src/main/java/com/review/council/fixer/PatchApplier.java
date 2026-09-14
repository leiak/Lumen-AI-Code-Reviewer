package com.review.council.fixer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PatchApplier {
    private PatchApplier() {}

    /** Returns true if applied, false if oldText not found. */
    public static boolean applyReplace(Path file, String oldText, String newText) throws Exception {
        var content = Files.readString(file, StandardCharsets.UTF_8);
        if (!content.contains(oldText)) return false;
        Files.writeString(file, content.replace(oldText, newText), StandardCharsets.UTF_8);
        return true;
    }
}
