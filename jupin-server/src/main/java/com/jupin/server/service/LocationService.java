package com.jupin.server.service;

import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LocationService {

    private final StringRedisTemplate stringRedis;
    private static final String GEO_KEY = "pool:locations";

    public LocationService(StringRedisTemplate stringRedis) {
        this.stringRedis = stringRedis;
    }

    public void save(Long poolId, double lng, double lat) {
        stringRedis.opsForGeo().add(GEO_KEY, new Point(lng, lat), String.valueOf(poolId));
    }

    public void remove(Long poolId) {
        stringRedis.opsForGeo().remove(GEO_KEY, String.valueOf(poolId));
    }

    public List<NearbyResult> searchNearby(double lng, double lat, double radiusKm) {
        Circle circle = new Circle(new Point(lng, lat), new Distance(radiusKm, Metrics.KILOMETERS));
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
                .newGeoRadiusArgs().includeDistance().sortAscending().limit(50);

        GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                stringRedis.opsForGeo().radius(GEO_KEY, circle, args);

        if (results == null) return List.of();

        return results.getContent().stream().map(r -> {
            String poolId = r.getContent().getName();
            double dist = r.getDistance().getValue();
            return new NearbyResult(Long.valueOf(poolId), Math.round(dist * 100.0) / 100.0);
        }).collect(Collectors.toList());
    }

    public record NearbyResult(Long poolId, Double distanceKm) {}
}
