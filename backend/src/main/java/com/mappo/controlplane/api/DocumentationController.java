package com.mappo.controlplane.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DocumentationController {

    @GetMapping(value = {"/docs", "/docs/"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> renderSwaggerUi() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body("""
            <!doctype html>
            <html lang="en">
              <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                <title>MAPPO API Docs</title>
                <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/swagger-ui-dist@5/swagger-ui.css" />
                <style>
                  body { margin: 0; background: #0b1320; }
                  #swagger-ui { max-width: 1280px; margin: 0 auto; }
                </style>
              </head>
              <body>
                <div id="swagger-ui"></div>
                <script src="https://cdn.jsdelivr.net/npm/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
                <script>
                  window.ui = SwaggerUIBundle({
                    url: '/api/v1/openapi.json',
                    dom_id: '#swagger-ui',
                    deepLinking: true,
                    displayRequestDuration: true,
                    persistAuthorization: true,
                    docExpansion: 'list'
                  });
                </script>
              </body>
            </html>
            """);
    }
}
