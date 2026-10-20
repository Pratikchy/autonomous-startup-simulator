package com.startupsimulator.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoworkerChatRequestDto {

    @NotBlank(message = "Message content cannot be blank")
    private String message;
}
