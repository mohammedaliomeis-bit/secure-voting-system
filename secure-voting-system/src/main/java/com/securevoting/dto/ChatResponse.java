package com.securevoting.dto;

import java.util.List;

public record ChatResponse(
        String intent,
        String answer,
        List<String> suggestions
) {}