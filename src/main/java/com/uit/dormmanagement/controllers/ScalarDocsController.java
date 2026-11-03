package com.uit.dormmanagement.controllers;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Hidden
@Controller
@ConditionalOnProperty(name = "scalar.enabled", havingValue = "true", matchIfMissing = true)
public class ScalarDocsController {

  private final String openApiUrl;
  private final String pageTitle;
  private final String theme;
  private final String layout;
  private final boolean showSidebar;
  private final boolean persistAuth;
  private final String searchHotKey;

  @Autowired
  public ScalarDocsController(
      @Value("${springdoc.api-docs.path:/v3/api-docs}") String openApiUrl,
      @Value("${scalar.page-title:KTX Hub API Reference | Private Dormitory Management System}")
          String pageTitle,
      @Value("${scalar.theme:purple}") String theme,
      @Value("${scalar.layout:modern}") String layout,
      @Value("${scalar.show-sidebar:true}") boolean showSidebar,
      @Value("${scalar.persist-auth:true}") boolean persistAuth,
      @Value("${scalar.search-hot-key:k}") String searchHotKey) {
    this.openApiUrl = openApiUrl;
    this.pageTitle = pageTitle;
    this.theme = theme;
    this.layout = layout;
    this.showSidebar = showSidebar;
    this.persistAuth = persistAuth;
    this.searchHotKey = searchHotKey;
  }

  public ScalarDocsController(String openApiUrl) {
    this(
        openApiUrl,
        "KTX Hub API Reference | Private Dormitory Management System",
        "purple",
        "modern",
        true,
        true,
        "k");
  }

  @GetMapping(
      value = {"${scalar.path:/scalar}", "${scalar.path:/scalar}/", "/docs", "/docs/"},
      produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  public ResponseEntity<String> renderScalarDocs() {
    String html =
        """
        <!doctype html>
        <html lang="vi">
          <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1" />
            <title>%s</title>
            <link rel="icon" type="image/svg+xml" href="https://scalar.com/favicon.svg" />
            <style>
              body {
                margin: 0;
                padding: 0;
              }
            </style>
          </head>
          <body>
            <div id="app"></div>
            <script type="module">
              import { createApiReference } from 'https://cdn.jsdelivr.net/npm/@scalar/api-reference/esm.js';

              createApiReference('#app', {
                url: '%s',
                theme: '%s',
                layout: '%s',
                showSidebar: %b,
                persistAuth: %b,
                searchHotKey: '%s',
                defaultOpenAllTags: true,
                orderRequiredPropertiesFirst: true
              });
            </script>
          </body>
        </html>
        """
            .formatted(
                pageTitle, openApiUrl, theme, layout, showSidebar, persistAuth, searchHotKey);

    return ResponseEntity.ok(html);
  }
}
