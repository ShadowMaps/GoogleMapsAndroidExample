package com.shadowmaps.example.data;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Created by Danny Iland on 2/23/15.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SensorData {
    @JsonProperty()
    public float pressure;
    @JsonProperty()
    public float lux;
    @JsonProperty()
    public float temp;

    public SensorData() {
    }

    public SensorData(float pressure, float lux, float temp) {
        this.pressure = pressure;
        this.lux = lux;
        this.temp = temp;
    }

    @JsonProperty()
    public float getPressure() {

        return pressure;
    }

    public void setPressure(float pressure) {
        this.pressure = pressure;
    }

    @JsonProperty()
    public float getLux() {
        return lux;
    }

    public void setLux(float lux) {
        this.lux = lux;
    }

    @JsonProperty()
    public float getTemp() {
        return temp;
    }

    public void setTemp(float temp) {
        this.temp = temp;
    }
}
