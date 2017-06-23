package no.rutebanken.marduk.services;

import com.google.cloud.storage.Storage;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.repository.BlobStoreRepository;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.InputStream;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;

@Service
public class BlobStoreService {

	@Autowired
	BlobStoreRepository repository;

	@Autowired
	Storage storage;

	@Value("${blobstore.gcs.container.name}")
	String containerName;

	@PostConstruct
	public void init() {
		repository.setStorage(storage);
		repository.setContainerName(containerName);
	}

	public BlobStoreFiles listBlobsInFolder(@Header(value = Exchange.FILE_PARENT) String folder, Exchange exchange) {
		ExchangeUtils.addHeadersAndAttachments(exchange);
		return repository.listBlobs(folder + "/");
	}


	public BlobStoreFiles listBlobs(@Header(value = Constants.CHOUETTE_REFERENTIAL) String referential, Exchange exchange) {
		ExchangeUtils.addHeadersAndAttachments(exchange);
		return repository.listBlobs(Constants.BLOBSTORE_PATH_INBOUND + referential + "/");
	}

	public BlobStoreFiles listBlobsFlat(@Header(value = Constants.CHOUETTE_REFERENTIAL) String referential, Exchange exchange) {
		ExchangeUtils.addHeadersAndAttachments(exchange);
		return repository.listBlobsFlat(Constants.BLOBSTORE_PATH_INBOUND + referential + "/");
	}

	public InputStream getBlob(@Header(value = Constants.FILE_HANDLE) String name, Exchange exchange) {
		ExchangeUtils.addHeadersAndAttachments(exchange);
		return repository.getBlob(name);
	}

	public void uploadBlob(@Header(value = Constants.FILE_HANDLE) String name,
			                      @Header(value = Constants.BLOBSTORE_MAKE_BLOB_PUBLIC) boolean makePublic, InputStream inputStream, Exchange exchange) {
		ExchangeUtils.addHeadersAndAttachments(exchange);
		repository.uploadBlob(name, inputStream, makePublic);
	}

	public boolean deleteBlob(@Header(value = FILE_HANDLE) String name, Exchange exchange) {
		ExchangeUtils.addHeadersAndAttachments(exchange);
		return repository.delete(name);
	}

	public boolean deleteAllBlobsInFolder(String folder, Exchange exchange) {
		ExchangeUtils.addHeadersAndAttachments(exchange);
		return repository.deleteAllFilesInFolder(folder);
	}

	public void uploadBlob(String name, boolean makePublic, InputStream inputStream) {
		repository.uploadBlob(name, inputStream, makePublic);
	}

}
