package com.rideshare.location;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/places")
@Tag(name = "Places", description = "Common pickup/drop points (no paid map API needed)")
public class PlaceController {

    private final PlacesProperties placesProperties;

    public PlaceController(PlacesProperties placesProperties) {
        this.placesProperties = placesProperties;
    }

    @GetMapping
    @Operation(summary = "List preset places with approximate coordinates")
    public List<PlacesProperties.Place> list() {
        return placesProperties.places();
    }
}
