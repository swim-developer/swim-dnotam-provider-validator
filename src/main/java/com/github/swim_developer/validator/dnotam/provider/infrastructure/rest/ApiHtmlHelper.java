package com.github.swim_developer.validator.dnotam.provider.infrastructure.rest;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ApiHtmlHelper {

    public String formatXmlForDisplay(String xml, String eventId) {
        String escapedXml = xml != null ? escapeHtml(xml) : "";

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>AIXM Message - %s</title>
                    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/themes/prism-tomorrow.min.css">
                    <style>
                        body { margin: 0; padding: 0; background: #1e1e1e; color: #d4d4d4; font-family: 'Fira Code', monospace; }
                        .header { background: #2d2d2d; padding: 15px 20px; border-bottom: 1px solid #404040; display: flex; justify-content: space-between; align-items: center; }
                        .header h1 { margin: 0; font-size: 1rem; color: #e94560; }
                        .header .info { color: #888; font-size: 0.85rem; }
                        .content { padding: 20px; overflow-x: auto; }
                        pre { margin: 0; white-space: pre-wrap; word-wrap: break-word; }
                        code { font-size: 0.9rem; line-height: 1.6; }
                    </style>
                </head>
                <body>
                    <div class="header">
                        <h1>📄 AIXM Message</h1>
                        <span class="info">Event ID: %s</span>
                    </div>
                    <div class="content">
                        <pre><code class="language-xml">%s</code></pre>
                    </div>
                    <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/prism.min.js"></script>
                    <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/components/prism-markup.min.js"></script>
                    <script>document.addEventListener('DOMContentLoaded', function() { Prism.highlightAll(); });</script>
                </body>
                </html>
                """.formatted(eventId != null ? eventId : "Unknown", eventId != null ? eventId : "-", escapedXml);
    }

    public String buildEventsMapHtml(String svg) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>DNOTAM Events Map</title>
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        body {
                            background: #1a1a2e;
                            min-height: 100vh;
                            display: flex;
                            flex-direction: column;
                            align-items: center;
                            padding: 20px;
                            font-family: 'Segoe UI', sans-serif;
                        }
                        h1 { color: #e94560; margin-bottom: 20px; font-size: 1.5rem; }
                        .map-container {
                            background: white;
                            border-radius: 12px;
                            padding: 20px;
                            box-shadow: 0 10px 40px rgba(0,0,0,0.3);
                            max-width: 100%%;
                            overflow: auto;
                            position: relative;
                        }
                        svg { display: block; }
                        .legend { display: flex; flex-wrap: wrap; gap: 15px; margin-top: 20px; color: #ccc; font-size: 0.85rem; }
                        .legend-item { display: flex; align-items: center; gap: 5px; }
                        .legend-dot { width: 12px; height: 12px; border-radius: 50%%; }
                        .refresh-info { color: #666; margin-top: 15px; font-size: 0.8rem; }
                        #custom-tooltip {
                            position: fixed;
                            background: rgba(0,0,0,0.9);
                            color: white;
                            padding: 12px 16px;
                            border-radius: 8px;
                            font-size: 14px;
                            white-space: pre-line;
                            pointer-events: none;
                            z-index: 1000;
                            display: none;
                            max-width: 300px;
                            box-shadow: 0 4px 20px rgba(0,0,0,0.4);
                            border: 1px solid #444;
                        }
                    </style>
                </head>
                <body>
                    <h1>🗺️ DNOTAM Events Map</h1>
                    <div class="map-container">
                        %s
                    </div>
                    <div class="legend">
                        <div class="legend-item"><span class="legend-dot" style="background:#e74c3c"></span> Closure</div>
                        <div class="legend-item"><span class="legend-dot" style="background:#f39c12"></span> Limitation</div>
                        <div class="legend-item"><span class="legend-dot" style="background:#9b59b6"></span> Navaid</div>
                        <div class="legend-item"><span class="legend-dot" style="background:#3498db"></span> Obstacle</div>
                        <div class="legend-item"><span class="legend-dot" style="background:#e67e22"></span> Airspace</div>
                        <div class="legend-item"><span class="legend-dot" style="background:#1abc9c"></span> Surface</div>
                        <div class="legend-item"><span class="legend-dot" style="background:#c0392b"></span> Wildlife</div>
                    </div>
                    <p class="refresh-info">Auto-refresh: <a href="" style="color:#e94560">Reload</a></p>
                    <div id="custom-tooltip"></div>
                    <script>
                        const tooltip = document.getElementById('custom-tooltip');
                        document.querySelectorAll('.event-marker, .event-cluster').forEach(el => {
                            const title = el.querySelector('title');
                            if (title) {
                                el.addEventListener('mouseenter', e => {
                                    tooltip.textContent = title.textContent;
                                    tooltip.style.display = 'block';
                                });
                                el.addEventListener('mousemove', e => {
                                    tooltip.style.left = (e.clientX + 15) + 'px';
                                    tooltip.style.top = (e.clientY + 15) + 'px';
                                });
                                el.addEventListener('mouseleave', () => {
                                    tooltip.style.display = 'none';
                                });
                            }
                        });
                    </script>
                </body>
                </html>
                """.formatted(svg);
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
