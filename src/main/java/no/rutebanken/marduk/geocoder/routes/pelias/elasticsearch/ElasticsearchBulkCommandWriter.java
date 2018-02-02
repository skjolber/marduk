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

package no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch;


import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;


public class ElasticsearchBulkCommandWriter {

	private Writer writer;
	private ObjectMapper mapper;

	public ElasticsearchBulkCommandWriter(Writer writer) {
		this.writer = writer;
		mapper = new ObjectMapper();
		mapper.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

	}

	public void write(ElasticsearchCommand elasticsearchCommand) throws IOException {
		mapper.writeValue(writer, elasticsearchCommand);
		writer.append("\n");

		if (elasticsearchCommand.getSource() != null) {
			mapper.writeValue(writer, elasticsearchCommand.getSource());
			writer.append("\n");
		}
	}

	public void write(Collection<ElasticsearchCommand> elasticsearchCommands) throws IOException {
		for (ElasticsearchCommand elasticsearchCommand : elasticsearchCommands) {
			write(elasticsearchCommand);
		}

	}


}
