package practicum.mapper;

import practicum.model.Location;
import practicum.model.dto.location.LocationDto;

public final class LocationMapper {

    private LocationMapper() {
    }

    public static Location toLocation(LocationDto locationDto) {
        return Location.builder()
                .id(locationDto.getId())
                .lat(locationDto.getLat())
                .lon(locationDto.getLon())
                .build();
    }

    public static LocationDto toLocationDto(Location location) {
        return LocationDto.builder()
                .lat(location.getLat())
                .lon(location.getLon())
                .build();
    }
}