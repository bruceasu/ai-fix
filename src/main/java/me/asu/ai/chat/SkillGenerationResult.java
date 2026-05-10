package me.asu.ai.chat;

import java.nio.file.Path;

public record SkillGenerationResult(Path skillFile, String validationMessage) {
}