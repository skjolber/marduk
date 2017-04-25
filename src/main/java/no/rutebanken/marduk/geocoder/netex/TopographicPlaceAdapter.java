package no.rutebanken.marduk.geocoder.netex;

import com.vividsolutions.jts.geom.Geometry;

import java.util.Map;

public interface TopographicPlaceAdapter {
    enum Type {COUNTRY, COUNTY, LOCALITY, BOROUGH, NEIGHBOURHOOD}

    String getId();

    String getIsoCode();

    String getParentId();

    String getName();

    TopographicPlaceAdapter.Type getType();

    Geometry getDefaultGeometry();

    /**
     * Returns map of languages as keys and corresponding name as value.
     */
    Map<String,String> getAlternativeNames();
}
