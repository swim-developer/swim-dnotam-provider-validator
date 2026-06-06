package com.github.swim_developer.validator.dnotam.provider.application.usecase;

import com.github.swim_developer.validator.dnotam.provider.domain.model.GeoCoordinate;
import com.github.swim_developer.validator.dnotam.provider.domain.model.DnotamEvent;
import com.github.swim_developer.validator.dnotam.provider.domain.port.in.GeoCoordinateParserPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

@ApplicationScoped
@Slf4j
public class DnotamEventExtractor {

    private static final String ELEMENT_LOCAL_NAME_NOTAM = "NOTAM";

    private static final Pattern ICAO_LOCATION_CODE = Pattern.compile("^[A-Z]{4}$");

    private static final Pattern HREF_PATH_SEPARATOR = Pattern.compile("/");

    private final GeoCoordinateParserPort mapConverter;

    @Inject
    public DnotamEventExtractor(GeoCoordinateParserPort mapConverter) {
        this.mapConverter = mapConverter;
    }

    public Optional<DnotamEvent> extractFromAixmMessage(String aixmXml) {
        try {
            String normalizedXml = unwrapAixmMessage(aixmXml);
            Document doc = parseDocument(normalizedXml);
            Element eventElement = findFirstElement(doc, "Event");
            if (eventElement == null) {
                log.warn("No Event element found in AIXM message");
                return Optional.empty();
            }
            return Optional.of(buildDnotamEvent(doc, eventElement, aixmXml));
        } catch (ParserConfigurationException | SAXException | IOException | RuntimeException e) {
            log.error("Error extracting DNOTAM event from AIXM message", e);
            return Optional.empty();
        }
    }

    private Document parseDocument(String normalizedXml)
            throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(normalizedXml)));
    }

    private DnotamEvent buildDnotamEvent(Document doc, Element eventElement, String aixmXml) {
        String eventId = extractEventId(eventElement);
        String eventScenario = extractTextContent(eventElement, "scenario");
        String eventSeries = extractEventSeries(eventElement);
        Instant validFrom = extractTimeInstant(eventElement, "beginPosition");
        Instant validTo = extractTimeInstant(eventElement, "endPosition");
        String airportHeliport = extractAirportCode(eventElement);
        String airspace = extractAirspaceCode(eventElement);
        String publisher = extractPublisher(doc);
        String provider = extractProvider(doc);
        String notamCoordinates = extractNotamCoordinates(eventElement);
        Double latitude = null;
        Double longitude = null;
        Optional<GeoCoordinate> geoCoord = extractGeoCoordinates(doc, notamCoordinates);
        if (geoCoord.isPresent()) {
            latitude = geoCoord.get().latitude();
            longitude = geoCoord.get().longitude();
            log.debug("Extracted coordinates: lat={}, lon={}", latitude, longitude);
        }
        return new DnotamEvent(
                eventId,
                eventScenario,
                airportHeliport,
                airspace,
                eventSeries,
                publisher,
                provider,
                validFrom,
                validTo,
                aixmXml,
                latitude,
                longitude,
                notamCoordinates
        );
    }

    private Optional<GeoCoordinate> extractGeoCoordinates(Document doc, String notamCoords) {
        Element gmlPos = findFirstElement(doc, "pos");
        if (gmlPos != null) {
            Optional<GeoCoordinate> coord = mapConverter.parseGmlPos(gmlPos.getTextContent());
            if (coord.isPresent()) {
                return coord;
            }
        }

        if (notamCoords != null && !notamCoords.isBlank()) {
            return mapConverter.parseNotamCoordinates(notamCoords);
        }

        return Optional.empty();
    }

    private String extractNotamCoordinates(Element eventElement) {
        Element notamElement = findFirstElement(eventElement, ELEMENT_LOCAL_NAME_NOTAM);
        if (notamElement != null) {
            Element coordinates = findFirstElement(notamElement, "coordinates");
            if (coordinates != null) {
                return coordinates.getTextContent().trim();
            }
        }
        return null;
    }

    private String extractEventId(Element eventElement) {
        String gmlId = eventElement.getAttribute("gml:id");
        if (gmlId != null && !gmlId.isEmpty()) {
            return gmlId;
        }

        Element identifier = findFirstElement(eventElement, "identifier");
        return identifier != null ? identifier.getTextContent().trim() : "unknown";
    }

    private String extractEventSeries(Element eventElement) {
        Element notamElement = findFirstElement(eventElement, ELEMENT_LOCAL_NAME_NOTAM);
        if (notamElement != null) {
            Element series = findFirstElement(notamElement, "series");
            if (series != null) {
                return series.getTextContent().trim();
            }
        }
        return null;
    }

    private String extractAirportCode(Element eventElement) {
        Element notamElement = findFirstElement(eventElement, ELEMENT_LOCAL_NAME_NOTAM);
        if (notamElement != null) {
            Element location = findFirstElement(notamElement, "location");
            if (location != null) {
                String locationCode = location.getTextContent().trim();
                if (ICAO_LOCATION_CODE.matcher(locationCode).matches()) {
                    return locationCode;
                }
            }
        }

        Element concernedAirspace = findFirstElement(eventElement, "concernedAirspace");
        if (concernedAirspace != null) {
            String href = concernedAirspace.getAttribute("xlink:href");
            if (href != null && href.contains("AirportHeliport")) {
                return extractIcaoFromHref(href);
            }
        }

        return null;
    }

    private String extractAirspaceCode(Element eventElement) {
        Element concernedAirspace = findFirstElement(eventElement, "concernedAirspace");
        if (concernedAirspace != null) {
            String href = concernedAirspace.getAttribute("xlink:href");
            if (href != null && href.contains("Airspace")) {
                return extractIcaoFromHref(href);
            }
        }

        Element notamElement = findFirstElement(eventElement, ELEMENT_LOCAL_NAME_NOTAM);
        if (notamElement != null) {
            Element affectedFIR = findFirstElement(notamElement, "affectedFIR");
            if (affectedFIR != null) {
                return affectedFIR.getTextContent().trim();
            }
        }

        return null;
    }

    private String extractPublisher(Document doc) {
        Element root = doc.getDocumentElement();
        String publisher = root.getAttribute("publisher");
        return publisher != null && !publisher.isEmpty() ? publisher : null;
    }

    private String extractProvider(Document doc) {
        Element root = doc.getDocumentElement();
        String provider = root.getAttribute("provider");
        return provider != null && !provider.isEmpty() ? provider : null;
    }

    private String extractIcaoFromHref(String href) {
        String[] parts = HREF_PATH_SEPARATOR.split(href);
        String lastPart = parts[parts.length - 1];
        if (ICAO_LOCATION_CODE.matcher(lastPart).matches()) {
            return lastPart;
        }
        return null;
    }

    private String extractTextContent(Element parent, String tagName) {
        Element element = findFirstElement(parent, tagName);
        return element != null ? element.getTextContent().trim() : null;
    }

    private Instant extractTimeInstant(Element parent, String tagName) {
        String timeStr = extractTextContent(parent, tagName);
        if (timeStr == null || timeStr.isEmpty()) {
            return null;
        }

        try {
            return Instant.parse(timeStr);
        } catch (Exception e) {
            log.warn("Failed to parse time: {}", timeStr);
            return null;
        }
    }

    private Element findFirstElement(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() > 0) {
            return (Element) nodes.item(0);
        }
        return null;
    }

    private Element findFirstElement(Document doc, String localName) {
        NodeList nodes = doc.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() > 0) {
            return (Element) nodes.item(0);
        }
        return null;
    }

    private String unwrapAixmMessage(String xml) {
        if (xml == null || xml.isBlank()) {
            return xml;
        }
        String trimmed = xml.trim();
        if (isStandaloneAixmMessage(trimmed)) {
            return trimmed;
        }
        String fromCdata = extractAixmFromCdata(trimmed);
        if (fromCdata != null) {
            return fromCdata;
        }
        String fromWrapper = extractAixmFromWrapper(trimmed);
        if (fromWrapper != null) {
            return fromWrapper;
        }
        return trimmed;
    }

    private boolean isStandaloneAixmMessage(String trimmed) {
        return (trimmed.contains("<message:AIXMBasicMessage") || trimmed.contains("<aixm:AIXMBasicMessage"))
                && !trimmed.startsWith("<messages");
    }

    private String extractAixmFromCdata(String trimmed) {
        int cdataStart = trimmed.indexOf("<![CDATA[");
        if (cdataStart == -1) {
            return null;
        }
        int contentStart = cdataStart + 9;
        int cdataEnd = trimmed.indexOf("]]>", contentStart);
        if (cdataEnd == -1) {
            return null;
        }
        String extracted = trimmed.substring(contentStart, cdataEnd).trim();
        log.debug("Extracted AIXM from CDATA wrapper");
        return extracted;
    }

    private String extractAixmFromWrapper(String trimmed) {
        int aixmStart = trimmed.indexOf("<message:AIXMBasicMessage");
        if (aixmStart == -1) {
            aixmStart = trimmed.indexOf("<aixm:AIXMBasicMessage");
        }
        if (aixmStart == -1) {
            return null;
        }
        String extracted = trimmed.substring(aixmStart);
        int closeTag = extracted.lastIndexOf("</message:AIXMBasicMessage>");
        if (closeTag == -1) {
            closeTag = extracted.lastIndexOf("</aixm:AIXMBasicMessage>");
        }
        if (closeTag == -1) {
            return null;
        }
        extracted = extracted.substring(0, closeTag + (extracted.contains("</message:") ? 27 : 25));
        log.debug("Extracted AIXM from wrapper elements");
        return extracted;
    }
}

