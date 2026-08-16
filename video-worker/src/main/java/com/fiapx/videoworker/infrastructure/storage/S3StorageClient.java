package com.fiapx.videoworker.infrastructure.storage;

import java.io.InputStream;

import org.springframework.stereotype.Component;

import com.fiapx.videoworker.domain.port.StorageClient;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class S3StorageClient implements StorageClient {

	private final S3Client s3Client;

	public S3StorageClient(S3Client s3Client) {
		this.s3Client = s3Client;
	}

	@Override
	public void upload(String bucket, String key, InputStream data, long contentLength, String contentType) {
		PutObjectRequest request = PutObjectRequest.builder()
				.bucket(bucket)
				.key(key)
				.contentType(contentType)
				.build();
		s3Client.putObject(request, RequestBody.fromInputStream(data, contentLength));
	}

	@Override
	public InputStream download(String bucket, String key) {
		GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key).build();
		return s3Client.getObject(request);
	}
}
