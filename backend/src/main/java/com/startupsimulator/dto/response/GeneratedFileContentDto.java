package com.startupsimulator.dto.response;

public record GeneratedFileContentDto(
        String path,
        String name,
        String content,
        long sizeBytes
) {}
