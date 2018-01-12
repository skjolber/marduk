package no.rutebanken.marduk.geocoder.routes.maplayer.mapper;

import no.rutebanken.marduk.exceptions.FileValidationException;
import no.rutebanken.marduk.netex.PublicationDeliveryHelper;
import org.rutebanken.netex.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static javax.xml.bind.JAXBContext.newInstance;

@Service
public class DeliveryPublicationStreamToMapLayerData {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryPublicationStreamToMapLayerData.class);

    public DeliveryPublicationStreamToMapLayerData() {

    }

    public Collection<String> transform(InputStream publicationDeliveryStream) {
        logger.info("Transform {}", publicationDeliveryStream);
        try {
            PublicationDeliveryStructure deliveryStructure = PublicationDeliveryHelper.unmarshall(publicationDeliveryStream);
            fromDeliveryPublicationStructure(deliveryStructure);
        } catch (Exception e) {
            throw new FileValidationException("Parsing of DeliveryPublications failed: " + e.getMessage(), e);
        }
        return new ArrayList<>();
    }

    private void fromDeliveryPublicationStructure(PublicationDeliveryStructure publicationDeliveryStructure) {
        List<StopPlace> stops = PublicationDeliveryHelper.resolveStops(publicationDeliveryStructure)
                .peek(stop -> logger.info("{}", stop))
                .collect(Collectors.toList());
    }


}
