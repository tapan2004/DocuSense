package com.docusense.backend.controller;

import com.docusense.backend.dto.SearchQueryRequest;
import com.docusense.backend.dto.ChatQueryRequest;
import com.docusense.backend.dto.SearchResponse;
import com.docusense.backend.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @PostMapping
    public ResponseEntity<SearchResponse> search(@RequestBody SearchQueryRequest request) {
        try {
            SearchResponse response = searchService.secureSearch(request.getQuery());
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<SearchResponse> chat(@RequestBody ChatQueryRequest request) {
        try {
            SearchResponse response = searchService.secureChat(request);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }
}