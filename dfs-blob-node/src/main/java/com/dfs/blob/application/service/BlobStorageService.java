package com.dfs.blob.application.service;

import com.dfs.blob.application.port.in.BlobStorageUseCase;
import com.dfs.blob.application.port.out.BlobStorePort;
import com.dfs.blob.domain.model.BlobDigest;
import com.dfs.blob.domain.model.StoredBlob;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Optional;

@Service
public class BlobStorageService implements BlobStorageUseCase {

    private final BlobStorePort blobStore;

    public BlobStorageService(BlobStorePort blobStore) {
        this.blobStore = blobStore;
    }

    @Override
    public StoredBlob store(BlobDigest digest, InputStream content) {
        return blobStore.write(digest, content);
    }

    @Override
    public Optional<StoredBlob> stat(BlobDigest digest) {
        return blobStore.stat(digest);
    }

    @Override
    public InputStream open(BlobDigest digest) {
        return blobStore.openStream(digest);
    }
}
