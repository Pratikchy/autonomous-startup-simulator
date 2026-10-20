package com.startupsimulator.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProposedFileChangeDto {
    private String relativePath;
    private String action; // CREATE, MODIFY, DELETE
    private String description;
    private String content;
}
