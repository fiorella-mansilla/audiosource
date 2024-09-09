import { useState } from 'react';
import { getSignedUrl, uploadFileToS3SignedUrl, notifyClientUpload } from '../services/api';

export const useUpload = () => {
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploadError, setUploadError] = useState(null);
  const [uploadSuccess, setUploadSuccess] = useState(null);

  const uploadFile = async (file, userEmail, outputFormat, separationType) => {
    const content_type = file.type;
    const key = `originals/${file.name}`;

    try {
      const signedUrl = await getSignedUrl({ key, content_type });

      // Track progress and handle completion in separate callbacks
      await uploadFileToS3SignedUrl(
        key,
        signedUrl,
        file,
        content_type,
        (progressEvent) => {
          const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total);
          setUploadProgress(percentCompleted);
        }
      );

      // After upload completes, notify the backend
      setUploadProgress(100); // Ensure progress is set to 100%
      setUploadSuccess('File uploaded successfully!');

      try {
        await notifyClientUpload({
          keyName: key,
          fileSize: file.size,
          separationType,
          outputFormat,
          userEmail,
        }, {
          headers: {
            'Content-Type': 'application/json',
          },
        });
        console.log("Backend notified successfully");
      } catch (error) {
        console.error("Error notifying the backend about the successful upload:", error);
        setUploadError("Failed to notify the backend about the successful upload.");
      }
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