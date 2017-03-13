package no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex;


import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import org.apache.commons.collections.CollectionUtils;
import org.rutebanken.netex.model.StopPlace;

import java.util.Arrays;

public class StopPlaceToPeliasMapper extends AbstractNetexPlaceToPeliasDocumentMapper<StopPlace> {

	// Using substitute layer for stops to avoid having to fork pelias (custom layers not configurable).
	public static final String STOP_PLACE_LAYER = "venue";

	private long defaultPopularity;

	public StopPlaceToPeliasMapper(String participantRef, long defaultPopularity) {
		super(participantRef);
		this.defaultPopularity = defaultPopularity;
	}

	@Override
	protected void populateDocument(StopPlace place, PeliasDocument document) {
		if (place.getStopPlaceType() != null) {
			document.setCategory(Arrays.asList(place.getStopPlaceType().value()));
		}

		if (place.getAlternativeNames() != null && !CollectionUtils.isEmpty(place.getAlternativeNames().getAlternativeName())) {
			place.getAlternativeNames().getAlternativeName().stream().filter(an -> an.getName() != null && an.getName().getLang() != null).forEach(n -> document.addName(n.getName().getLang(), n.getName().getValue()));
		}

		// Make stop place rank highest in autocomplete by setting popularity
		document.setPopularity(defaultPopularity);
	}

	@Override
	protected String getLayer(StopPlace place) {
		return STOP_PLACE_LAYER;
	}
}
