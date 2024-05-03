import React, { Component } from "react";
import Uppy from "@uppy/core";
import AwsS3 from '@uppy/aws-s3';
import { Container, Row, Col, Form, FormGroup, Label, Input , Button} from "reactstrap";

// Import Background Image
import Background from "../../assets/images/hero-4-bg-img.png";


class Section extends Component {

  constructor(props) {
    super(props);
    this.uppy = new Uppy()
      .use(AwsS3, {

        async getUploadParameters(file, options) {
          const response = await fetch("/upload/s3");
          return await response.json();
        },

        shouldUseMultipart(file) {
          return true;
        },

        async createMultipartUpload(file) {
          let data = { "type": file.type };
          const response = await fetch("/upload/s3-multipart", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(data)
          });
          return await response.json();
        },

        async listParts(file, { uploadId, key }) {
          let data = { "key": key };
          const response = await fetch(`/upload/s3-multipart/${uploadId}`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(data)
          });
          return await response.json();
        },

        async signPart(file, { key, uploadId, partNumber }) {
          let data = { "key": key };
          const response = await fetch(`/upload/s3-multipart/${uploadId}/${partNumber}`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(data)
          });
          return await response.json();
        },

        async abortMultipartUpload(file, { uploadId, key }) {
          let data = { "key": key };
          const response = await fetch(`/upload/s3-multipart/${uploadId}`, {
            method: "DELETE",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(data)
          });
          return await response.json();
        },

        completeMultipartUpload(file, { uploadId, key, parts }) {
          let data = { "key": key, "parts": parts };
          return fetch(`/upload/s3-multipart/${uploadId}/complete`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(data)
          }).then((response) => response.json());
        }
      });

    this.uppy.on("upload-success", (file, response) => {
      alert(response.uploadURL);
    });
  }

  render() {
    return (
      <React.Fragment>
        <section className="hero-4-bg position-relative bg-light" id="home" style={{ backgroundImage: `url(${Background})` }}>
        <Container>
          <Row className="align-items-center justify-content-center">
            <Col lg="6">
              <div className="">
                <p className="text-uppercase font-weight-bold f-14 mb-4">AudioSource</p>
                <h1 className="hero-4-title mb-4 line-height-1_4">Music Source Separation</h1>
                <p className="text-muted mb-4 pb-3">Nemo enim ipsam voluptatem quia voluptas sit aspernatur aut odit aut fugit, sed quia consequuntur magni dolores eos qui ratione sequinesciunt.</p>
              </div>
            </Col>
            <Col lg={4} className="offset-lg-2 col-md-8">
              <div className="hero-login-form mx-auto bg-white shadow p-4 rounded mt-5 mt-lg-0">
                <Form>
                  <FormGroup className="mb-3"> 
                    <Label for="input-audio" className="h5 mb-4">Upload your Audio</Label>
                    <Input id="input-audio" name="file" type="file" accept="audio/wav, audio/mp3" />
                  </FormGroup>
                  <FormGroup className="mb-3">
                    <Label for="sep-type" className="f-15">Separation Type</Label>
                    <Input type="select" className="form-control" id="sep-type" name="select">
                      <option disabled selected>Choose the type of Separation</option>                                             
                      <option>Vocal Remover</option>
                      <option>Stems Splitter</option>
                    </Input> 
                  </FormGroup>
                  <FormGroup className="mb-3">
                    <Label for="email" className="f-15">Email to</Label>
                    <Input type="email" className="form-control" id="email" placeholder="Enter your e-mail" />
                  </FormGroup>
                  <FormGroup className="mb-4">
                    <Label for="encoding" className="f-15">Output Encoding</Label> 
                    <Input type="select" className="form-control" id="encoding" name="select">
                      <option>MP3</option>
                      <option>WAV</option>
                    </Input>
                  </FormGroup>
                  <div class ="d-grid gap-2 mx-auto">
                    <Button type="submit" color="primary" className="btn btn-primary btn-lg w-100 mt-2">Separate<i className="mdi mdi-telegram ml-2"></i></Button>
                  </div>
                </Form>
              </div>
            </Col>
          </Row>
        </Container>
    </section>
      </React.Fragment>
    );
  }
}

export default Section;
