# AudioSource Project

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
1. **Clone the repositor to your local machine:**

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

3. **Create a file with your environment variables for AWS S3:**

```bash
AWS_ACCESS_KEY_ID=your-access-key-id
AWS_SECRET_ACCESS_KEY=your-secret-access-key
AWS_REGION=your-aws-region
S3_BUCKET_NAME=your-s3-bucket-name
```

4. **Dotenv configuration:**

The project includes an `AppConfig` class that configures Dotenv to load environment variables from the `.env` file.

## Demucs Setup

---
You will need to create a Python environment for being able to run Demucs from your project inside your local server. 
For this, you can use Anaconda/Miniconda.

1. **Check your system requirements :** https://docs.anaconda.com/miniconda/miniconda-system-requirements/
2. **Install Miniconda with the CLI depending on your OS:** https://docs.anaconda.com/miniconda/#quick-command-line-install
3. **Create your environment with a version of Python greater than 3.8 :** 
```bash
conda create -n demucs-env python>=3.8
```
4. **Make sure to activate your environment:**
```bash
conda activate demucs-env
```
5. **Install Demucs along with all required packages within your environment :**
```bash
conda install demucs
conda list // To verify installation of Demucs with packages
```

#####  For macOS users :
6. Torchaudio no longer supports decoding mp3s without ffmpeg installed.
**You must install ffmpeg**, either through Anaconda (```conda install ffmpeg -c conda-forge```) 
or with Homebrew for instance (```brew install ffmpeg```).

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
