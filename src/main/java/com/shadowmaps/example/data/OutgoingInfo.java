package com.shadowmaps.example.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import org.codehaus.jackson.annotate.JsonProperty;

import java.util.List;

/**
 * Created by Danny Iland on 2/17/15.
 */

@JsonInclude(Include.NON_EMPTY)
public class OutgoingInfo {
    @JsonProperty()
    DeviceInfo dev;
    @JsonProperty()
    List<Satellite> sats;
    @JsonProperty()
    SensorData sensors;
    @JsonProperty()
    List<WiFi> wifi;
    @JsonProperty()
    List<Cell> cells;


    public OutgoingInfo() {}

    public OutgoingInfo(DeviceInfo dev, List<Satellite> sats, SensorData sensors, List<WiFi> wifi, List<Cell> cells) {
        this.dev = dev;
        this.sats = sats;
        this.sensors = sensors;
        this.wifi = wifi;
        this.cells = cells;

    }

    public void setDev(DeviceInfo dev) {
        this.dev = dev;
    }

    public void setSats(List<Satellite> sats) {
        this.sats = sats;
    }

    public void setSensors(SensorData sensors) {
        this.sensors = sensors;
    }

    public void setWifi(List<WiFi> wifi) {
        this.wifi = wifi;
    }

    public void setCells(List<Cell> cells) {
        this.cells = cells;
    }

    @JsonProperty()
    public SensorData getSensors() { return sensors; }

    @JsonProperty()
    public List<WiFi> getWifi() {
        return wifi;
    }

    @JsonProperty()
    public List<Cell> getCells() {
        return cells;
    }

    @JsonProperty()
    public DeviceInfo getDev() {
        return dev;
    }

    @JsonProperty()
    public List<Satellite> getSats() {
        return sats;
    }

}
