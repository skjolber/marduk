package no.rutebanken.marduk.repository;

import no.rutebanken.marduk.domain.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.List;

@Repository
public class RestProviderRepository implements ProviderRepository {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${nabu.rest.service.url}")
    private String restServiceUrl;

    @Override
    public Collection<Provider> getProviders() {
        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<List<Provider>> rateResponse =
                restTemplate.exchange(restServiceUrl + "/providers/all",
                        HttpMethod.GET, null, new ParameterizedTypeReference<List<Provider>>() {
                        });
        return rateResponse.getBody();
    }

    @Override
    public Provider getProvider(Long id) {
        RestTemplate restTemplate = new RestTemplate();
        return restTemplate.getForObject(restServiceUrl + "/providers/" + id, Provider.class);
    }

    @Override
    public boolean isConnected(){
        RestTemplate restTemplate = new RestTemplate();
        logger.debug("Checking status");
        ResponseEntity response = restTemplate.getForEntity(restServiceUrl + "/appstatus/up", Object.class, new Object[0]);
        logger.debug("Got response: " + response);
        logger.debug("Response status:" + response.getStatusCode().toString() );
        if ("200".equals(response.getStatusCode().toString())){
            logger.debug("Response ok");
            return true;
        }
        logger.debug("Response not ok");
        return false;
    }

}
