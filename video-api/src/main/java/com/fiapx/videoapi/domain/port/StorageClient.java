package com.fiapx.videoapi.domain.port;

import java.io.InputStream;

public interface StorageClient {

	void upload(String bucket, String key, InputStream data, long contentLength, String contentType);

	InputStream download(String bucket, String key);

	void delete(String bucket, String key);
}
