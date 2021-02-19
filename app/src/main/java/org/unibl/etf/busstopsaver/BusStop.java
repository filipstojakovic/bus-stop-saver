package org.unibl.etf.busstopsaver;

import com.google.android.gms.maps.model.LatLng;

import java.io.Serializable;

public class BusStop implements Serializable
{
    private String name;
    private double lat;
    private double lng;

    public BusStop()
    {
    }

    public BusStop(String name, LatLng location)
    {
        this.name = name;
        lat = location.latitude;
        lng = location.longitude;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public double getLat()
    {
        return lat;
    }

    public void setLat(double lat)
    {
        this.lat = lat;
    }

    public double getLng()
    {
        return lng;
    }

    public void setLng(double lng)
    {
        this.lng = lng;
    }

    @Override
    public String toString()
    {
        return "name= " + name
                + ", location= "
                + lat + "," + lng;
    }
}
