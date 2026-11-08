package com.uit.dormmanagement.services;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

  /**
   * Upload a file to storage under the specified folder.
   *
   * @param file the multipart file to upload
   * @param folder directory or category folder (e.g. "contracts", "vehicles")
   * @return accessible URL or public path of the uploaded file
   */
  String uploadFile(MultipartFile file, String folder);

  /**
   * Delete a file from storage given its URL or relative path.
   *
   * @param fileUrl URL or relative path of the file to delete
   */
  void deleteFile(String fileUrl);
}
