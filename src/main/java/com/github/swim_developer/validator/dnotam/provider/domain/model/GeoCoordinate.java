package com.github.swim_developer.validator.dnotam.provider.domain.model;

public record GeoCoordinate(double latitude, double longitude) {

    public boolean isInEuropeBounds() {
        return latitude >= 25.0 && latitude <= 80.0
                && longitude >= -45.0 && longitude <= 60.0;
    }
}
