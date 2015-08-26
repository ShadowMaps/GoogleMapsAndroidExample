package com.shadowmaps.example.data;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Created by Danny Iland on 2/17/15.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceInfo {
    @JsonProperty()
    public long utc;
    @JsonProperty()
    public double lat;
    @JsonProperty()
    public double lon;
    public double alt;
    @JsonProperty()
    public double acc;
    public double spd;
    public double brng;
    public String provider;
    public String id;
    public String model;
    public String key;

    public DeviceInfo(long utc, double lat, double lon, double alt, double acc, float spd, float brng, String provider, String id, String model, String key) {
        this.utc = utc;
        this.lat = lat;
        this.lon = lon;
        this.alt = alt;
        this.acc = acc;
        this.spd = spd;
        this.brng = brng;
        this.provider = provider;
        this.id = id;
        this.model = model;
        this.key = key;
    }

    public DeviceInfo() {}

    public long getUtc() {
        return utc;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public double getAlt() {
        return alt;
    }

    public double getAcc() {
        return acc;
    }

    public double getSpd() {
        return spd;
    }

    public double getBrng() {
        return brng;
    }

    public String getProvider() {
        return provider;
    }

    public String getId() { return id; }
    public String getModel() { return model; }
    public String getKey() { return key; }

    public void setUtc(long utc) {
        this.utc = utc;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

    public void setAlt(double alt) {
        this.alt = alt;
    }

    public void setAcc(double acc) {
        this.acc = acc;
    }

    public void setSpd(double spd) {
        this.spd = spd;
    }

    public void setBrng(double brng) {
        this.brng = brng;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
