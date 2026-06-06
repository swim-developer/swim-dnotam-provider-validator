package com.github.swim_developer.validator.dnotam.provider.infrastructure.map;

import com.github.swim_developer.validator.dnotam.provider.domain.port.out.MapRenderPort;

import com.github.swim_developer.validator.dnotam.provider.infrastructure.map.SvgPoint;
import com.github.swim_developer.validator.dnotam.provider.domain.model.ReceivedMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
public class MapService implements MapRenderPort {

    private static final String MAP_TEMPLATE_PATH = "META-INF/resources/static/maps/Europe_laea_location_map.svg";
    private static final String EVENTS_GROUP_ID = "dnotam-events";
    private static final double COORD_TOLERANCE = 0.01;

    private final EuropeMapConverter mapConverter;

    private volatile String cachedMapTemplate;

    @Inject
    public MapService(EuropeMapConverter mapConverter) {
        this.mapConverter = mapConverter;
    }

    @Override
    public String generateMapWithEvents(List<ReceivedMessage> events) {
        String template = getMapTemplate();
        if (template == null) {
            return generateFallbackSvg("Error loading map template");
        }

        Map<String, List<ReceivedMessage>> grouped = groupByLocation(events);

        StringBuilder eventMarkers = new StringBuilder();
        eventMarkers.append("\n  <g id=\"").append(EVENTS_GROUP_ID).append("\">\n");

        int plotted = 0;
        for (List<ReceivedMessage> group : grouped.values()) {
            Optional<String> marker = generateMarkerForGroup(group);
            if (marker.isPresent()) {
                eventMarkers.append("    ").append(marker.get()).append("\n");
                plotted += group.size();
            }
        }

        eventMarkers.append("  </g>\n");

        String styles = """
            <style>
              .event-marker, .event-cluster { cursor: pointer; }
              .event-marker:hover circle, .event-cluster:hover circle { 
                stroke-width: 3; 
                filter: brightness(1.2);
              }
            </style>
            """;

        String result = template.replace("</svg>", styles + eventMarkers.toString() + "</svg>");

        log.info("Generated map with {} events in {} locations out of {} total", 
            plotted, grouped.size(), events.size());
        return result;
    }

    private Map<String, List<ReceivedMessage>> groupByLocation(List<ReceivedMessage> events) {
        Map<String, List<ReceivedMessage>> grouped = new HashMap<>();
        
        for (ReceivedMessage event : events) {
            if (event.getLatitude() == null || event.getLongitude() == null) {
                continue;
            }
            String key = createLocationKey(event.getLatitude(), event.getLongitude());
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(event);
        }
        
        return grouped;
    }

    private String createLocationKey(double lat, double lon) {
        long latKey = Math.round(lat / COORD_TOLERANCE);
        long lonKey = Math.round(lon / COORD_TOLERANCE);
        return latKey + ":" + lonKey;
    }

    private Optional<String> generateMarkerForGroup(List<ReceivedMessage> group) {
        if (group.isEmpty()) {
            return Optional.empty();
        }

        ReceivedMessage first = group.get(0);
        Optional<SvgPoint> point = mapConverter.geoToSvg(
            first.getLatitude(), first.getLongitude()
        );

        if (point.isEmpty()) {
            log.debug("Group coordinates out of bounds: [{}, {}]", 
                first.getLatitude(), first.getLongitude());
            return Optional.empty();
        }

        int count = group.size();
        String tooltip = buildGroupTooltip(group);
        String scenario = first.getEventScenario() != null ? first.getEventScenario() : "UNKNOWN";

        if (count == 1) {
            return Optional.of(mapConverter.generateEventMarker(
                first.getEventId() != null ? first.getEventId() : "unknown",
                scenario,
                point.get().x(),
                point.get().y(),
                tooltip
            ).trim());
        }

        return Optional.of(generateBadgeMarker(
            point.get().x(),
            point.get().y(),
            count,
            tooltip
        ));
    }

    private String generateBadgeMarker(double x, double y, int count, String tooltip) {
        String color = mapConverter.getColorForScenario();
        String escapedTooltip = escapeXml(tooltip);
        
        int radius = 10 + Math.min(count, 10);
        
        return String.format(
            """
            <g class="event-cluster" transform="translate(%.1f, %.1f)">
              <circle r="%d" fill="%s" stroke="#333" stroke-width="2" opacity="0.9"/>
              <text x="0" y="4" text-anchor="middle" fill="white" font-size="10" font-weight="bold">%d</text>
              <title>%s</title>
            </g>
            """,
            x, y, radius, color, count, escapedTooltip
        );
    }

    private String buildGroupTooltip(List<ReceivedMessage> group) {
        if (group.size() == 1) {
            return buildTooltip(group.get(0));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(group.size()).append(" events:\n");
        
        int shown = 0;
        for (ReceivedMessage event : group) {
            if (shown >= 5) {
                sb.append("... and ").append(group.size() - 5).append(" more");
                break;
            }
            if (shown > 0) sb.append("\n");
            sb.append("• ");
            if (event.getEventScenario() != null) {
                sb.append(event.getEventScenario());
            }
            if (event.getAirportHeliport() != null) {
                sb.append(" (").append(event.getAirportHeliport()).append(")");
            }
            shown++;
        }
        
        return sb.toString();
    }

    private String buildTooltip(ReceivedMessage event) {
        StringBuilder sb = new StringBuilder();
        
        if (event.getAirportHeliport() != null) {
            sb.append(event.getAirportHeliport());
        }
        
        if (event.getEventScenario() != null) {
            if (!sb.isEmpty()) {
                sb.append(" - ");
            }
            sb.append(event.getEventScenario());
        }
        
        if (event.getValidFrom() != null) {
            sb.append("\nFrom: ").append(event.getValidFrom());
        }
        
        if (event.getValidTo() != null) {
            sb.append("\nTo: ").append(event.getValidTo());
        }

        return sb.toString();
    }

    private String getMapTemplate() {
        if (cachedMapTemplate != null) {
            return cachedMapTemplate;
        }

        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(MAP_TEMPLATE_PATH)) {
            if (is == null) {
                log.error("Map template not found: {}", MAP_TEMPLATE_PATH);
                return null;
            }

            cachedMapTemplate = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));

            log.info("Loaded map template: {} bytes", cachedMapTemplate.length());
            return cachedMapTemplate;

        } catch (Exception e) {
            log.error("Error loading map template", e);
            return null;
        }
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }

    private String generateFallbackSvg(String message) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 700 380" width="700" height="380">
              <rect fill="#f0f0f0" width="700" height="380"/>
              <text x="350" y="190" text-anchor="middle" fill="#666" font-size="16">%s</text>
            </svg>
            """.formatted(message);
    }
}

