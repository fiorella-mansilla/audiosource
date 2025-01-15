# AudioSource 
AudioSource is a full-stack application created to separate user-provided audio files into individual stems, including vocals, drums, bass, and accompaniment, all while maintaining high audio quality.

Powered by [Demucs](https://github.com/facebookresearch/demucs), an AI model developed and trained by Facebook Research, our web application achieves state-of-the-art results in music source separation.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Backend Setup](#backend-setup)
  - [AWS S3 Credentials](#aws-s3-credentials)
  - [MongoDB Configuration](#mongodb-configuration)
  - [RabbitMQ Configuration](#rabbitmq-configuration)
  - [SMTP Server Configuration](#smtp-email-server-settings)
- [Demucs Setup](#demucs-setup)
  - [Create a Python Environment](#create-a-python-environment)
  - [Activate Your Environment](#activate-your-environment)
  - [Install Demucs and Required Packages](#install-demucs-and-required-packages)
  - [Install FFmpeg (macOS Only)](#install-ffmpeg-macos-only)
- [Frontend Setup](#frontend-setup)
  - [Install Dependencies](#install-dependencies)
  - [Configure API and AWS Credentials](#configure-api-and-aws-credentials)
- [Running the Application](#running-the-application)
  - [Running the Backend](#running-the-backend)
  - [Running the Frontend](#running-the-frontend)
  
## Prerequisites

Ensure you have the following installed in your local machine:

- Java 17 or higher
- Maven 3.6.3 or higher
- Node.js 16 or higher
- npm 7 or higher
- AWS Account

## Backend Setup

1. Clone the repository to your local machine:

  ```bash
  git clone https://github.com/fiorella-mansilla/audiosource.git
  cd audiosource
  ```

2. Configure your Environment Variables in an `.env` file.

3. Build the project using **Maven** and install the dependencies:

```bash
cd backend
mvn clean install
```

### Configuring Environment Variables
---

To connect to various services in the backend, create a `.env` file in the backend directory and add the following environment variables:

### AWS S3 Credentials
---
These values are necessary to interact with your AWS S3 bucket :

```bash
AWS_ACCESS_KEY_ID=your-access-key-id
AWS_SECRET_ACCESS_KEY=your-secret-access-key
AWS_REGION=your-aws-region
S3_BUCKET=your-s3-bucket-name
```

### MongoDB Configuration
---
Provide **MongoDB Atlas** credentials for the backend to connect to your database:

```bash
MONGODB_URI=your-mongodb-uri
MONGODB_DATABASE=your-database-name
```

### RabbitMQ Configuration
---
Define the **RabbitMQ** connection settings and credentials:

```bash
RABBITMQ_HOST=your-rabbitmq-host
RABBITMQ_PORT=your-rabbitmq-port
RABBITMQ_USERNAME=your-username
RABBITMQ_PASSWORD=your-password
```

### SMTP (Email) Server Settings
---
Configure the SMTP settings for sending emails via **Gmail’s SMTP server**:

```bash
GMAIL_USERNAME=your-gmail-username
GMAIL_PASSWORD=your-gmail-password
```


## Demucs Setup

To use the Demucs model locally, follow these steps to set up a compatible Python environment and configure any necessary credentials. We recommend using Anaconda or Miniconda for managing the environment efficiently.


### Create a Python Environment
---

Create a new Python environment with **Python version 3.8 or higher**, which is compatible with Demucs:

```bash
conda create -n demucs-env python>=3.8
```

### Activate your Environment
---

Activate the newly created environment to begin installing and running Demucs:

```bash
conda activate demucs-env
```

### Install Demucs and Required Packages
---

With the environment active, install Demucs and any additional packages required:

```bash
conda install demucs
```

To verify that Demucs and dependencies were installed successfully:

```bash
conda list
```

### Configure Demucs Service Credentials
---

Add the following lines to your `.env` file in the backend directory and ensure these paths align with the locations where Demucs will access files and your Python environment:

```bash
DEMUCS_INPUT_DIRECTORY=path/to/input-directory
DEMUCS_OUTPUT_DIRECTORY=path/to/output-directory
PYTHON_ENV_PATH=path/to/python-environment
```

### Install FFmpeg (macOS Only)
---

For macOS users, Torchaudio requires FFmpeg to decode MP3 files. Install **FFmpeg** through either Anaconda or Homebrew:


- Using Anaconda:
```bash
conda install ffmpeg -c conda-forge
```

- Using Homebrew:
```bash
brew install ffmpeg
```

## Frontend Setup

1. Navigate to the frontend directory:

```bash
cd ../frontend
```

### Install Dependencies
---

2. Ensure to install the dependencies for the frontend:

```bash
npm install
```

### Configure API and AWS Credentials
---

3. Create a `config.js` file that matches the address where your backend is running and include the correct AWS credentials. Update `config.js` as follows and ensure to add this path to your `gitignore` file :

```bash
const config = {
    API_BASE_URL: "http://localhost:8080", // Update with your backend URL if different
    AWS: {
        Region: "your-aws-region", // e.g., "us-west-1"
        AccessKey: "your-access-key-id",
        SecretAccessKey: "your-secret-access-key",
        BucketName: "your-s3-bucket-name"
    }
};

export default config;
```

## Running the Application

To run the application, you need to start both the backend and frontend services. Follow the steps below to do so:

### Running the Backend
---

1. Navigate to the Backend directory:

```bash
cd ../backend
```

2. Start the Backend Application

Use the following command to run the backend application. This typically starts your Spring Boot application:

```bash
./mvnw spring-boot:run
```

### Running the Frontend
---

1. Navigate to the Frontend directory:

```bash
cd ../frontend
```
2. Start the Frontend Application

Use the following command to start the frontend application, which will run a development server:

```bash
npm start
```

Ensure that both services are up and running to enable proper communication between them. The frontend will interact with the backend via the API specified in the configuration.
