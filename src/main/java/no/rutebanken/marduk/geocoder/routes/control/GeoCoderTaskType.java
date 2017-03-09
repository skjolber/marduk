package no.rutebanken.marduk.geocoder.routes.control;

import static no.rutebanken.marduk.geocoder.GeoCoderConstants.*;

public enum GeoCoderTaskType {
	ADDRESS_DOWNLOAD(KARTVERKET_ADDRESS_DOWNLOAD), ADMINISTRATIVE_UNITS_DOWNLOAD(KARTVERKET_ADMINISTRATIVE_UNITS_DOWNLOAD),
	PLACE_NAMES_DOWNLOAD(KARTVERKET_PLACE_NAMES_DOWNLOAD), TIAMAT_POI_UPDATE(TIAMAT_PLACES_OF_INTEREST_UPDATE_START),
	TIAMAT_ADMINISTRATIVE_UNITS_UPDATE(TIAMAT_ADMINISTRATIVE_UNITS_UPDATE_START), TIAMAT_EXPORT(TIAMAT_EXPORT_START),
	PELIAS_UPDATE(PELIAS_UPDATE_START);

	GeoCoderTaskType(GeoCoderTask geoCoderTask) {
		this.geoCoderTask = geoCoderTask;
	}

	private GeoCoderTask geoCoderTask;

	public GeoCoderTask getGeoCoderTask() {
		return geoCoderTask;
	}
}