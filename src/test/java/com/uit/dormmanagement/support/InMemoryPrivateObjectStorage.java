package com.uit.dormmanagement.support;

import com.uit.dormmanagement.exceptions.ObjectStorageNotFoundException;
import com.uit.dormmanagement.services.PrivateObjectStorage;
import com.uit.dormmanagement.services.StoredObjectContent;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;

public class InMemoryPrivateObjectStorage implements PrivateObjectStorage {

  private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();

  @Override
  public void putObject(String key, String contentType, InputStream content, long contentLength) {
    String validatedKey = PrivateObjectStorage.validateKey(key);
    if (content == null) {
      throw new IllegalArgumentException("Object content stream must not be null");
    }
    if (contentLength < 0) {
      throw new IllegalArgumentException("Object content length must be non-negative");
    }
    byte[] bytes;
    try {
      bytes = content.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read object content", e);
    }
    String effectiveContentType =
        (contentType != null && !contentType.isBlank())
            ? contentType.trim()
            : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    objects.put(validatedKey, new StoredObject(bytes, effectiveContentType));
  }

  @Override
  public StoredObjectContent getObject(String key) {
    String validatedKey = PrivateObjectStorage.validateKey(key);
    StoredObject stored = objects.get(validatedKey);
    if (stored == null) {
      throw new ObjectStorageNotFoundException("Stored object not found");
    }
    return new StoredObjectContent(
        new ByteArrayResource(stored.bytes()), stored.contentType(), stored.bytes().length);
  }

  @Override
  public void deleteObject(String key) {
    String validatedKey = PrivateObjectStorage.validateKey(key);
    objects.remove(validatedKey);
  }

  public boolean exists(String key) {
    String validatedKey = PrivateObjectStorage.validateKey(key);
    return objects.containsKey(validatedKey);
  }

  public void clear() {
    objects.clear();
  }

  public int size() {
    return objects.size();
  }

  private record StoredObject(byte[] bytes, String contentType) {}
}
