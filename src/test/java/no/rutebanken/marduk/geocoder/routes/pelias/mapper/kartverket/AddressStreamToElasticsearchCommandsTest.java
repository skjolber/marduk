package no.rutebanken.marduk.geocoder.routes.pelias.mapper.kartverket;


import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.rutebanken.marduk.geocoder.routes.pelias.json.AddressParts;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import no.rutebanken.marduk.geocoder.routes.pelias.mapper.GeometryTransformer;
import no.rutebanken.marduk.geocoder.routes.pelias.mapper.kartverket.AddressStreamToElasticSearchCommands;
import org.junit.Assert;
import org.junit.Test;

import java.io.FileInputStream;
import java.util.Collection;
import java.util.stream.Collectors;

public class AddressStreamToElasticsearchCommandsTest {


	@Test
	public void testStreamAddressesToIndexCommands() throws Exception {
		AddressStreamToElasticSearchCommands transformer = new AddressStreamToElasticSearchCommands();

		Collection<ElasticsearchCommand> commands = transformer
				                                            .transform(new FileInputStream("src/test/resources/no/rutebanken/marduk/geocoder/csv/addresses.csv"));


		Assert.assertEquals(28, commands.size());

		commands.forEach(c -> assertCommand(c));

		PeliasDocument knownDocument = commands.stream().map(c -> (PeliasDocument) c.getSource()).filter(d -> d.getSourceId().endsWith("87033123")).collect(Collectors.toList()).get(0);
		assertKnownAddress(knownDocument);
	}


	// 87033123;Vegadresse;0125;1850;Bergheimveien;Bergheimveien;14;A;57;14;0;0;;;;;23;6607447.1;293546.2;508;KIRKÅS/ENGA;02030103;Mysen;121;Mysen;1;EIDSBERG;1850;MYSEN;
	private void assertKnownAddress(PeliasDocument known) throws Exception {

		AddressParts addressParts = known.getAddressParts();

		Assert.assertEquals("Bergheimveien", addressParts.getStreet());
		Assert.assertEquals("14", addressParts.getNumber());
		Assert.assertEquals("1850", addressParts.getZip());

		Point utm33Point = new GeometryFactory().createPoint(new Coordinate(293546.2, 6607447.1));
		Point wgs84Point = GeometryTransformer.fromUTM(utm33Point, "33");


		Assert.assertEquals(wgs84Point.getX(), known.getCenterPoint().getLat(), 0.0001);
		Assert.assertEquals(wgs84Point.getY(), known.getCenterPoint().getLon(), 0.0001);
	}

	private void assertCommand(ElasticsearchCommand command) {
		Assert.assertNotNull(command.getIndex());
		Assert.assertEquals("pelias", command.getIndex().getIndex());
		Assert.assertEquals("ADDRESS", command.getIndex().getType());
	}
}