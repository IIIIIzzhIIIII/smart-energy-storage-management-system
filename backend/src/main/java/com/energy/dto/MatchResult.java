package com.energy.dto;

import lombok.Data;

@Data
public class MatchResult {
    private boolean success;
    private String message;
    private Double sampleValue;
}