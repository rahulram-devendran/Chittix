package com.companybrain.dto;

public record ErrorResponse(int status, String error, String message) {
}
