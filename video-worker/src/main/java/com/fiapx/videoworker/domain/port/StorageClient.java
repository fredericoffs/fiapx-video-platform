package com.fiapx.videoworker.domain.port;

import java.io.InputStream;

public interface StorageClient {

	void upload(String bucket, String key, InputStream data, long contentLength, String contentType);

	InputStream download(String bucket, String key);

	/** Usado para detectar reentrega: um zip já existente na chave de saída é um vídeo já processado. */
	boolean exists(String bucket, String key);
}
