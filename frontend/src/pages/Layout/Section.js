import React, { useState } from 'react';
import { Container, Row, Col, Form, FormGroup, Label, Input, Button, Alert, Progress } from 'reactstrap';
import { useUpload } from '../../hooks/useUpload';
import Background from "../../assets/images/hero-4-bg-img.png";
import validateForm from '../../middleware/validateForm.js';

const Section = () => {
  const { uploadFile, uploadProgress, uploadError, uploadSuccess, resetUploadState } = useUpload();
  const [file, setFile] = useState(null);
  const [separationType, setSeparationType] = useState("STEMS_SPLITTER"); // Set initial default value as Stems Splitter
  const [userEmail, setUserEmail] = useState(""); 
  const [outputFormat, setOutputFormat] = useState("WAV"); // Set initial default value as WAV
  
  const handleSubmit = async (e) => {
    e.preventDefault();

    const errorMessage = validateForm(file, separationType, userEmail, outputFormat);
    if (errorMessage) {
      alert(errorMessage);
      return;
    }

    // Reset status before starting a new upload
    resetUploadState();

    try {
      await uploadFile(file, userEmail, outputFormat, separationType);
    } catch (error) {
      console.error('Error uploading file', error);
    }
  };

  return (
    <section
      className="hero-4-bg position-relative bg-light"
      id="home"
      style={{ backgroundImage: `url(${Background})` }}
    >
      <Container>
        <Row className="align-items-center justify-content-center">
          <Col lg="6">
            <div>
              <p className="text-uppercase font-weight-bold f-14 mb-4">AudioSource</p>
              <h1 className="hero-4-title mb-4 line-height-1_4">Music Source Separation</h1>
              <p className="text-muted mb-4 pb-3">
                Nemo enim ipsam voluptatem quia voluptas sit aspernatur aut odit aut fugit, sed quia consequuntur magni
                dolores eos qui ratione sequinesciunt.
              </p>
            </div>
          </Col>
          <Col lg={4} className="offset-lg-2 col-md-8">
            <div className="hero-login-form mx-auto bg-white shadow p-4 rounded mt-5 mt-lg-0">
              <Form onSubmit={handleSubmit}>
                <FormGroup className="mb-3">
                  <div className="text-center mb-4 w-100">
                    <Label for="input-audio" className="h5">
                      Upload your Audio
                    </Label>
                  </div>
                  <Input
                    id="input-audio"
                    name="file"
                    type="file"
                    accept="audio/wav, audio/mp3"
                    onChange={(e) => setFile(e.target.files[0])}
                  />
                </FormGroup>
                <FormGroup className="mb-3">
                  <Label for="separationType" className="f-15">Separation Type</Label>
                  <Input 
                    type="select" 
                    className="form-control" 
                    id="separationType" 
                    name="separationType"
                    value={separationType} // Default to Stems Splitter
                    onChange={(e) => setSeparationType(e.target.value)}
                  >
                    <option disabled value="">Choose the type of Separation</option>
                    <option value="STEMS_SPLITTER">Stems Splitter</option>
                    <option value="VOCAL_REMOVER">Vocal Remover</option>
                  </Input>
                </FormGroup>
                <FormGroup className="mb-3">
                  <Label for="userEmail" className="f-15">Email to</Label>
                  <Input 
                    type="userEmail" 
                    className="form-control" 
                    id="userEmail" 
                    placeholder="Enter your E-mail" 
                    value={userEmail}
                    onChange={(e) => setUserEmail(e.target.value)}
                  />
                </FormGroup>
                <FormGroup className="mb-4">
                  <Label for="outputFormat" className="f-15">Output Format</Label>
                  <Input 
                    type="select" 
                    className="form-control" 
                    id="outputFormat" 
                    name="outputFormat"
                    value={outputFormat} // Default to WAV
                    onChange={(e) => setOutputFormat(e.target.value)}
                  >
                    <option value="WAV">WAV</option>
                    <option value="MP3">MP3</option>
                  </Input>
                </FormGroup>
                {uploadProgress > 0 && (
                  <Progress value={uploadProgress} className="mb-3">
                    {uploadProgress}%
                  </Progress>
                )}
                {uploadError && (
                  <Alert color="danger" className="mb-3">
                    {uploadError}
                  </Alert>
                )}
                {uploadSuccess && (
                  <Alert color="success" className="mb-3">
                    {uploadSuccess}
                  </Alert>
                )}
                <div className="d-grid gap-2 mx-auto">
                  <Button type="submit" color="primary" className="btn btn-primary btn-lg w-100 mt-2">
                    Submit
                    <i className="mdi mdi-telegram ml-2"></i>
                  </Button>
                </div>
              </Form>
            </div>
          </Col>
        </Row>
      </Container>
    </section>
  );
};

export default Section;