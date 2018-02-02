/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.geocoder.netex.pbf;


import org.junit.Assert;
import org.junit.Test;
import org.rutebanken.netex.model.IanaCountryTldEnumeration;
import org.rutebanken.netex.model.TopographicPlace;
import org.rutebanken.netex.model.TopographicPlaceTypeEnumeration;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

public class PbfTopographicPlaceReaderTest {

	@Test
	public void testParsePbfSampleFile() throws Exception {
		PbfTopographicPlaceReader reader =
				new PbfTopographicPlaceReader(Arrays.asList("leisure=common", "naptan:indicator"), IanaCountryTldEnumeration.NO,
						                             new File("src/test/resources/no/rutebanken/marduk/geocoder/pbf/sample.pbf"));

		BlockingQueue<TopographicPlace> queue = new LinkedBlockingDeque<>();
		reader.addToQueue(queue);

		Assert.assertEquals(4, queue.size());

		for (TopographicPlace tp : queue) {
			Assert.assertEquals(IanaCountryTldEnumeration.NO, tp.getCountryRef().getRef());
			Assert.assertEquals(TopographicPlaceTypeEnumeration.PLACE_OF_INTEREST, tp.getTopographicPlaceType());
			Assert.assertNotNull(tp.getName());
			Assert.assertNotNull(tp.getCentroid());
		}

	}
}
