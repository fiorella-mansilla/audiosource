import { useState } from 'react';
import { getSignedUrl, uploadFileToS3SignedUrl } from '../services/api';

export const useUpload = () => {
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploadError, setUploadError] = useState(null);
  const [uploadSuccess, setUploadSuccess] = useState(null);

  const uploadFile = async (file) => {
    const content_type = file.type;
    const key = `originals/${file.name}`;

    try {
      const signedUrl = await getSignedUrl({ key, content_type });

      await uploadFileToS3SignedUrl(
        key,
        signedUrl,
        file,
        content_type,
        (progressEvent) => {
          const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total);
          setUploadProgress(percentCompleted);
        },
        (result) => {
          // Handle successful upload completion
          if (result.status === "File uploaded successfully") {
            setUploadProgress(100); // Ensure progress is set to 100%
            setUploadSuccess('File uploaded successfully!');
          }
        }
      );
    } catch (error) {
      setUploadError("Failed to upload file. Please try again.");
      console.error("Error uploading file:", error);
    }
  };

  const resetUploadState = () => {
    setUploadProgress(0);
    setUploadError(null);
    setUploadSuccess(null);
  };

  return { uploadFile, uploadProgress, uploadError, uploadSuccess, resetUploadState };
};