package com.github.swim_developer.validator.dnotam.provider.infrastructure.map;

import com.github.swim_developer.validator.dnotam.provider.domain.port.in.GeoCoordinateParserPort;

import com.github.swim_developer.validator.dnotam.provider.infrastructure.map.SvgPoint;
import com.github.swim_developer.validator.dnotam.provider.domain.model.GeoCoordinate;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
public class EuropeMapConverter implements GeoCoordinateParserPort {

    private static final double SVG_WIDTH = 1401.34;
    private static final double SVG_HEIGHT = 1198.34;

    private static final double CENTER_LAT = 52.0;
    private static final double CENTER_LON = 10.0;

    private static final double SCALE = 14.5;

    private static final double SVG_CENTER_X = SVG_WIDTH / 2.0;
    private static final double SVG_CENTER_Y = SVG_HEIGHT / 2.0;

    private static final Pattern NOTAM_COORD_PATTERN = 
        Pattern.compile("(\\d{2})(\\d{2})([NS])(\\d{3})(\\d{2})([EW])");

    private static final Pattern NOTAM_COORD_WITH_SECONDS_PATTERN = 
        Pattern.compile("(\\d{2})(\\d{2})(\\d{2})([NS])\\s*(\\d{3})(\\d{2})(\\d{2})([EW])");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public Optional<SvgPoint> geoToSvg(double latitude, double longitude) {
        if (latitude < 25.0 || latitude > 80.0 || longitude < -45.0 || longitude > 60.0) {
            log.debug("Coordinates out of Europe bounds: lat={}, lon={}", latitude, longitude);
            return Optional.empty();
        }

        double[] xy = laeaProject(latitude, longitude);
        double x = SVG_CENTER_X + xy[0] * SCALE;
        double y = SVG_CENTER_Y - xy[1] * SCALE;

        if (x < 0 || x > SVG_WIDTH || y < 0 || y > SVG_HEIGHT) {
            log.debug("Projected point outside SVG bounds: x={}, y={}", x, y);
            return Optional.empty();
        }

        return Optional.of(new SvgPoint(Math.round(x * 10) / 10.0, Math.round(y * 10) / 10.0));
    }

    private double[] laeaProject(double lat, double lon) {
        double phi = Math.toRadians(lat);
        double lambda = Math.toRadians(lon);
        double phi0 = Math.toRadians(CENTER_LAT);
        double lambda0 = Math.toRadians(CENTER_LON);

        double sinPhi = Math.sin(phi);
        double cosPhi = Math.cos(phi);
        double sinPhi0 = Math.sin(phi0);
        double cosPhi0 = Math.cos(phi0);
        double cosLambdaDiff = Math.cos(lambda - lambda0);
        double sinLambdaDiff = Math.sin(lambda - lambda0);

        double k = Math.sqrt(2.0 / (1.0 + sinPhi0 * sinPhi + cosPhi0 * cosPhi * cosLambdaDiff));

        double x = k * cosPhi * sinLambdaDiff;
        double y = k * (cosPhi0 * sinPhi - sinPhi0 * cosPhi * cosLambdaDiff);

        return new double[] { x * 40.0, y * 40.0 };
    }

    public Optional<SvgPoint> geoToSvg(GeoCoordinate coord) {
        return geoToSvg(coord.latitude(), coord.longitude());
    }

    @Override
    public Optional<GeoCoordinate> parseNotamCoordinates(String notamCoords) {
        if (notamCoords == null || notamCoords.isBlank()) {
            return Optional.empty();
        }

        String cleaned = WHITESPACE.matcher(notamCoords).replaceAll("").toUpperCase();

        Matcher matcherWithSeconds = NOTAM_COORD_WITH_SECONDS_PATTERN.matcher(cleaned);
        if (matcherWithSeconds.find()) {
            return parseWithSeconds(matcherWithSeconds);
        }

        Matcher matcher = NOTAM_COORD_PATTERN.matcher(cleaned);
        if (matcher.find()) {
            return parseWithoutSeconds(matcher);
        }

        log.warn("Could not parse NOTAM coordinates: {}", notamCoords);
        return Optional.empty();
    }

    private Optional<GeoCoordinate> parseWithoutSeconds(Matcher matcher) {
        try {
            int latDeg = Integer.parseInt(matcher.group(1));
            int latMin = Integer.parseInt(matcher.group(2));
            String latDir = matcher.group(3);

            int lonDeg = Integer.parseInt(matcher.group(4));
            int lonMin = Integer.parseInt(matcher.group(5));
            String lonDir = matcher.group(6);

            double latitude = latDeg + latMin / 60.0;
            if ("S".equals(latDir)) latitude = -latitude;

            double longitude = lonDeg + lonMin / 60.0;
            if ("W".equals(lonDir)) longitude = -longitude;

            return Optional.of(new GeoCoordinate(latitude, longitude));
        } catch (NumberFormatException e) {
            log.warn("Failed to parse coordinates: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<GeoCoordinate> parseWithSeconds(Matcher matcher) {
        try {
            int latDeg = Integer.parseInt(matcher.group(1));
            int latMin = Integer.parseInt(matcher.group(2));
            int latSec = Integer.parseInt(matcher.group(3));
            String latDir = matcher.group(4);

            int lonDeg = Integer.parseInt(matcher.group(5));
            int lonMin = Integer.parseInt(matcher.group(6));
            int lonSec = Integer.parseInt(matcher.group(7));
            String lonDir = matcher.group(8);

            double latitude = latDeg + latMin / 60.0 + latSec / 3600.0;
            if ("S".equals(latDir)) latitude = -latitude;

            double longitude = lonDeg + lonMin / 60.0 + lonSec / 3600.0;
            if ("W".equals(lonDir)) longitude = -longitude;

            return Optional.of(new GeoCoordinate(latitude, longitude));
        } catch (NumberFormatException e) {
            log.warn("Failed to parse coordinates with seconds: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<GeoCoordinate> parseGmlPos(String gmlPos) {
        if (gmlPos == null || gmlPos.isBlank()) {
            return Optional.empty();
        }

        String[] parts = WHITESPACE.split(gmlPos.trim());
        if (parts.length >= 2) {
            try {
                double lat = Double.parseDouble(parts[0]);
                double lon = Double.parseDouble(parts[1]);
                return Optional.of(new GeoCoordinate(lat, lon));
            } catch (NumberFormatException e) {
                log.warn("Failed to parse gml:pos: {}", gmlPos);
            }
        }
        return Optional.empty();
    }

    public String generateEventMarker(String eventId, String scenario, double x, double y, String tooltip) {
        String color = getColorForScenario();
        String escapedTooltip = escapeXml(tooltip);
        
        return String.format(
            """
            <g class="event-marker" data-event-id="%s" data-scenario="%s" transform="translate(%.1f, %.1f)">
              <circle r="6" fill="%s" stroke="#333" stroke-width="1.5"/>
              <title>%s</title>
            </g>
            """,
            escapeXml(eventId), escapeXml(scenario), x, y, color, escapedTooltip
        );
    }

    public String getColorForScenario() {
        return "#e65100";
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }

    public double getSvgWidth() {
        return SVG_WIDTH;
    }

    public double getSvgHeight() {
        return SVG_HEIGHT;
    }
}

