package com.example.loans.dto.request;
import jakarta.validation.constraints.*;
public record ReviewRequest(@NotBlank @Size(max=60) String reasonCode,@NotBlank @Size(max=2000) String explanation) {}

