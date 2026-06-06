package com.github.swim_developer.validator.dnotam.provider.domain.port.in;

import com.github.swim_developer.validator.dnotam.provider.domain.model.GeoCoordinate;

import java.util.Optional;

public interface GeoCoordinateParserPort {
    Optional<GeoCoordinate> parseGmlPos(String gmlPos);
    Optional<GeoCoordinate> parseNotamCoordinates(String notamCoords);
}
