import axios from 'axios';
import config from '../config';

const apiClient = axios.create({
    baseURL: config.API_BASE_URL,
});

// Makes the call to use the signed URL using the key and the content type
export async function getSignedUrl({ key, content_type }) {
    try {
      const response = await apiClient.post("/s3/upload/signed_url", {
        key,
        content_type,
      });
      return response.data;
    } catch (error) {
      console.error("Error getting signed URL:", error);
      throw error; // Rethrow the error for handling in the component
    }
  }

  export async function uploadFileToSignedUrl(
    signedUrl,
    file,
    contentType,
    onProgress,
    onComplete
  ) {
    try {
      const response = await axios.put(signedUrl, file, {
        onUploadProgress: onProgress,
        headers: {
          "Content-Type": contentType,
        },
      });
      onComplete(response);
    } catch (error) {
      console.error("Error uploading file to signed URL:", error);
      throw error; // Rethrow the error for handling in the component
    }
  }