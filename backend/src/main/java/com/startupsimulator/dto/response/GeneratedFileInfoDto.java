package com.startupsimulator.dto.response;

public record GeneratedFileInfoDto(
        String path,
        String name,
        long sizeBytes,
        boolean isDirectory,
        String fileType
) {}
