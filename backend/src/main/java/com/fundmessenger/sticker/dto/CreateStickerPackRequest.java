package com.fundmessenger.sticker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateStickerPackRequest {
    @NotBlank
    @Size(max = 30)
    private String name;

    private int price;
}
