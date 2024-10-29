# AudioSource 

## Prerequisites

-----
Ensure you have the following installed:

- Java 17 or higher
- Maven 3.6.3 or higher
- Node.js 16 or higher
- npm 7 or higher
- AWS account with S3 bucket setup

## Backend Setup

-----
1. **Clone the repository to your local machine:**

```bash
  git clone https://github.com/fiorella-mansilla/audiosource.git
```

2. **Navigate to the backend directory:**

```bash
cd backend
```

3. **Build the project using Maven:**

```bash
mvn clean install
```

4. **Configure AWS S3 Credentials:**

Create a `.env` file in the backend directory with your AWS credentials:

```bash
AWS_ACCESS_KEY_ID=your-access-key-id
AWS_SECRET_ACCESS_KEY=your-secret-access-key
AWS_REGION=your-aws-region
S3_BUCKET=your-s3-bucket-name
```

The `AppConfig` class in the project uses Dotenv to load these environment variables.

## Demucs Setup

---
You need a Python environment to run Demucs locally. We recommend using Anaconda or Miniconda.

1. **Verify System Requirements**
Check the system requirements for Anaconda/Miniconda : 
[Miniconda System Requirements](https://docs.anaconda.com/miniconda/miniconda-system-requirements/)

2. **Install Miniconda**
Install Miniconda for your operating system using the command-line instructions:
[Miniconda Installation](https://docs.anaconda.com/miniconda/#quick-command-line-install)

3. **Create a Python Environment :**
Create a new Python environment with a version of Python greater than 3.8:
```bash
conda create -n demucs-env python>=3.8
```
4. **Activate your Environment:**
```bash
conda activate demucs-env
```
5. **Install Demucs and Required Packages :**
Install Demucs within your environment:
```bash
conda install demucs
```
Verify the installation:
```bash
conda list
```
6. **Install FFmpeg (macOS Only)**

For macOS users, Torchaudio no longer supports decoding mp3s without ffmpeg installed.
Install ffmpeg using either Anaconda or Homebrew:

- Using Anaconda:
```bash
conda install ffmpeg -c conda-forge
```

- Using Homebrew:
```bash
brew install ffmpeg
```

## Frontend Setup

-----
1. **Navigate to the frontend directory:**

```bash
cd ../frontend
```

2. **Install the dependencies:**

```bash
npm install
```

3. **Make sure that the `API_BASE_URL` from the config file matches the address where your backend is running:**

```javascript
const config = {
  API_BASE_URL: "http://localhost:8080",
};

export default config;
```
