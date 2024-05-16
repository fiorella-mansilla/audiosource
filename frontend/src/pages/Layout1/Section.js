import React, { Component } from "react";
import { Container, Row, Col, Form, FormGroup, Label, Input , Button} from "reactstrap";
import { getSignedUrl, uploadFileToSignedUrl } from "../../api";
import Background from "../../assets/images/hero-4-bg-img.png";


class Section extends Component {
  handleSubmit = async (e) => {
    e.preventDefault();
    const file = e.target.file.files[0]; // Get the selected file
    const content_type = file.type;
    const key = file.name;

    try {
      // Get the signed URL for uploading the file
      const { signedUrl, fileLink } = await getSignedUrl({key, content_type});

      // Upload the file to the signed URL  
      await uploadFileToSignedUrl(signedUrl, file, content_type, null, () => {
        this.setState({ fileLink });
        // Handle successful upload (e.g., show the file link)
        console.log("File uploaded successfully. File link : ", fileLink);
      });
    } catch (error) {
      console.error("Error uploading file:", error);
    }
  };

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
                <Form onSubmit={this.handleSubmit}>
                  <FormGroup className="mb-3"> 
                    <Label for="input-audio" className="h5 mb-4">Upload your Audio</Label>
                    <Input id="input-audio" name="file" type="file" accept="audio/wav, audio/mp3, audio/mpeg"/>
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
